package com.qiproxy.client.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.qiproxy.client.util.ProxyLogger;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * ConfigManager - Manages client configuration using EncryptedSharedPreferences
 * Mirrors Node.js config.js
 */
public class ConfigManager {
    private static final String PREFS_FILE = "qiproxy_config";
    private static final String KEY_CLIENT_KEY = "client.key";
    private static final String KEY_SSL_ENABLE = "ssl.enable";
    private static final String KEY_SSL_CERT_PATH = "ssl.certPath";
    private static final String KEY_SSL_KEY_PATH = "ssl.keyPath";
    private static final String KEY_SSL_KEY_PASSWORD = "ssl.keyPassword";
    private static final String KEY_SERVER_HOST = "server.host";
    private static final String KEY_SERVER_PORT = "server.port";
    private static final String KEY_LOG_LEVEL = "log.level";

    private static ConfigManager instance;
    private Context appContext;
    private EncryptedSharedPreferences encryptedPrefs;
    private SharedPreferences fallbackPrefs;

    private ConfigManager() {}

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            ProxyLogger.e("Failed to create EncryptedSharedPreferences, falling back to plain", e);
            fallbackPrefs = context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    private SharedPreferences getPrefs() {
        return encryptedPrefs != null ? encryptedPrefs : fallbackPrefs;
    }

    public void saveConfig(ClientConfig config) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(KEY_CLIENT_KEY, config.getClientKey());
        editor.putBoolean(KEY_SSL_ENABLE, config.isSslEnabled());
        editor.putString(KEY_SSL_CERT_PATH, config.getSslCertPath());
        editor.putString(KEY_SSL_KEY_PATH, config.getSslKeyPath());
        editor.putString(KEY_SSL_KEY_PASSWORD, config.getSslKeyPassword());
        editor.putString(KEY_SERVER_HOST, config.getServerHost());
        editor.putInt(KEY_SERVER_PORT, config.getServerPort());
        editor.putString(KEY_LOG_LEVEL, config.getLogLevel());
        editor.apply();
    }

    public ClientConfig loadConfig() {
        ClientConfig config = new ClientConfig();
        SharedPreferences prefs = getPrefs();
        config.setClientKey(prefs.getString(KEY_CLIENT_KEY, ConfigDefaults.DEFAULT_CLIENT_KEY));
        config.setSslEnabled(prefs.getBoolean(KEY_SSL_ENABLE, ConfigDefaults.DEFAULT_SSL_ENABLED));
        config.setSslCertPath(prefs.getString(KEY_SSL_CERT_PATH, ConfigDefaults.DEFAULT_SSL_CERT_PATH));
        config.setSslKeyPath(prefs.getString(KEY_SSL_KEY_PATH, ConfigDefaults.DEFAULT_SSL_KEY_PATH));
        config.setSslKeyPassword(prefs.getString(KEY_SSL_KEY_PASSWORD, ConfigDefaults.DEFAULT_SSL_KEY_PASSWORD));
        config.setServerHost(prefs.getString(KEY_SERVER_HOST, ConfigDefaults.DEFAULT_SERVER_HOST));
        config.setServerPort(prefs.getInt(KEY_SERVER_PORT, ConfigDefaults.DEFAULT_SERVER_PORT));
        config.setLogLevel(prefs.getString(KEY_LOG_LEVEL, ConfigDefaults.DEFAULT_LOG_LEVEL));
        loadEmbeddedCerts(config);
        return config;
    }

    private void loadEmbeddedCerts(ClientConfig config) {
        if (appContext == null) return;
        try {
            if (config.getSslCertBytes() == null) {
                try (java.io.InputStream is = appContext.getResources().openRawResource(com.qiproxy.client.R.raw.client_cert)) {
                    config.setSslCertBytes(readAllBytes(is));
                }
            }
            if (config.getSslKeyBytes() == null) {
                try (java.io.InputStream is = appContext.getResources().openRawResource(com.qiproxy.client.R.raw.client_key)) {
                    config.setSslKeyBytes(readAllBytes(is));
                }
            }
        } catch (Exception e) {
            ProxyLogger.w("Failed to load embedded certificates", e);
        }
    }

    private static byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    public boolean isConfigured() {
        SharedPreferences prefs = getPrefs();
        String host = prefs.getString(KEY_SERVER_HOST, ConfigDefaults.DEFAULT_SERVER_HOST);
        String key = prefs.getString(KEY_CLIENT_KEY, ConfigDefaults.DEFAULT_CLIENT_KEY);
        return host != null && !host.isEmpty() && key != null && !key.isEmpty();
    }

    public String getStringValue(String key, String defaultValue) {
        return getPrefs().getString(key, defaultValue);
    }

    public int getIntValue(String key, int defaultValue) {
        return getPrefs().getInt(key, defaultValue);
    }

    public boolean getBooleanValue(String key, boolean defaultValue) {
        return getPrefs().getBoolean(key, defaultValue);
    }
}
