package com.akto.dto.testing.info;

public class NucleiTestInfo extends TestInfo {

    private String subcategory;
    private String templatePath;

    public NucleiTestInfo() {
        super();
    }

    public NucleiTestInfo(String subcategory, String templatePath) {
        super();
        this.subcategory = subcategory;
        this.templatePath = templatePath;
    }

    public String getSubcategory() {
        return this.subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getTemplatePath() {
        return this.templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    @Override
    public String toString() {
        return "{" +
            " subcategory='" + getSubcategory() + "'" +
            ", templatePath='" + getTemplatePath() + "'" +
            "}";
    }


}