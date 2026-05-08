package com.qiproxy.client.core;

import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.network.BackpressureManager;
import com.qiproxy.client.network.SocketFactory;
import com.qiproxy.client.protocol.FrameDecoder;
import com.qiproxy.client.protocol.MessageType;
import com.qiproxy.client.protocol.ProtocolDecoder;
import com.qiproxy.client.protocol.ProtocolEncoder;
import com.qiproxy.client.protocol.ProxyMessage;
import com.qiproxy.client.util.ProxyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CommandChannel - Manages the single command channel to the proxy server.
 * Handles authentication, heartbeat, incoming CONNECT/DISCONNECT/TRANSFER messages,
 * and delegates data channel management.
 * Mirrors Node.js index.js connectProxyServer + handleSocketData logic.
 */
public class CommandChannel implements Runnable {

    private static final long READ_IDLE_TIME_MS = 60 * 1000;
    private static final long WRITE_IDLE_TIME_MS = 40 * 1000;

    private final ClientConfig config;
    private final DataChannelPool dataChannelPool;
    private final BackpressureManager backpressureManager;
    private final ReconnectionPolicy reconnectionPolicy;
    private final CommandChannelListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<Socket> socketRef = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, Socket> realServerSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Socket, String> realServerUserIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Socket> dataChannels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Socket, ChannelBinding> bindings = new ConcurrentHashMap<>();

    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> idleCheckTask;
    private volatile long lastReadTime;
    private Thread thread;

    public interface CommandChannelListener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    public CommandChannel(ClientConfig config, DataChannelPool dataChannelPool,
                          BackpressureManager backpressureManager,
                          ReconnectionPolicy reconnectionPolicy,
                          CommandChannelListener listener) {
        this.config = config;
        this.dataChannelPool = dataChannelPool;
        this.backpressureManager = backpressureManager;
        this.reconnectionPolicy = reconnectionPolicy;
        this.listener = listener;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, "CommandChannel");
            thread.start();
        }
    }

    public void stop() {
        running.set(false);
        closeSocket();
        clearAllChannels();
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (idleCheckTask != null) idleCheckTask.cancel(false);
        scheduler.shutdownNow();
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                connectAndRun();
                reconnectionPolicy.reset();
            } catch (Exception e) {
                ProxyLogger.e("Command channel error: " + e.getMessage(), e);
                if (listener != null) listener.onError(e.getMessage());
            }

            connected.set(false);
            if (listener != null) listener.onDisconnected();

            if (!running.get()) break;

            // Clear all real server channels on disconnect
            clearAllChannels();

            // Wait before reconnect
            reconnectionPolicy.sleep();
        }
    }

    private void connectAndRun() throws IOException {
        SocketFactory socketFactory = new SocketFactory(config);
        Socket socket = socketFactory.createSocket();
        socketRef.set(socket);
        lastReadTime = System.currentTimeMillis();

        ProxyLogger.i("Command channel connected to " + config.getServerHost() + ":" + config.getServerPort());

        // Send AUTH immediately
        sendAuth(socket);

        connected.set(true);
        if (listener != null) listener.onConnected();

        // Start heartbeat and idle check
        startHeartbeat(socket);
        startIdleCheck();

        // Read loop
        InputStream in = socket.getInputStream();
        FrameDecoder frameDecoder = new FrameDecoder();
        byte[] buffer = new byte[8192];

        while (running.get() && !socket.isClosed()) {
            int read;
            try {
                read = in.read(buffer);
            } catch (IOException e) {
                if (running.get()) {
                    ProxyLogger.w("Command channel read error: " + e.getMessage());
                }
                break;
            }

            if (read < 0) {
                ProxyLogger.w("Command channel EOF");
                break;
            }

            if (read > 0) {
                lastReadTime = System.currentTimeMillis();
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);

                List<byte[]> frames = frameDecoder.decode(chunk);
                for (byte[] frame : frames) {
                    ProxyMessage msg = ProtocolDecoder.decode(frame);
                    if (msg != null) {
                        handleMessage(socket, msg);
                    }
                }
            }
        }
    }

    private void sendAuth(Socket socket) throws IOException {
        ProxyMessage authMsg = new ProxyMessage();
        authMsg.setType(MessageType.C_TYPE_AUTH);
        authMsg.setUri(config.getClientKey());
        OutputStream out = socket.getOutputStream();
        out.write(ProtocolEncoder.encode(authMsg));
        out.flush();
        ProxyLogger.i("Sent AUTH message");
    }

    private void handleMessage(Socket cmdSocket, ProxyMessage msg) {
        ProxyLogger.d("Received message type=" + MessageType.typeToString(msg.getType()));

        switch (msg.getType()) {
            case MessageType.TYPE_CONNECT:
                handleConnect(cmdSocket, msg);
                break;
            case MessageType.TYPE_DISCONNECT:
                handleDisconnect(cmdSocket, msg);
                break;
            case MessageType.P_TYPE_TRANSFER:
                handleTransfer(cmdSocket, msg);
                break;
            default:
                break;
        }
    }

    private void handleConnect(Socket proxyChannel, ProxyMessage msg) {
        String userId = msg.getUri();
        String serverInfo = new String(msg.getData(), StandardCharsets.UTF_8);
        String[] parts = serverInfo.split(":");
        if (parts.length != 2) {
            ProxyLogger.e("Invalid server info: " + serverInfo);
            return;
        }
        String ip = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            ProxyLogger.e("Invalid port in server info: " + serverInfo);
            return;
        }

        ProxyLogger.i("TYPE_CONNECT: Real server " + ip + ":" + port + " for user " + userId);

        // Run in background thread to avoid blocking command channel read loop
        new Thread(() -> {
            try {
                Socket realServerSocket = RealServerConnector.connect(ip, port);
                Socket dataChannel = dataChannelPool.borrowChannel();

                // Send CONNECT on data channel
                ProxyMessage connectMsg = new ProxyMessage();
                connectMsg.setType(MessageType.TYPE_CONNECT);
                connectMsg.setUri(userId + "@" + config.getClientKey());
                dataChannel.getOutputStream().write(ProtocolEncoder.encode(connectMsg));
                dataChannel.getOutputStream().flush();

                // Track mappings
                realServerSockets.put(userId, realServerSocket);
                realServerUserIds.put(realServerSocket, userId);
                dataChannels.put(userId, dataChannel);

                // Start bidirectional forwarding
                ChannelBinding rsToProxy = new ChannelBinding(realServerSocket, dataChannel, userId, true);
                ChannelBinding proxyToRs = new ChannelBinding(dataChannel, realServerSocket, userId, false);
                bindings.put(realServerSocket, rsToProxy);
                bindings.put(dataChannel, proxyToRs);

                rsToProxy.start();
                proxyToRs.start();

                ProxyLogger.i("Data tunnel established for user " + userId);
            } catch (Exception e) {
                ProxyLogger.e("Failed to establish data tunnel for user " + userId + ": " + e.getMessage(), e);
                // Send DISCONNECT back
                try {
                    ProxyMessage disconnectMsg = new ProxyMessage();
                    disconnectMsg.setType(MessageType.TYPE_DISCONNECT);
                    disconnectMsg.setUri(userId);
                    proxyChannel.getOutputStream().write(ProtocolEncoder.encode(disconnectMsg));
                    proxyChannel.getOutputStream().flush();
                } catch (IOException ignored) {
                }
            }
        }, "HandleConnect-" + userId).start();
    }

    private void handleDisconnect(Socket proxyChannel, ProxyMessage msg) {
        String userId = msg.getUri();
        ProxyLogger.i("TYPE_DISCONNECT for user " + userId);

        Socket realServerSocket = realServerSockets.remove(userId);
        Socket dataChannel = dataChannels.remove(userId);

        if (realServerSocket != null) {
            realServerUserIds.remove(realServerSocket);
            ChannelBinding rsBinding = bindings.remove(realServerSocket);
            if (rsBinding != null) rsBinding.close();
        }

        if (dataChannel != null) {
            ChannelBinding proxyBinding = bindings.remove(dataChannel);
            if (proxyBinding != null) proxyBinding.close();
            dataChannelPool.removeChannel(dataChannel);
        }

        if (realServerSocket != null) {
            try {
                realServerSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleTransfer(Socket proxyChannel, ProxyMessage msg) {
        // TRANSFER on command channel is unexpected in this architecture;
        // data channel handles transfer. Log for debugging.
        ProxyLogger.d("Unexpected TRANSFER on command channel");
    }

    private void startHeartbeat(Socket socket) {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (!socket.isClosed() && socket.isConnected()) {
                try {
                    ProxyMessage heartbeat = new ProxyMessage();
                    heartbeat.setType(MessageType.TYPE_HEARTBEAT);
                    socket.getOutputStream().write(ProtocolEncoder.encode(heartbeat));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    ProxyLogger.d("Heartbeat failed: " + e.getMessage());
                }
            }
        }, WRITE_IDLE_TIME_MS, WRITE_IDLE_TIME_MS, TimeUnit.MILLISECONDS);
    }

    private void startIdleCheck() {
        if (idleCheckTask != null) idleCheckTask.cancel(false);
        idleCheckTask = scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - lastReadTime > READ_IDLE_TIME_MS) {
                ProxyLogger.w("Command channel idle timeout, closing socket");
                closeSocket();
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    private void closeSocket() {
        Socket socket = socketRef.getAndSet(null);
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void clearAllChannels() {
        ProxyLogger.w("Clearing all real server channels and bindings");
        for (ChannelBinding binding : bindings.values()) {
            binding.close();
        }
        bindings.clear();
        for (Socket socket : realServerSockets.values()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        for (Socket socket : dataChannels.values()) {
            dataChannelPool.removeChannel(socket);
        }
        realServerSockets.clear();
        realServerUserIds.clear();
        dataChannels.clear();
        backpressureManager.clear();
    }
}
