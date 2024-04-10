package com.akto.action;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.Code;
import org.checkerframework.checker.units.qual.s;

import com.akto.action.observe.Utils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CodeAnalysisCollectionDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.CodeAnalysisApi;
import com.akto.dto.CodeAnalysisCollection;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class CodeAnalysisAction extends UserAction {

    private String projectDir;
    private String apiCollectionName;
    private Map<String, CodeAnalysisApi> codeAnalysisApisMap;

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

        /*
         * In some cases it is not possible to determine the type of template url from source code
         * In such cases, we can use the information from traffic endpoints to match the traffic and source code endpoints
         * 
         * Eg:
         * Source code endpoints:
         * GET /books/STRING -> GET /books/AKTO_TEMPLATE_STR -> GET /books/INTEGER
         * POST /city/STRING/district/STRING -> POST /city/AKTO_TEMPLATE_STR/district/AKTO_TEMPLATE_STR -> POST /city/STRING/district/INTEGER
         * Traffic endpoints:
         * GET /books/INTEGER -> GET /books/AKTO_TEMPLATE_STR
         * POST /city/STRING/district/INTEGER -> POST /city/AKTO_TEMPLATE_STR/district/AKTO_TEMPLATE_STR
         */
        List<BasicDBObject> trafficApis = Utils.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0);
        Map<String, String> trafficApiEndpointAktoTemplateStrToOriginalMap = new HashMap<>();
        List<String> trafficApiKeys = new ArrayList<>();
        for (BasicDBObject trafficApi: trafficApis) {
            BasicDBObject trafficApiApiInfoKey = (BasicDBObject) trafficApi.get("_id");
            String trafficApiMethod = trafficApiApiInfoKey.getString("method");
            String trafficApiUrl = trafficApiApiInfoKey.getString("url");
            String trafficApiEndpoint = "";

            // extract path name from url
            try {
                URL url = new URL(trafficApiUrl);
                trafficApiEndpoint = url.getPath(); 

                trafficApiEndpoint = new URI(trafficApiEndpoint).getPath()
                        .replace("%7B", "{")
                        .replace("%7D", "}");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error parsing URL: " + trafficApiUrl, LogDb.DASHBOARD);
            }

            // Ensure endpoint doesn't end with a slash
            if (trafficApiEndpoint.length() > 1 && trafficApiEndpoint.endsWith("/")) {
                trafficApiEndpoint = trafficApiEndpoint.substring(0, trafficApiEndpoint.length() - 1);
            }

            String trafficApiKey = trafficApiMethod + " " + trafficApiEndpoint;
            trafficApiKeys.add(trafficApiKey);

            String trafficApiEndpointAktoTemplateStr = trafficApiEndpoint;

            for (SuperType type : SuperType.values()) {
                // Replace each occurrence of Akto template url format with"AKTO_TEMPLATE_STRING"
                trafficApiEndpointAktoTemplateStr = trafficApiEndpointAktoTemplateStr.replace(type.name(), "AKTO_TEMPLATE_STR");
            }

            trafficApiEndpointAktoTemplateStrToOriginalMap.put(trafficApiEndpointAktoTemplateStr, trafficApiEndpoint);
        }

        Map<String, CodeAnalysisApi> tempCodeAnalysisApisMap = new HashMap<>(codeAnalysisApisMap);
        for (Map.Entry<String, CodeAnalysisApi> codeAnalysisApiEntry: codeAnalysisApisMap.entrySet()) {
            String codeAnalysisApiKey = codeAnalysisApiEntry.getKey();
            CodeAnalysisApi codeAnalysisApi = codeAnalysisApiEntry.getValue();

            String codeAnalysisApiEndpoint = codeAnalysisApi.getEndpoint();

            String codeAnalysisApiEndpointAktoTemplateStr = codeAnalysisApiEndpoint;

            for (SuperType type : SuperType.values()) {
                // Replace each occurrence of Akto template url format with "AKTO_TEMPLATE_STRING"
                codeAnalysisApiEndpointAktoTemplateStr = codeAnalysisApiEndpointAktoTemplateStr.replace(type.name(), "AKTO_TEMPLATE_STR");
            }

            if(codeAnalysisApiEndpointAktoTemplateStr.contains("AKTO_TEMPLATE_STR") && trafficApiEndpointAktoTemplateStrToOriginalMap.containsKey(codeAnalysisApiEndpointAktoTemplateStr)) {
               CodeAnalysisApi newCodeAnalysisApi = new CodeAnalysisApi(
                    codeAnalysisApi.getMethod(), 
                    trafficApiEndpointAktoTemplateStrToOriginalMap.get(codeAnalysisApiEndpointAktoTemplateStr), 
                    codeAnalysisApi.getLocation());
                
                tempCodeAnalysisApisMap.remove(codeAnalysisApiKey);
                tempCodeAnalysisApisMap.put(newCodeAnalysisApi.generateCodeAnalysisApiKey(), newCodeAnalysisApi);
            }
        }


        /*
         * Match endpoints between traffic and source code endpoints, when only method is different
         * Eg:
         * Source code endpoints:
         * POST /books
         * Traffic endpoints:
         * PUT /books
         * Add PUT /books to source code endpoints
         */
        for(String trafficApiKey: trafficApiKeys) {
            if (!codeAnalysisApisMap.containsKey(trafficApiKey)) {
                for(Map.Entry<String, CodeAnalysisApi> codeAnalysisApiEntry: tempCodeAnalysisApisMap.entrySet()) {
                    CodeAnalysisApi codeAnalysisApi = codeAnalysisApiEntry.getValue();
                    String codeAnalysisApiEndpoint = codeAnalysisApi.getEndpoint();
                   
                    String trafficApiMethod = "", trafficApiEndpoint = "";
                    try {
                        String[] trafficApiKeyParts = trafficApiKey.split(" ");
                        trafficApiMethod = trafficApiKeyParts[0];
                        trafficApiEndpoint = trafficApiKeyParts[1];
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error parsing traffic API key: " + trafficApiKey, LogDb.DASHBOARD);
                    }

                    if (codeAnalysisApiEndpoint.equals(trafficApiEndpoint)) {
                        CodeAnalysisApi newCodeAnalysisApi = new CodeAnalysisApi(
                            trafficApiMethod, 
                            trafficApiEndpoint, 
                            codeAnalysisApi.getLocation());
                        
                        tempCodeAnalysisApisMap.put(newCodeAnalysisApi.generateCodeAnalysisApiKey(), newCodeAnalysisApi);
                        break;
                    }
                }
            }
        }

        codeAnalysisApisMap = tempCodeAnalysisApisMap;

        CodeAnalysisCollectionDao.instance.updateOne(
            Filters.eq("codeAnalysisCollectionName", apiCollectionName),
            Updates.combine(
                    Updates.setOnInsert(CodeAnalysisCollection.NAME, apiCollectionName),
                    Updates.set(CodeAnalysisCollection.CODE_ANALYSIS_APIS_MAP, codeAnalysisApisMap),
                    Updates.set(CodeAnalysisCollection.PROJECT_DIR, projectDir)
            )
        );

        loggerMaker.infoAndAddToDb("Updated code analysis collection: " + apiCollectionName, LogDb.DASHBOARD);
        loggerMaker.infoAndAddToDb("Source code endpoints: " + codeAnalysisApisMap.size(), LogDb.DASHBOARD);

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

    public Map<String, CodeAnalysisApi> getCodeAnalysisApisMap() {
        return codeAnalysisApisMap;
    }

    public void setCodeAnalysisApisMap(Map<String, CodeAnalysisApi> codeAnalysisApisMap) {
        this.codeAnalysisApisMap = codeAnalysisApisMap;
    }
}
