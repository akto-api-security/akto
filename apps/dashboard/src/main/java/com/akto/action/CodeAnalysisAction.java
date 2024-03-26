package com.akto.action;

import java.util.Map;

import com.akto.dao.CodeAnalysisCollectionDao;
import com.akto.dto.CodeAnalysisCollection;

public class CodeAnalysisAction extends UserAction {

    private String codeAnalysisCollectionName;
    private Map<String, String> urlsMap;
    
    public String syncExtractedAPIs() {
        System.out.println(codeAnalysisCollectionName);
        CodeAnalysisCollection codeAnalysisCollection = new CodeAnalysisCollection(codeAnalysisCollectionName, urlsMap);

        CodeAnalysisCollectionDao.instance.insertOne(codeAnalysisCollection);
        System.out.println(codeAnalysisCollection.getName());
        return SUCCESS.toUpperCase();
    }

    public String getCodeAnalysisCollectionName() {
        return codeAnalysisCollectionName;
    }

    public void setCodeAnalysisCollectionName(String codeAnalysisCollectionName) {
        this.codeAnalysisCollectionName = codeAnalysisCollectionName;
    }

    public Map<String, String> getUrlsMap() {
        return urlsMap;
    }

    public void setUrlsMap(Map<String, String> urlsMap) {
        this.urlsMap = urlsMap;
    }

}
