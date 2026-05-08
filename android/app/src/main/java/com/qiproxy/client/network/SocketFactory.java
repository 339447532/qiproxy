package com.qiproxy.client.network;

import com.qiproxy.client.config.ClientConfig;
import com.qiproxy.client.util.ProxyLogger;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SocketFactory - Creates plain Socket or SSLSocket based on config
 * Mirrors Node.js createProxyConnection
 */
public class SocketFactory {

    private final ClientConfig config;
    private javax.net.ssl.SSLContext sslContext;

    public SocketFactory(ClientConfig config) {
        this.config = config;
    }

    public Socket createSocket() throws IOException {
        String host = config.getServerHost();
        int port = config.getServerPort();

        if (host == null || host.isEmpty()) {
            throw new IOException("Server host not configured");
        }

        if (config.isSslEnabled()) {
            return createSSLSocket(host, port);
        } else {
            ProxyLogger.i("Connecting to proxy server (plain TCP): " + host + ":" + port);
            return new Socket(host, port);
        }
    }

    private Socket createSSLSocket(String host, int port) throws IOException {
        try {
            if (sslContext == null) {
                sslContext = SSLContextFactory.createSSLContext(config);
            }
            SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            socket.startHandshake();
            ProxyLogger.i("Connected to proxy server (SSL): " + host + ":" + port);
            return socket;
        } catch (Exception e) {
            throw new IOException("Failed to create SSL socket", e);
        }
    }

    public void clearSSLContext() {
        sslContext = null;
    }
}
