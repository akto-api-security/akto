package com.akto.dto.type;

public class CollectionReplaceDetails {
    String regex;
    String newName;


    public CollectionReplaceDetails() {
    }

    public CollectionReplaceDetails(String regex, String newName) {
        this.regex = regex;
        this.newName = newName;
    }

    public String getRegex() {
        return this.regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getNewName() {
        return this.newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }
}