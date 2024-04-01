package com.akto.action;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.units.qual.s;

import com.akto.action.observe.Utils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CodeAnalysisCollectionDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiCollection;
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

        /*
         * In some cases it is not possible to determine the type of template url from source code
         * In such cases, we can use the information from traffic endpoints to match the traffic and source code endpoints
         */
        List<BasicDBObject> trafficApis = Utils.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0);
        List<String> originalTrafficEndpoints = new ArrayList<>();
        Map<String, String> trafficEndpoints = new HashMap<>();
        for (BasicDBObject endpoint : trafficApis) {
            BasicDBObject apiInfoKey = (BasicDBObject) endpoint.get("_id");
            String method = apiInfoKey.getString("method");
            String url = apiInfoKey.getString("url");

            // extract path name from url
            try {
                URL parsedURL = new URL(url);
                String pathUrl = parsedURL.getPath(); 

                url = new URI(pathUrl).getPath()
                        .replace("%7B", "{")
                        .replace("%7D", "}");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error parsing URL: " + url, LogDb.DASHBOARD);
            }

            // Ensure url doesn't end with a slash
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            String originaEndpoint = method + " " + url;

             for (SuperType type : SuperType.values()) {
                // Replace each occurrence of Akto template url format with "AKTO_TEMPLATE_STRING"
                url = url.replace(type.name(), "AKTO_TEMPLATE_STR");
            }

            originalTrafficEndpoints.add(originaEndpoint);
            trafficEndpoints.put(method + " " + url, originaEndpoint);
        }

        Map<String, String> processedUrlsMap = new HashMap<>(urlsMap);
        for (Map.Entry<String, String> codeAnalysisApi : urlsMap.entrySet()) {
            String codeAnalysisApiEndpoint = codeAnalysisApi.getKey(); 
            String codeAnalysisApiLocation = codeAnalysisApi.getValue();
            String processedCodeAnalysisApiEndpoint = codeAnalysisApiEndpoint;

            for (SuperType type : SuperType.values()) {
                // Replace each occurrence of Akto template url format with "AKTO_TEMPLATE_STRING"
                processedCodeAnalysisApiEndpoint = processedCodeAnalysisApiEndpoint.replace(type.name(), "AKTO_TEMPLATE_STR");
            }

            if (processedCodeAnalysisApiEndpoint.contains("AKTO_TEMPLATE_STR") && trafficEndpoints.containsKey(processedCodeAnalysisApiEndpoint)) {
                    processedUrlsMap.remove(codeAnalysisApiEndpoint);
                    processedUrlsMap.put(trafficEndpoints.get(processedCodeAnalysisApiEndpoint), codeAnalysisApiLocation);  
            }
        }

        /*
         * Match endpoints between traffic and source code endpoints, when only method is different
         */
        for (String originalTrafficEndpoint : originalTrafficEndpoints) {
            if (!processedUrlsMap.containsKey(originalTrafficEndpoint)) {
                Set<String> codeAnalysisEndpoints = processedUrlsMap.keySet();
                String[] originalTrafficEndpointParts = originalTrafficEndpoint.split(" ");
                String originalTrafficEndpointPartsUrl = "";

                if (originalTrafficEndpointParts.length == 2) {
                    originalTrafficEndpointPartsUrl = originalTrafficEndpointParts[1];
                }

                for (String codeAnalysisEndpoint : codeAnalysisEndpoints) {
                    if (codeAnalysisEndpoint.contains(originalTrafficEndpointPartsUrl)) {
                        processedUrlsMap.put(originalTrafficEndpoint, processedUrlsMap.get(codeAnalysisEndpoint));
                        break;
                    }
                }
            }
        }

        CodeAnalysisCollectionDao.instance.updateOne(
            Filters.eq("codeAnalysisCollectionName", apiCollectionName),
            Updates.combine(
                    Updates.setOnInsert(CodeAnalysisCollection.NAME, apiCollectionName),
                    Updates.set(CodeAnalysisCollection.URLS_MAP, processedUrlsMap),
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
