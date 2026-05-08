package com.qiproxy.client.config;

/**
 * 默认配置值（预设配置）
 */
public class ConfigDefaults {
    public static final String DEFAULT_CLIENT_KEY = "a513deab23ee47698934f7f8507b1ca5";
    public static final boolean DEFAULT_SSL_ENABLED = true;
    public static final String DEFAULT_SSL_CERT_PATH = "client-cert.pem";
    public static final String DEFAULT_SSL_KEY_PATH = "client-key.pem";
    public static final String DEFAULT_SSL_KEY_PASSWORD = "changeit";
    public static final String DEFAULT_SERVER_HOST = "39.108.124.205";
    public static final int DEFAULT_SERVER_PORT = 4993;
    public static final String DEFAULT_LOG_LEVEL = "INFO";

    private ConfigDefaults() {}
}
