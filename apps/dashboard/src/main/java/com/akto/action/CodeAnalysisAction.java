package com.akto.action;

import java.util.Map;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CodeAnalysisCollectionDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.CodeAnalysisCollection;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class CodeAnalysisAction extends UserAction {

    private String projectDir;
    private String apiCollectionName;
    private Map<String, String> urlsMap;

    private static final LoggerMaker loggerMaker = new LoggerMaker(CodeAnalysisAction.class);
    
    public String syncExtractedAPIs() {
        loggerMaker.infoAndAddToDb("Syncing code analysis endpoints for: " + projectDir, LogDb.DASHBOARD);

        // todo:  If API collection does exist, create it
        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(apiCollectionName);
        if (apiCollection == null) {
            loggerMaker.errorAndAddToDb("API collection not found " + apiCollectionName, LogDb.DASHBOARD);
            addActionError("API collection not found: " + apiCollectionName);
            return ERROR.toUpperCase();
        }

        CodeAnalysisCollectionDao.instance.updateOne(
            Filters.eq("codeAnalysisCollectionName", apiCollectionName),
            Updates.combine(
                    Updates.setOnInsert(CodeAnalysisCollection.NAME, apiCollectionName),
                    Updates.set(CodeAnalysisCollection.URLS_MAP, urlsMap),
                    Updates.set(CodeAnalysisCollection.PROJECT_DIR, projectDir)
            )
        );

        loggerMaker.infoAndAddToDb("Updated code analysis collection: " + apiCollectionName, LogDb.DASHBOARD);
        loggerMaker.infoAndAddToDb("Source code endpoints: " + urlsMap.size(), LogDb.DASHBOARD);

        return SUCCESS.toUpperCase();
    }

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }

    public String getApiCollectionName() {
        return apiCollectionName;
    }

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public Map<String, String> getUrlsMap() {
        return urlsMap;
    }

    public void setUrlsMap(Map<String, String> urlsMap) {
        this.urlsMap = urlsMap;
    }

}
