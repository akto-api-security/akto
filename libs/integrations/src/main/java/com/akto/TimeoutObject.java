package com.akto;

public class TimeoutObject {
    private int connectTimeout;
    private int readTimeout;
    private int writeTimeout;

    public TimeoutObject(int connectTimeout, int readTimeout, int writeTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    public TimeoutObject() { }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeoutt(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

}
