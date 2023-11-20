package com.akto.dto.demo;

import com.akto.dto.ApiInfo;

import java.util.Arrays;
import java.util.List;

public class VulnerableRequestForTemplate {
    private ApiInfo.ApiInfoKey id;
    private List<String> templateIds;
    List<Integer> collectionIds;

    public VulnerableRequestForTemplate() {
    }
    public VulnerableRequestForTemplate(ApiInfo.ApiInfoKey id, List<String> templateIds) {
        this.id = id;
        this.templateIds = templateIds;
        if(id != null){
            this.collectionIds = Arrays.asList(id.getApiCollectionId());
        }
    }

    public ApiInfo.ApiInfoKey getId() {
        return id;
    }

    public void setId(ApiInfo.ApiInfoKey id) {
        this.id = id;
    }

    public List<String> getTemplateIds() {
        return templateIds;
    }

    public void setTemplateIds(List<String> templateIds) {
        this.templateIds = templateIds;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }
}
