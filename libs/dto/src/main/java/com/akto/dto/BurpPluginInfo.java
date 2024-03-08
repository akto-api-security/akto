package com.akto.dto;

public class BurpPluginInfo {

    public static final String USERNAME = "username";
    private String username;
    public static final String LAST_BOOT_UP_TIMESTAMP = "lastBootupTimestamp";
    private int lastBootupTimestamp;
    public static final String LAST_DATA_SENT_TIMESTAMP = "lastDataSentTimestamp";
    private int lastDataSentTimestamp;
    public static final String VERSION = "version";
    private int version;
    public static final String LAST_DOWNLOAD_TIMESTAMP = "lastDownloadTimestamp";
    public int lastDownloadTimestamp;

    public BurpPluginInfo() {

    }

    public BurpPluginInfo(String username, int lastBootupTimestamp, int lastDataSentTimestamp, int version) {
        this.username = username;
        this.lastBootupTimestamp = lastBootupTimestamp;
        this.lastDataSentTimestamp = lastDataSentTimestamp;
        this.version = version;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getLastBootupTimestamp() {
        return lastBootupTimestamp;
    }

    public void setLastBootupTimestamp(int lastBootupTimestamp) {
        this.lastBootupTimestamp = lastBootupTimestamp;
    }

    public int getLastDataSentTimestamp() {
        return lastDataSentTimestamp;
    }

    public void setLastDataSentTimestamp(int lastDataSentTimestamp) {
        this.lastDataSentTimestamp = lastDataSentTimestamp;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    

}
