package com.akto.dto;

public class CodeAnalysisApiLocation {
    private String filePath;
    private String fileName;
    private String fileLink;
    private int lineNo;

    public CodeAnalysisApiLocation() {
    }

    public CodeAnalysisApiLocation(String filePath, String fileName, String fileLink, int lineNo) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileLink = fileLink;
        this.lineNo = lineNo;
    }

    @Override
    public String toString() {
        return "CodeAnalysisApiLocation{" +
                "filePath='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileLink='" + fileLink + '\'' +
                ", lineNo=" + lineNo +
                '}';
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