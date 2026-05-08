package com.qiproxy.client.protocol;

/**
 * Message type constants
 * Mirrors Node.js protocol.js constants
 */
public class MessageType {
    public static final int C_TYPE_AUTH = 0x01;
    public static final int TYPE_CONNECT = 0x03;
    public static final int TYPE_DISCONNECT = 0x04;
    public static final int P_TYPE_TRANSFER = 0x05;
    public static final int C_TYPE_WRITE_CONTROL = 0x06;
    public static final int TYPE_HEARTBEAT = 0x07;

    public static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1 MB

    private MessageType() {}

    public static String typeToString(int type) {
        switch (type) {
            case C_TYPE_AUTH: return "AUTH";
            case TYPE_CONNECT: return "CONNECT";
            case TYPE_DISCONNECT: return "DISCONNECT";
            case P_TYPE_TRANSFER: return "TRANSFER";
            case C_TYPE_WRITE_CONTROL: return "WRITE_CONTROL";
            case TYPE_HEARTBEAT: return "HEARTBEAT";
            default: return "UNKNOWN(" + type + ")";
        }
    }
}
