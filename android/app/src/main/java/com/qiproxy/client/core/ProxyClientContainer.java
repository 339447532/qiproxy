package com.qiproxy.client.core;

import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.config.ConfigManager;
import com.qiproxy.client.network.BackpressureManager;
import com.qiproxy.client.util.ProxyLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ProxyClientContainer - Main orchestrator for the proxy client.
 * Mirrors Node.js ProxyClientContainer.
 */
public class ProxyClientContainer {

    private final ClientConfig config;
    private final DataChannelPool dataChannelPool;
    private final BackpressureManager backpressureManager;
    private final ReconnectionPolicy reconnectionPolicy;
    private CommandChannel commandChannel;
    private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
    private State currentState = State.IDLE;

    public interface StateListener {
        void onStateChanged(State state, String message);
    }

    public enum State {
        IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }

    public ProxyClientContainer(ClientConfig config) {
        this.config = config;
        this.dataChannelPool = new DataChannelPool(config);
        this.backpressureManager = new BackpressureManager();
        this.reconnectionPolicy = new ReconnectionPolicy();
        ProxyLogger.setLevel(config.getLogLevel());
    }

    public void addStateListener(StateListener listener) {
        if (listener != null && !stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }

    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public void start() {
        if (commandChannel != null && commandChannel.isConnected()) {
            ProxyLogger.w("客户端已在运行");
            return;
        }

        ProxyLogger.i("正在启动内网穿透客户端...");
        notifyState(State.CONNECTING, "正在连接到 " + config.getServerHost() + ":" + config.getServerPort());

        commandChannel = new CommandChannel(
                config,
                dataChannelPool,
                backpressureManager,
                reconnectionPolicy,
                new CommandChannel.CommandChannelListener() {
                    @Override
                    public void onConnected() {
                        notifyState(State.CONNECTED, "已连接到代理服务器");
                    }

                    @Override
                    public void onDisconnected() {
                        notifyState(State.DISCONNECTED, "与代理服务器断开连接");
                    }

                    @Override
                    public void onError(String message) {
                        notifyState(State.ERROR, message);
                    }
                }
        );
        commandChannel.start();
    }

    public void stop() {
        ProxyLogger.i("正在停止内网穿透客户端...");
        if (commandChannel != null) {
            commandChannel.stop();
            commandChannel = null;
        }
        dataChannelPool.clear();
        backpressureManager.clear();
        notifyState(State.IDLE, "客户端已停止");
    }

    public boolean isRunning() {
        return commandChannel != null;
    }

    public State getState() {
        return currentState;
    }

    public int getPoolSize() {
        return dataChannelPool.getPoolSize();
    }

    public int getActiveCount() {
        return dataChannelPool.getActiveCount();
    }

    private void notifyState(State state, String message) {
        this.currentState = state;
        for (StateListener listener : stateListeners) {
            try {
                listener.onStateChanged(state, message);
            } catch (Exception e) {
                ProxyLogger.e("StateListener error: " + e.getMessage());
            }
        }
    }
}
