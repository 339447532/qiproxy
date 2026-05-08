package com.qiproxy.client.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.qiproxy.client.config.ConfigManager;
import com.qiproxy.client.util.ProxyLogger;

/**
 * BootReceiver - Auto-starts the proxy foreground service after device boot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ConfigManager.getInstance().init(context);
            if (ConfigManager.getInstance().isConfigured()) {
                ProxyLogger.i("Boot completed, starting proxy service");
                Intent serviceIntent = new Intent(context, ProxyForegroundService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
