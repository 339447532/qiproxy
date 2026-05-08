package com.qiproxy.client.protocol;

import java.util.Arrays;

/**
 * ProxyMessage - Protocol message POJO
 * Mirrors Node.js ProxyMessage and Java ProxyMessage
 */
public class ProxyMessage {
    private int type;
    private long serialNumber;
    private String uri;
    private byte[] data;

    public ProxyMessage() {
        this.type = 0;
        this.serialNumber = 0;
        this.uri = "";
        this.data = null;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(long serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ProxyMessage [type=" + type
                + ", serialNumber=" + serialNumber
                + ", uri=" + uri
                + ", data=" + (data != null ? "[" + data.length + " bytes]" : "null")
                + "]";
    }
}
