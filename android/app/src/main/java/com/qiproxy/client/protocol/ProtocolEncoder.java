package com.qiproxy.client.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * ProtocolEncoder - Encodes ProxyMessage to wire format
 * Format: [4-byte length][1-byte type][8-byte sn][1-byte uriLen][uri][data]
 * Mirrors Node.js encodeProxyMessage
 */
public class ProtocolEncoder {
    private static final int TYPE_SIZE = 1;
    private static final int SERIAL_NUMBER_SIZE = 8;
    private static final int URI_LENGTH_SIZE = 1;
    private static final int HEADER_SIZE = 4; // length field

    public static byte[] encode(ProxyMessage msg) {
        byte[] uriBytes = msg.getUri() != null ? msg.getUri().getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] dataBytes = msg.getData() != null ? msg.getData() : new byte[0];

        int bodyLength = TYPE_SIZE + SERIAL_NUMBER_SIZE + URI_LENGTH_SIZE + uriBytes.length + dataBytes.length;
        int totalLength = HEADER_SIZE + bodyLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // Write body length
        buffer.putInt(bodyLength);
        // Write type
        buffer.put((byte) msg.getType());
        // Write serial number
        buffer.putLong(msg.getSerialNumber());
        // Write URI length + URI
        buffer.put((byte) uriBytes.length);
        if (uriBytes.length > 0) {
            buffer.put(uriBytes);
        }
        // Write data
        if (dataBytes.length > 0) {
            buffer.put(dataBytes);
        }

        return buffer.array();
    }
}
