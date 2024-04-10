package com.akto.dto;

public class CodeAnalysisApi {

    private String method;
    private String endpoint;
    private Location location;

    public static class Location {
        private String filepath;

        public Location() {
        }

        public Location(String filepath) {
            this.filepath = filepath;
        }

        public String getFilepath() {
            return filepath;
        }

        public void setFilepath(String filepath) {
            this.filepath = filepath;
        }
    }

    public CodeAnalysisApi() {
    }

    public CodeAnalysisApi(String method, String endpoint, Location location) {
        this.method = method;
        this.endpoint = endpoint;
        this.location = location;
    }

    public String generateCodeAnalysisApiKey() {
        return method + " " + endpoint;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
