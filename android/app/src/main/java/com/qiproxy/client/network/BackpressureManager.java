package com.qiproxy.client.network;

import java.util.concurrent.ConcurrentHashMap;

/**
 * BackpressureManager - Manages writable state for flow control
 * Mirrors Node.js channelManager isRealServerReadable logic
 */
public class BackpressureManager {

    // Channel-level writable flags
    private final ConcurrentHashMap<Object, Boolean> userChannelWritable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Object, Boolean> clientChannelWritable = new ConcurrentHashMap<>();

    public void setUserChannelWritable(Object channel, boolean writable) {
        userChannelWritable.put(channel, writable);
    }

    public void setClientChannelWritable(Object channel, boolean writable) {
        clientChannelWritable.put(channel, writable);
    }

    public boolean isReadable(Object channel) {
        Boolean userWritable = userChannelWritable.get(channel);
        Boolean clientWritable = clientChannelWritable.get(channel);
        return (userWritable == null || userWritable) && (clientWritable == null || clientWritable);
    }

    public void removeChannel(Object channel) {
        userChannelWritable.remove(channel);
        clientChannelWritable.remove(channel);
    }

    public void clear() {
        userChannelWritable.clear();
        clientChannelWritable.clear();
    }
}
