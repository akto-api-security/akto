package com.akto.dto.test_editor;

import java.util.List;

public class Info {
    
    private String name;

    private String description;

    private String details;

    private String impact;

    private Category category;

    private String subCategory;

    private String confidence;

    private String severity;

    private List<String> tags;

    private List<String> references;

    private String author;

    public Info(String name, String description, String details, String impact, Category category, String subCategory, String confidence,
            String severity, List<String> tags, List<String> references, String author) {
        this.name = name;
        this.description = description;
        this.details = details;
        this.impact = impact;
        this.category = category;
        this.subCategory = subCategory;
        this.confidence = confidence;
        this.severity = severity;
        this.tags = tags;
        this.references = references;
        this.author = author;
    }

    public Info() { }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
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
    
}
