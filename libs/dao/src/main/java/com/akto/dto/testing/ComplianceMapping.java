package com.akto.dto.testing;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class ComplianceMapping {
    
    @BsonId
    String id;

    private Map<String, List<String>> mapComplianceToListClauses;

    String author;

    int lastUpdatedTs;

    String source;

    int hash;


    public ComplianceMapping() {
    }

    public ComplianceMapping(String id, Map<String,List<String>> mapComplianceToListClauses, String author, int lastUpdatedTs, String source, int hash) {
        this.id = id;
        this.mapComplianceToListClauses = mapComplianceToListClauses;
        this.author = author;
        this.lastUpdatedTs = lastUpdatedTs;
        this.source = source;
        this.hash = hash;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
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

    public int getLastUpdatedTs() {
        return this.lastUpdatedTs;
    }

    public void setLastUpdatedTs(int lastUpdatedTs) {
        this.lastUpdatedTs = lastUpdatedTs;
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
            " id='" + getId() + "'" +
            ", mapComplianceToListClauses='" + getMapComplianceToListClauses() + "'" +
            ", author='" + getAuthor() + "'" +
            ", lastUpdatedTs='" + getLastUpdatedTs() + "'" +
            ", source='" + getSource() + "'" +
            ", hash='" + getHash() + "'" +
            "}";
    }
    
}
