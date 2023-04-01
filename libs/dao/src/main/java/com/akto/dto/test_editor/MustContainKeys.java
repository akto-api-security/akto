package com.akto.dto.test_editor;

import java.util.List;

public class MustContainKeys {
    
    private List<String> location;

    private List<String> keyRegex;

    public MustContainKeys(List<String> location, List<String> keyRegex) {
        this.location = location;
        this.keyRegex = keyRegex;
    }

    public MustContainKeys() {
    }

    public List<String> getLocation() {
        return location;
    }

    public void setLocation(List<String> location) {
        this.location = location;
    }

    public List<String> getKeyRegex() {
        return keyRegex;
    }

    public void setKeyRegex(List<String> keyRegex) {
        this.keyRegex = keyRegex;
    }

}
