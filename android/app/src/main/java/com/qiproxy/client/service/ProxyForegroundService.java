package com.qiproxy.client.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.qiproxy.client.R;
import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.config.ConfigManager;
import com.qiproxy.client.core.ProxyClientContainer;
import com.qiproxy.client.ui.MainActivity;
import com.qiproxy.client.util.ProxyLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * ProxyForegroundService - Foreground service that keeps the proxy client alive.
 * Mirrors the persistent background requirement of the Node.js client.
 */
public class ProxyForegroundService extends Service {

    private static final String CHANNEL_ID = "qiproxy_channel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private ProxyClientContainer clientContainer;
    private final List<ProxyClientContainer.StateListener> pendingListeners = new ArrayList<>();

    public class LocalBinder extends Binder {
        public ProxyForegroundService getService() {
            return ProxyForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ConfigManager.getInstance().init(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."));

        if (clientContainer == null || !clientContainer.isRunning()) {
            ClientConfig config = ConfigManager.getInstance().loadConfig();
            if (config.getServerHost() == null || config.getServerHost().isEmpty()
                    || config.getClientKey() == null || config.getClientKey().isEmpty()) {
                updateNotification("未配置 - 请打开应用进行设置");
                ProxyLogger.w("Service started but not configured");
            } else {
                startClient(config);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (clientContainer != null) {
            clientContainer.stop();
            clientContainer = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void startClient(ClientConfig config) {
        ProxyLogger.setLevel(config.getLogLevel());

        if (clientContainer != null && clientContainer.isRunning()) {
            ProxyLogger.w("Client already running, skipping start");
            return;
        }

        if (clientContainer != null) {
            clientContainer.stop();
        }

        clientContainer = new ProxyClientContainer(config);
        clientContainer.addStateListener((state, message) -> {
            String status = state.name() + ": " + message;
            updateNotification(status);
        });
        // Attach any listeners registered before container was created
        for (ProxyClientContainer.StateListener listener : pendingListeners) {
            clientContainer.addStateListener(listener);
        }
        clientContainer.start();
    }

    public void stopClient() {
        if (clientContainer != null) {
            clientContainer.stop();
            clientContainer = null;
        }
        updateNotification("已停止");
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    public ProxyClientContainer getClientContainer() {
        return clientContainer;
    }

    public void addContainerStateListener(ProxyClientContainer.StateListener listener) {
        if (listener == null) return;
        pendingListeners.remove(listener);
        pendingListeners.add(listener);
        if (clientContainer != null) {
            clientContainer.addStateListener(listener);
        }
    }

    public void removeContainerStateListener(ProxyClientContainer.StateListener listener) {
        if (listener == null) return;
        pendingListeners.remove(listener);
        if (clientContainer != null) {
            clientContainer.removeStateListener(listener);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "内网穿透服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持代理客户端后台连接");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("内网穿透客户端")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(content));
        }
    }
}
