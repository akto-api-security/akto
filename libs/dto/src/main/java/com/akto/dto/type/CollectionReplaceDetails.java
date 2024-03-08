package com.akto.dto.type;

public class CollectionReplaceDetails {
    String regex;
    String newName;
    String headerName;

    public CollectionReplaceDetails() {
    }

    public CollectionReplaceDetails(String regex, String newName, String headerName) {
        this.regex = regex;
        this.newName = newName;
        this.headerName = headerName;
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

    public String getHeaderName() {
        return this.headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}