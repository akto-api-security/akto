package com.akto.dto.testing.sources;

import java.util.List;

import com.akto.util.enums.GlobalEnums.Severity;

public class TestSourceConfig {
    public enum TestCategory {
        BOLA, BUA, BFLA, MA, INJ
    }

    public enum TestSourceType {
        wordlist
    }

    public enum ApiModifiers {
        url, method, headers, queryParams, payload
    }
    private TestSourceType type;
    private List<String> sources;

    private TestCategory category;
    private String subcategory;
    private Severity severity;

    private TestTemplate requests;


    public TestSourceConfig() {
    }

    public TestSourceConfig(TestSourceType type, List<String> sources, TestCategory category, String subcategory, Severity severity, TestTemplate requests) {
        this.type = type;
        this.sources = sources;
        this.category = category;
        this.subcategory = subcategory;
        this.severity = severity;
        this.requests = requests;
    }

    public TestSourceType getType() {
        return this.type;
    }

    public void setType(TestSourceType type) {
        this.type = type;
    }

    public List<String> getSources() {
        return this.sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public TestCategory getCategory() {
        return this.category;
    }

    public void setCategory(TestCategory category) {
        this.category = category;
    }

    public String getSubcategory() {
        return this.subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public Severity getSeverity() {
        return this.severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public TestTemplate getRequests() {
        return this.requests;
    }

    public void setRequests(TestTemplate requests) {
        this.requests = requests;
    }

    @Override
    public String toString() {
        return "{" +
            " type='" + getType() + "'" +
            ", sources='" + getSources() + "'" +
            ", category='" + getCategory() + "'" +
            ", subcategory='" + getSubcategory() + "'" +
            ", severity='" + getSeverity() + "'" +
            ", requests='" + getRequests() + "'" +
            "}";
    }
}
