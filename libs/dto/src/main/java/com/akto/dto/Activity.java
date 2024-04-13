package com.akto.dto;

public class Activity {

    public static final String TIME_STAMP = "timestamp";
    private int timestamp ;

    public static final String TYPE = "type";
    private String type ;

    public static final String DESCRIPTION = "description";
    private String description ;

    public Activity () {}

    public Activity (String type, String description, int timestamp){
        this.type = type;
        this.timestamp = timestamp;
        this.description = description;
    }
    
    public int getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

}
