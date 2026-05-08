package com.qiproxy.client.core;

import com.qiproxy.client.util.ProxyLogger;

/**
 * ReconnectionPolicy - Exponential backoff reconnection strategy
 * Mirrors Node.js reconnectWithBackoff logic
 */
public class ReconnectionPolicy {
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 60000;

    private long currentDelayMs = INITIAL_DELAY_MS;

    public long getNextDelay() {
        long delay = currentDelayMs;
        currentDelayMs = Math.min(currentDelayMs * 2, MAX_DELAY_MS);
        ProxyLogger.i("Reconnect delay: " + delay + "ms");
        return delay;
    }

    public void reset() {
        currentDelayMs = INITIAL_DELAY_MS;
    }

    public void sleep() {
        try {
            Thread.sleep(getNextDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
