package com.qiproxy.client.config;

/**
 * ClientConfig - Configuration data class
 * Mirrors Node.js config properties
 */
public class ClientConfig {
    private String clientKey;
    private boolean sslEnabled;
    private String sslCertPath;
    private String sslKeyPath;
    private byte[] sslCertBytes;
    private byte[] sslKeyBytes;
    private String sslKeyPassword;
    private String serverHost;
    private int serverPort;
    private String logLevel;

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getSslCertPath() {
        return sslCertPath;
    }

    public void setSslCertPath(String sslCertPath) {
        this.sslCertPath = sslCertPath;
    }

    public String getSslKeyPath() {
        return sslKeyPath;
    }

    public void setSslKeyPath(String sslKeyPath) {
        this.sslKeyPath = sslKeyPath;
    }

    public byte[] getSslCertBytes() {
        return sslCertBytes;
    }

    public void setSslCertBytes(byte[] sslCertBytes) {
        this.sslCertBytes = sslCertBytes;
    }

    public byte[] getSslKeyBytes() {
        return sslKeyBytes;
    }

    public void setSslKeyBytes(byte[] sslKeyBytes) {
        this.sslKeyBytes = sslKeyBytes;
    }

    public String getSslKeyPassword() {
        return sslKeyPassword;
    }

    public void setSslKeyPassword(String sslKeyPassword) {
        this.sslKeyPassword = sslKeyPassword;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
