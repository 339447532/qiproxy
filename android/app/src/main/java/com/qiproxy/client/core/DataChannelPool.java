package com.qiproxy.client.core;

import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.network.SocketFactory;
import com.qiproxy.client.util.ProxyLogger;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataChannelPool - Manages a pool of proxy data channels
 * Mirrors Node.js channelManager borrowProxyChannel / returnProxyChannel / removeProxyChannel
 */
public class DataChannelPool {

    public static final int MAX_POOL_SIZE = 100;

    private final LinkedBlockingQueue<Socket> pool = new LinkedBlockingQueue<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final SocketFactory socketFactory;

    public DataChannelPool(ClientConfig config) {
        this.socketFactory = new SocketFactory(config);
    }

    /**
     * Borrows a proxy channel from the pool or creates a new one.
     * This is blocking until a channel is available or created.
     */
    public Socket borrowChannel() throws IOException {
        // Try to get from pool first
        while (true) {
            Socket channel = pool.poll();
            if (channel == null) {
                break;
            }
            if (isAlive(channel)) {
                ProxyLogger.d("Borrowed proxy channel from pool, pool size=" + pool.size());
                return channel;
            }
            // Dead channel, discard and try next
            closeQuietly(channel);
        }

        // Create new connection
        ProxyLogger.d("Creating new proxy data channel");
        Socket socket = socketFactory.createSocket();
        activeCount.incrementAndGet();
        return socket;
    }

    /**
     * Returns a proxy channel to the pool for reuse.
     */
    public void returnChannel(Socket channel) {
        if (channel == null || !isAlive(channel)) {
            closeQuietly(channel);
            activeCount.decrementAndGet();
            return;
        }

        if (!pool.offer(channel)) {
            // Pool is full
            ProxyLogger.d("Pool full, destroying proxy channel");
            closeQuietly(channel);
            activeCount.decrementAndGet();
        } else {
            ProxyLogger.d("Returned proxy channel to pool, pool size=" + pool.size());
        }
    }

    /**
     * Removes a channel from the pool.
     */
    public void removeChannel(Socket channel) {
        if (channel != null) {
            pool.remove(channel);
            closeQuietly(channel);
            activeCount.decrementAndGet();
        }
    }

    public void clear() {
        Socket ch;
        while ((ch = pool.poll()) != null) {
            closeQuietly(ch);
        }
        activeCount.set(0);
    }

    public int getPoolSize() {
        return pool.size();
    }

    public int getActiveCount() {
        return activeCount.get();
    }

    private boolean isAlive(Socket socket) {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    private void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
