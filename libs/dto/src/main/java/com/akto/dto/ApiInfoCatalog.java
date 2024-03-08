package com.akto.dto;

import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApiInfoCatalog {
    private Map<URLStatic, PolicyCatalog> strictURLToMethods;
    private Map<URLTemplate, PolicyCatalog> templateURLToMethods;
    private List<ApiInfo.ApiInfoKey> deletedInfo = new ArrayList<>();

    public ApiInfoCatalog() {
    }

    public ApiInfoCatalog(Map<URLStatic, PolicyCatalog> strictURLToMethods, Map<URLTemplate, PolicyCatalog> templateURLToMethods, List<ApiInfo.ApiInfoKey> deletedInfo) {
        this.strictURLToMethods = strictURLToMethods;
        this.templateURLToMethods = templateURLToMethods;
        this.deletedInfo = deletedInfo;
    }

    public Map<URLStatic, PolicyCatalog> getStrictURLToMethods() {
        return strictURLToMethods;
    }

    public void setStrictURLToMethods(Map<URLStatic, PolicyCatalog> strictURLToMethods) {
        this.strictURLToMethods = strictURLToMethods;
    }

    public Map<URLTemplate, PolicyCatalog> getTemplateURLToMethods() {
        return templateURLToMethods;
    }

    public void setTemplateURLToMethods(Map<URLTemplate, PolicyCatalog> templateURLToMethods) {
        this.templateURLToMethods = templateURLToMethods;
    }

    public List<ApiInfo.ApiInfoKey> getDeletedInfo() {
        return deletedInfo;
    }

    public void setDeletedInfo(List<ApiInfo.ApiInfoKey> deletedInfo) {
        this.deletedInfo = deletedInfo;
    }
}
