package com.akto.dto.linear_integration;

import java.util.List;

public class IssueTemplate {

    public static final String TITLE = "title";
    private String title;

    public static final String DESCRIPTION = "description";
    private String description;

    public static final String INCLUDE_DETAILS = "includeDetails";
    private boolean includeDetails;

    public static final String INCLUDE_API_INFO = "includeApiInfo";
    private boolean includeApiInfo;

    public static final String LABELS = "labels";
    private List<String> labels;

    public IssueTemplate() {
    }

    public IssueTemplate(String title, String description, boolean includeDetails, boolean includeApiInfo,
                         List<String> labels) {
        this.title = title;
        this.description = description;
        this.includeDetails = includeDetails;
        this.includeApiInfo = includeApiInfo;
        this.labels = labels;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isIncludeDetails() {
        return includeDetails;
    }

    public void setIncludeDetails(boolean includeDetails) {
        this.includeDetails = includeDetails;
    }

    public boolean isIncludeApiInfo() {
        return includeApiInfo;
    }

    public void setIncludeApiInfo(boolean includeApiInfo) {
        this.includeApiInfo = includeApiInfo;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
}
