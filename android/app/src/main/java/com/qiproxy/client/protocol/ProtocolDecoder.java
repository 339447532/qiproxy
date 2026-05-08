package com.qiproxy.client.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * ProtocolDecoder - Decodes wire format to ProxyMessage
 * Mirrors Node.js decodeProxyMessage
 */
public class ProtocolDecoder {
    private static final int TYPE_SIZE = 1;
    private static final int SERIAL_NUMBER_SIZE = 8;
    private static final int URI_LENGTH_SIZE = 1;
    private static final int HEADER_SIZE = 4;

    /**
     * Decodes a complete frame (including 4-byte length header) into ProxyMessage.
     * Returns null if buffer is too short.
     */
    public static ProxyMessage decode(byte[] buffer) {
        if (buffer == null || buffer.length < HEADER_SIZE) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.order(ByteOrder.BIG_ENDIAN);

        int bodyLength = bb.getInt();
        if (buffer.length < HEADER_SIZE + bodyLength) {
            return null;
        }

        ProxyMessage msg = new ProxyMessage();
        msg.setType(bb.get() & 0xFF);
        msg.setSerialNumber(bb.getLong());

        int uriLength = bb.get() & 0xFF;
        if (uriLength > 0) {
            byte[] uriBytes = new byte[uriLength];
            bb.get(uriBytes);
            msg.setUri(new String(uriBytes, StandardCharsets.UTF_8));
        } else {
            msg.setUri("");
        }

        int dataLength = bodyLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength;
        if (dataLength > 0) {
            byte[] data = new byte[dataLength];
            bb.get(data);
            msg.setData(data);
        } else {
            msg.setData(null);
        }

        return msg;
    }
}
