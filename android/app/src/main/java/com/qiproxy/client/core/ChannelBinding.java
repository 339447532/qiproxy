package com.qiproxy.client.core;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ChannelBinding - Bidirectional data forwarding between two sockets
 * Mirrors Node.js index.js setupRealServerHandler + channelManager handleProxySocketData
 */
public class ChannelBinding {

    private static final int BUFFER_SIZE = 8192;

    private final Socket source;
    private final Socket destination;
    private final String userId;
    private final boolean isRealServerToProxy;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread thread;

    /**
     * @param source           the socket to read from
     * @param destination      the socket to write to
     * @param userId           user ID for transfer messages
     * @param isRealServerToProxy true if forwarding real server data to proxy (needs TRANSFER framing)
     */
    private final HttpSniffer sniffer;

    public ChannelBinding(Socket source, Socket destination, String userId, boolean isRealServerToProxy) {
        this.source = source;
        this.destination = destination;
        this.userId = userId;
        this.isRealServerToProxy = isRealServerToProxy;
        this.sniffer = new HttpSniffer(userId, !isRealServerToProxy);
    }

    public void start() {
        thread = new Thread(this::runForward, "ChannelBinding-" + (isRealServerToProxy ? "RS-Proxy" : "Proxy-RS"));
        thread.start();
    }

    private void runForward() {
        byte[] buffer = new byte[BUFFER_SIZE];
        FrameDecoder frameDecoder = isRealServerToProxy ? null : new FrameDecoder();
        try (InputStream in = source.getInputStream();
             OutputStream out = destination.getOutputStream()) {

            while (running.get() && !source.isClosed() && !destination.isClosed()) {
                int read = in.read(buffer);
                if (read < 0) {
                    break; // EOF
                }
                if (read > 0) {
                    if (isRealServerToProxy) {
                        // Wrap in TRANSFER message
                        sniffer.feed(buffer, 0, read);
                        ProxyMessage msg = new ProxyMessage();
                        msg.setType(MessageType.P_TYPE_TRANSFER);
                        msg.setUri(userId);
                        byte[] data = new byte[read];
                        System.arraycopy(buffer, 0, data, 0, read);
                        msg.setData(data);
                        out.write(ProtocolEncoder.encode(msg));
                    } else {
                        // Decode protocol frames and extract payload
                        byte[] chunk = new byte[read];
                        System.arraycopy(buffer, 0, chunk, 0, read);
                        List<byte[]> frames = frameDecoder.decode(chunk);
                        for (byte[] frame : frames) {
                            ProxyMessage msg = ProtocolDecoder.decode(frame);
                            if (msg != null) {
                                if (msg.getType() == MessageType.P_TYPE_TRANSFER && msg.getData() != null) {
                                    sniffer.feed(msg.getData(), 0, msg.getData().length);
                                    out.write(msg.getData());
                                } else if (msg.getType() == MessageType.TYPE_DISCONNECT) {
                                    ProxyLogger.d("Received DISCONNECT on data channel for user " + userId);
                                    running.set(false);
                                    break;
                                }
                            }
                        }
                    }
                    out.flush();
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                ProxyLogger.d("ChannelBinding closed: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    public void close() {
        if (running.compareAndSet(true, false)) {
            ProxyLogger.d("ChannelBinding closing for userId=" + userId);
            try {
                source.close();
            } catch (IOException ignored) {
            }
            try {
                destination.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public void join() {
        try {
            if (thread != null) {
                thread.join(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
