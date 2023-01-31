package com.akto.dto;

public class Account {
    private int id;
    private String name;
    private boolean isDefault = false;
    private String timezone = "US/Pacific";

    public Account() {}

    public Account(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
