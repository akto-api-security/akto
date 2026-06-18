package com.akto.dto.threat_detection;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class GuardrailComplianceInfo {

    @BsonId
    private String id;

    public static final String MAP_COMPLIANCE_TO_LIST_CLAUSES = "mapComplianceToListClauses";
    private Map<String, List<String>> mapComplianceToListClauses;

    public static final String AUTHOR = "author";
    private String author;

    public static final String HASH = "hash";
    private int hash;

    private String sourcePath;

    public GuardrailComplianceInfo() {
    }

    public GuardrailComplianceInfo(String id, Map<String, List<String>> mapComplianceToListClauses, String author, int hash, String sourcePath) {
        this.id = id;
        this.mapComplianceToListClauses = mapComplianceToListClauses;
        this.author = author;
        this.hash = hash;
        this.sourcePath = sourcePath;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, List<String>> getMapComplianceToListClauses() {
        return mapComplianceToListClauses;
    }

    public void setMapComplianceToListClauses(Map<String, List<String>> mapComplianceToListClauses) {
        this.mapComplianceToListClauses = mapComplianceToListClauses;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getHash() {
        return hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
}
