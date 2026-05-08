package com.qiproxy.client.core;

import com.qiproxy.client.util.ProxyLogger;

import java.io.IOException;
import java.net.Socket;

/**
 * RealServerConnector - Connects to real backend servers
 * Mirrors Node.js net.Socket.connect in handleConnectMessage
 */
public class RealServerConnector {

    /**
     * Connects to the real server at the given ip:port.
     */
    public static Socket connect(String ip, int port) throws IOException {
        ProxyLogger.i("Connecting to real server: " + ip + ":" + port);
        Socket socket = new Socket(ip, port);
        ProxyLogger.i("Connected to real server: " + ip + ":" + port);
        return socket;
    }
}
