package com.akto.dto.testing;

import java.util.List;
import java.util.Map;

public class ComplianceMapping {
    
    private Map<String, List<String>> mapComplianceToListClauses;

    private String author;

    public static final String SOURCE = "source";
    private String source;

    private int hash;

    public ComplianceMapping() {
    }

    public ComplianceMapping(Map<String,List<String>> mapComplianceToListClauses, String author, String source, int hash) {
        this.mapComplianceToListClauses = mapComplianceToListClauses;
        this.author = author;
        this.source = source;
        this.hash = hash;
    }

    public static ComplianceMapping createFromInfo(ComplianceInfo complianceInfo) {
        return new ComplianceMapping(complianceInfo.getMapComplianceToListClauses(), complianceInfo.getAuthor(), complianceInfo.getId(), complianceInfo.getHash());
    }

    public Map<String,List<String>> getMapComplianceToListClauses() {
        return this.mapComplianceToListClauses;
    }

    public void setMapComplianceToListClauses(Map<String,List<String>> mapComplianceToListClauses) {
        this.mapComplianceToListClauses = mapComplianceToListClauses;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getHash() {
        return this.hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }

    @Override
    public String toString() {
        return "{" +
            ", mapComplianceToListClauses='" + getMapComplianceToListClauses() + "'" +
            ", author='" + getAuthor() + "'" +
            ", source='" + getSource() + "'" +
            ", hash='" + getHash() + "'" +
            "}";
    }
    
}
