package com.qiproxy.client.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * FrameDecoder - Length-field-based frame decoder
 * Mirrors Node.js frameDecoder
 */
public class FrameDecoder {
    private ByteBuffer accumulator;

    public FrameDecoder() {
        // Initial capacity 64KB, will grow if needed
        this.accumulator = ByteBuffer.allocate(65536);
        this.accumulator.order(ByteOrder.BIG_ENDIAN);
        this.accumulator.flip();
    }

    /**
     * Feeds incoming bytes and returns list of complete frames.
     * Incomplete bytes are kept for next call.
     */
    public List<byte[]> decode(byte[] incoming) {
        List<byte[]> frames = new ArrayList<>();

        // Ensure capacity
        int required = accumulator.remaining() + incoming.length;
        if (required > accumulator.capacity()) {
            ByteBuffer newBuf = ByteBuffer.allocate(Math.max(accumulator.capacity() * 2, required));
            newBuf.order(ByteOrder.BIG_ENDIAN);
            newBuf.put(accumulator);
            accumulator = newBuf;
        } else {
            accumulator.compact();
        }
        accumulator.put(incoming);
        accumulator.flip();

        while (accumulator.remaining() >= 4) {
            accumulator.mark();
            int bodyLength = accumulator.getInt();
            int frameLength = 4 + bodyLength;

            if (bodyLength < 0 || bodyLength > MessageType.MAX_FRAME_LENGTH) {
                // Corrupted frame, discard everything
                accumulator.clear();
                accumulator.flip();
                break;
            }

            if (accumulator.remaining() < bodyLength) {
                // Incomplete frame
                accumulator.reset();
                break;
            }

            byte[] frame = new byte[frameLength];
            accumulator.reset();
            accumulator.get(frame);
            frames.add(frame);
        }

        // Compact remaining bytes
        ByteBuffer remaining = ByteBuffer.allocate(accumulator.capacity());
        remaining.order(ByteOrder.BIG_ENDIAN);
        remaining.put(accumulator);
        remaining.flip();
        accumulator = remaining;

        return frames;
    }

    public void reset() {
        accumulator.clear();
        accumulator.flip();
    }
}
