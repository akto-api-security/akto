package com.akto.dto.testing;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultTestSuites {
    private ObjectId id;
    public static final String CREATE_AT = "createAt";
    private int createdAt;
    public static final String CREATE_BY = "createBy";
    private String createdBy;
    public static final String LAST_UPDATED = "lastUpdated";
    private int lastUpdated;
    public static final String NAME = "name";
    private String name;
    public static final String SUB_CATEGORY_LIST = "subCategoryList";
    private List<String> subCategoryList;
    public static final String SUITE_TYPE = "suiteType";
    private DefaultSuitesType suiteType;

    @BsonIgnore
    private String hexId;

    public DefaultTestSuites() {}

    public DefaultTestSuites(int createdAt, String createdBy, int lastUpdated, String name, List<String> subCategoryList, DefaultSuitesType suiteType) {
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.lastUpdated = lastUpdated;
        this.name = name;
        this.subCategoryList = subCategoryList;
        this.suiteType = suiteType;
    }

    public enum DefaultSuitesType {
        OWASP,
        TESTING_METHODS,
        SEVERITY
    }

    public static final Map<String, List<String>> owaspTop10List = new HashMap<>();
    static {
        owaspTop10List.put("Broken Object Level Authorization", Arrays.asList("BOLA"));
        owaspTop10List.put("Broken Authentication", Arrays.asList("NO_AUTH"));
        owaspTop10List.put("Broken Object Property Level Authorization", Arrays.asList("EDE", "MA"));
        owaspTop10List.put("Unrestricted Resource Consumption", Arrays.asList("RL"));
        owaspTop10List.put("Broken Function Level Authorization", Arrays.asList("BFLA"));
        owaspTop10List.put("Unrestricted Access to Sensitive Business Flows", Arrays.asList("INPUT"));
        owaspTop10List.put("Server Side Request Forgery", Arrays.asList("SSRF"));
        owaspTop10List.put("Security Misconfiguration", Arrays.asList("SM", "UHM", "VEM", "MHH", "SVD", "CORS", "ILM"));
        owaspTop10List.put("Improper Inventory Management", Arrays.asList("IAM", "IIM"));
        owaspTop10List.put("Unsafe Consumption of APIs", Arrays.asList("COMMAND_INJECTION", "INJ", "CRLF", "SSTI", "LFI", "XSS", "INJECT"));
    }


    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public int getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSubCategoryList() {
        return subCategoryList;
    }

    public void setSubCategoryList(List<String> subCategoryList) {
        this.subCategoryList = subCategoryList;
    }

    public DefaultSuitesType getSuiteType() {
        return suiteType;
    }

    public void setSuiteType(DefaultSuitesType suiteType) {
        this.suiteType = suiteType;
    }

    public String getHexId() {
        if (hexId == null) {
            return (this.id != null) ? this.id.toHexString() : null;
        }
        return this.hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
}
