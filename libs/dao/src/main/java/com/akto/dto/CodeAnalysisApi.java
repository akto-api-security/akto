package com.akto.dto;

public class CodeAnalysisApi {

    private String method;
    private String endpoint;
    private Location location;

    public static class Location {
        private String filePath;
        private String fileName;
        private String fileLink;
        private int lineNo;

        public Location() {
        }

        public Location(String filePath, String fileName, String fileLink, int lineNo) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.fileLink = fileLink;
            this.lineNo = lineNo;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFileLink() {
            return fileLink;
        }

        public void setFileLink(String fileLink) {
            this.fileLink = fileLink;
        }

        public int getLineNo() {
            return lineNo;
        }

        public void setLineNo(int lineNo) {
            this.lineNo = lineNo;
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
