package com.qiproxy.client.core;

import com.qiproxy.client.util.ProxyLogger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HttpSniffer - Lightweight HTTP header sniffer for TCP streams.
 * Extracts request/response summary lines for logging without interfering with data flow.
 */
public class HttpSniffer {

    private static final int MAX_BUFFER = 8192;

    private final String userId;
    private final String direction; // e.g. "-> RS" or "<- RS"
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean done = false;

    public HttpSniffer(String userId, boolean isRequest) {
        this.userId = userId;
        this.direction = isRequest ? "-> RS" : "<- RS";
    }

    /**
     * Feeds data into the sniffer. Non-blocking.
     */
    public void feed(byte[] data, int offset, int length) {
        if (done || data == null || length <= 0) {
            return;
        }
        int canWrite = Math.min(length, MAX_BUFFER - buffer.size());
        if (canWrite > 0) {
            buffer.write(data, offset, canWrite);
            tryParse();
        }
        if (buffer.size() >= MAX_BUFFER) {
            done = true; // Give up on huge non-HTTP data
        }
    }

    private void tryParse() {
        byte[] bytes = buffer.toByteArray();
        // Look for \r\n\r\n (HTTP header end)
        int end = findHeaderEnd(bytes);
        if (end < 0) {
            return; // Not enough data yet
        }

        String headers = new String(bytes, 0, end, StandardCharsets.UTF_8);
        String[] lines = headers.split("\r\n");
        if (lines.length == 0) {
            done = true;
            return;
        }

        String first = lines[0].trim();
        String host = extractHeader(lines, "Host");
        String contentType = extractHeader(lines, "Content-Type");

        if (first.startsWith("HTTP/")) {
            // Response
            ProxyLogger.i("[DATA " + direction + "][U" + userId + "] Response: " + first
                    + (host != null ? " | Host: " + host : "")
                    + (contentType != null ? " | Type: " + contentType : ""));
        } else if (first.contains("HTTP/")) {
            // Request
            ProxyLogger.i("[DATA " + direction + "][U" + userId + "] Request: " + first
                    + (host != null ? " | Host: " + host : "")
                    + (contentType != null ? " | Type: " + contentType : ""));
        } else {
            // Not HTTP or binary protocol
            ProxyLogger.d("[DATA " + direction + "][U" + userId + "] First packet: " + first);
        }
        done = true;
    }

    private static int findHeaderEnd(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String extractHeader(String[] lines, String name) {
        String prefix = name + ":";
        for (String line : lines) {
            if (line.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
