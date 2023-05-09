package com.akto.dto.testing.sources;

import java.util.List;

import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestCategory;

public class TestSourceConfig {
    private String id;

    private TestCategory category;
    public static final String CATEGORY = "category";

    private String subcategory;
    public static final String SUBCATEGORY = "subcategory";
    
    private Severity severity;
    private String description;

    private String creator;
    public static final String CREATOR = "creator";
    public static final String DEFAULT = "default";

    private int addedEpoch;
    private int stars;
    private int installs;
    private List<String> tags;

    public TestSourceConfig() {
    }

    public TestSourceConfig(String id, TestCategory category, String subcategory, Severity severity, String description, String creator, int addedEpoch, List<String> tags) {
        this.id = id;
        this.category = category;
        this.subcategory = subcategory;
        this.severity = severity;
        this.description = description;
        this.creator = creator;
        this.addedEpoch = addedEpoch;
        this.tags = tags;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreator() {
        return this.creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public int getAddedEpoch() {
        return this.addedEpoch;
    }

    public void setAddedEpoch(int addedEpoch) {
        this.addedEpoch = addedEpoch;
    }

    public int getStars() {
        return this.stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public int getInstalls() {
        return this.installs;
    }

    public void setInstalls(int installs) {
        this.installs = installs;
    }

    public List<String> getTags(){
        return this.tags;
    } 

    public void setTags(List<String> tags){
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "{" +
            " sourceURL='" + getId() + "'" +
            ", category='" + getCategory() + "'" +
            ", subcategory='" + getSubcategory() + "'" +
            ", severity='" + getSeverity() + "'" +
            ", description='" + getDescription() + "'" +
            ", creator='" + getCreator() + "'" +
            ", addedEpoch='" + getAddedEpoch() + "'" +
            ", stars='" + getStars() + "'" +
            ", installs='" + getInstalls() + "'" +
            ", tags='" + getTags() + "'" +
            "}";
    }
}