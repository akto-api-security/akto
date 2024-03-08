package com.akto.dto.test_editor;

public class Category {
    
    private String name;

    private String displayName;

    private String shortName;

    public Category(String name, String displayName, String shortName) {
        this.name = name;
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public Category() { }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
    
}
