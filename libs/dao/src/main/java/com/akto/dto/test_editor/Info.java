package com.akto.dto.test_editor;

import java.util.List;

public class Info {
    
    private String name;

    private String description;

    private String details;

    private String impact;

    private Category category;

    private String subCategory;

    private String severity;

    private List<String> tags;

    private List<String> references;

    private List<String> cwe;

    private List<String> cve;

    public Info(String name, String description, String details, String impact, Category category, String subCategory,
            String severity, List<String> tags, List<String> references, List<String> cwe, List<String> cve) {
        this.name = name;
        this.description = description;
        this.details = details;
        this.impact = impact;
        this.category = category;
        this.subCategory = subCategory;
        this.severity = severity;
        this.tags = tags;
        this.references = references;
        this.cwe = cwe;
        this.cve = cve;
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

    public List<String> getCwe() {
        return cwe;
    }

    public void setCwe(List<String> cwe) {
        this.cwe = cwe;
    }

    public List<String> getCve() {
        return cve;
    }

    public void setCve(List<String> cve) {
        this.cve = cve;
    }

}
