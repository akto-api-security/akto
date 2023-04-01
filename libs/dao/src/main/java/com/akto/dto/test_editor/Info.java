package com.akto.dto.test_editor;

import java.util.List;

public class Info {
    
    private String message;
    private String category;

    private String subCategory;

    private String severity;

    private String confidence;

    private String tags;

    private List<String> cwe;

    private List<String> owasp;

    private String testUrl;

    private List<String> references;

    private String author;

    private String howToFix;

    public Info(String message, String category, String subCategory, String severity, String confidence, String tags,
            List<String> cwe, List<String> owasp, String testUrl, List<String> references, String author,
            String howToFix) {
        this.message = message;
        this.category = category;
        this.subCategory = subCategory;
        this.severity = severity;
        this.confidence = confidence;
        this.tags = tags;
        this.cwe = cwe;
        this.owasp = owasp;
        this.testUrl = testUrl;
        this.references = references;
        this.author = author;
        this.howToFix = howToFix;
    }

    public Info() { }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public List<String> getCwe() {
        return cwe;
    }

    public void setCwe(List<String> cwe) {
        this.cwe = cwe;
    }

    public List<String> getOwasp() {
        return owasp;
    }

    public void setOwasp(List<String> owasp) {
        this.owasp = owasp;
    }

    public String getTestUrl() {
        return testUrl;
    }

    public void setTestUrl(String testUrl) {
        this.testUrl = testUrl;
    }

    public List<String> getReferences() {
        return references;
    }

    public void setReferences(List<String> references) {
        this.references = references;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getHowToFix() {
        return howToFix;
    }

    public void setHowToFix(String howToFix) {
        this.howToFix = howToFix;
    }

}
