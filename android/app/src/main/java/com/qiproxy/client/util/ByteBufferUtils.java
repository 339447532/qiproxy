package com.qiproxy.client.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods for ByteBuffer operations
 */
public class ByteBufferUtils {

    private ByteBufferUtils() {}

    /**
     * Creates a BIG_ENDIAN ByteBuffer from byte array
     */
    public static ByteBuffer wrap(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb;
    }

    /**
     * Reads a 4-byte big-endian int from byte array at offset
     */
    public static int readIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
