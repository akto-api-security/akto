package com.akto.action;


import java.net.URI;
import java.util.*;

import com.akto.dao.*;
import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import com.opensymphony.xwork2.ActionSupport;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.util.HttpRequestResponseUtils.generateSTIsFromPayload;

public class CodeAnalysisAction extends ActionSupport {

    private String projectName;
    private String repoName;
    private boolean isLastBatch;
    private List<CodeAnalysisApi> codeAnalysisApisList;
    private CodeAnalysisRepo codeAnalysisRepo;
    public static final int MAX_BATCH_SIZE = 100;

    private static final LoggerMaker loggerMaker = new LoggerMaker(CodeAnalysisAction.class);

    public String syncExtractedAPIs() {
        String apiCollectionName = projectName + "/" + repoName;
        loggerMaker.infoAndAddToDb("Syncing code analysis endpoints for collection: " + apiCollectionName, LogDb.DASHBOARD);

        if (codeAnalysisApisList == null) {
            loggerMaker.errorAndAddToDb("Code analysis api's list is null", LogDb.DASHBOARD);
            addActionError("Code analysis api's list is null");
            return ERROR.toUpperCase();
        }

        if (codeAnalysisRepo == null) {
            loggerMaker.errorAndAddToDb("Code analysis repo is null", LogDb.DASHBOARD);
            addActionError("Code analysis repo is null");
            return ERROR.toUpperCase();
        }

        // Ensure batch size is not exceeded
        if (codeAnalysisApisList.size() > MAX_BATCH_SIZE) {
            String errorMsg = "Code analysis api's sync batch size exceeded. Max Batch size: " + MAX_BATCH_SIZE + " Batch size: " + codeAnalysisApisList.size();
            loggerMaker.errorAndAddToDb(errorMsg, LogDb.DASHBOARD);
            addActionError(errorMsg);
            return ERROR.toUpperCase();
        }

        // populate code analysis api map
        Map<String, CodeAnalysisApi> codeAnalysisApisMap = new HashMap<>();
        for (CodeAnalysisApi codeAnalysisApi: codeAnalysisApisList) {
            codeAnalysisApisMap.put(codeAnalysisApi.generateCodeAnalysisApisMapKey(), codeAnalysisApi);
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(apiCollectionName);
        if (apiCollection == null) {
            apiCollection = new ApiCollection(Context.now(), apiCollectionName, Context.now(), new HashSet<>(), null, 0, false, false);
            ApiCollectionsDao.instance.insertOne(apiCollection);
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
        List<BasicDBObject> trafficApis = new ArrayList<>();
        if (apiCollection.getHostName() != null && !apiCollection.getHostName().isEmpty()) {
            // If the api collection has a host name, fetch traffic endpoints using the host name
            trafficApis = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0, false);
        } else {
            // If the api collection does not have a host name, fetch traffic endpoints without host name
            trafficApis = ApiCollectionsDao.fetchEndpointsInCollection(apiCollection.getId(), 0, -1, 60 * 24 * 60 * 60);
        }
        Map<String, String> trafficApiEndpointAktoTemplateStrToOriginalMap = new HashMap<>();
        List<String> trafficApiKeys = new ArrayList<>();
        for (BasicDBObject trafficApi: trafficApis) {
            BasicDBObject trafficApiApiInfoKey = (BasicDBObject) trafficApi.get("_id");
            String trafficApiMethod = trafficApiApiInfoKey.getString("method");
            String trafficApiUrl = trafficApiApiInfoKey.getString("url");
            String trafficApiEndpoint = "";

            // extract path name from url
            try {
                // Directly parse the trafficApiUrl as a URI
                URI uri = new URI(trafficApiUrl);
                trafficApiEndpoint = uri.getPath();

                // Decode any percent-encoded characters in the path
                trafficApiEndpoint = java.net.URLDecoder.decode(trafficApiEndpoint, "UTF-8");

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error parsing URI: " + trafficApiUrl, LogDb.DASHBOARD);
                continue;
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
                        codeAnalysisApi.getLocation(), codeAnalysisApi.getRequestBody(), codeAnalysisApi.getResponseBody());

                tempCodeAnalysisApisMap.remove(codeAnalysisApiKey);
                tempCodeAnalysisApisMap.put(newCodeAnalysisApi.generateCodeAnalysisApisMapKey(), newCodeAnalysisApi);
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
                        continue;
                    }

                    if (codeAnalysisApiEndpoint.equals(trafficApiEndpoint)) {
                        CodeAnalysisApi newCodeAnalysisApi = new CodeAnalysisApi(
                                trafficApiMethod,
                                trafficApiEndpoint,
                                codeAnalysisApi.getLocation(), codeAnalysisApi.getRequestBody(), codeAnalysisApi.getResponseBody());

                        tempCodeAnalysisApisMap.put(newCodeAnalysisApi.generateCodeAnalysisApisMapKey(), newCodeAnalysisApi);
                        break;
                    }
                }
            }
        }

        codeAnalysisApisMap = tempCodeAnalysisApisMap;

        ObjectId codeAnalysisCollectionId = null;
        try {
            // ObjectId for new code analysis collection
            codeAnalysisCollectionId = new ObjectId();

            String projectDir = projectName + "/" + repoName;  //todo:

            CodeAnalysisCollection codeAnalysisCollection = CodeAnalysisCollectionDao.instance.updateOne(
                    Filters.eq("codeAnalysisCollectionName", apiCollectionName),
                    Updates.combine(
                            Updates.setOnInsert(CodeAnalysisCollection.ID, codeAnalysisCollectionId),
                            Updates.setOnInsert(CodeAnalysisCollection.NAME, apiCollectionName),
                            Updates.set(CodeAnalysisCollection.PROJECT_DIR, projectDir),
                            Updates.setOnInsert(CodeAnalysisCollection.API_COLLECTION_ID, apiCollection.getId())
                    )
            );

            // Set code analysis collection id if existing collection is updated
            if (codeAnalysisCollection != null) {
                codeAnalysisCollectionId = codeAnalysisCollection.getId();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating code analysis collection: " + apiCollectionName + " Error: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error syncing code analysis collection: " + apiCollectionName);
            return ERROR.toUpperCase();
        }

        int now = Context.now();

        if (codeAnalysisCollectionId != null) {
            List<WriteModel<CodeAnalysisApiInfo>> bulkUpdates = new ArrayList<>();
            List<WriteModel<SingleTypeInfo>> bulkUpdatesSTI = new ArrayList<>();

            for(Map.Entry<String, CodeAnalysisApi> codeAnalysisApiEntry: codeAnalysisApisMap.entrySet()) {
                CodeAnalysisApi codeAnalysisApi = codeAnalysisApiEntry.getValue();
                CodeAnalysisApiInfo.CodeAnalysisApiInfoKey codeAnalysisApiInfoKey = new CodeAnalysisApiInfo.CodeAnalysisApiInfoKey(codeAnalysisCollectionId, codeAnalysisApi.getMethod(), codeAnalysisApi.getEndpoint());

                bulkUpdates.add(
                        new UpdateOneModel<>(
                                Filters.eq(CodeAnalysisApiInfo.ID, codeAnalysisApiInfoKey),
                                Updates.combine(
                                        Updates.setOnInsert(CodeAnalysisApiInfo.ID, codeAnalysisApiInfoKey),
                                        Updates.set(CodeAnalysisApiInfo.LOCATION, codeAnalysisApi.getLocation()),
                                        Updates.setOnInsert(CodeAnalysisApiInfo.DISCOVERED_TS, now),
                                        Updates.set(CodeAnalysisApiInfo.LAST_SEEN_TS, now)
                                ),
                                new UpdateOptions().upsert(true)
                        )
                );

                String requestBody = codeAnalysisApi.getRequestBody();
                String responseBody = codeAnalysisApi.getResponseBody();

                List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
                singleTypeInfos.addAll(generateSTIsFromPayload(apiCollection.getId(), codeAnalysisApi.getEndpoint(), codeAnalysisApi.getMethod(), requestBody, -1));
                singleTypeInfos.addAll(generateSTIsFromPayload(apiCollection.getId(), codeAnalysisApi.getEndpoint(), codeAnalysisApi.getMethod(), responseBody, 200));

                Bson update = Updates.combine(Updates.max(SingleTypeInfo.LAST_SEEN, now), Updates.setOnInsert("timestamp", now));

                for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                    bulkUpdatesSTI.add(
                            new UpdateOneModel<>(
                                    SingleTypeInfoDao.createFilters(singleTypeInfo),
                                    update,
                                    new UpdateOptions().upsert(true)
                            )
                    );
                }

            }

            if (!bulkUpdatesSTI.isEmpty()) {
                CodeAnalysisSingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesSTI);
            }

            if (bulkUpdates.size() > 0) {
                try {
                    CodeAnalysisApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error updating code analysis api infos: " + apiCollectionName + " Error: " + e.getMessage(), LogDb.DASHBOARD);
                    addActionError("Error syncing code analysis collection: " + apiCollectionName);
                    return ERROR.toUpperCase();
                }
            }
        }

        loggerMaker.infoAndAddToDb("Updated code analysis collection: " + apiCollectionName, LogDb.DASHBOARD);
        loggerMaker.infoAndAddToDb("Source code endpoints count: " + codeAnalysisApisMap.size(), LogDb.DASHBOARD);

        if (isLastBatch) {//Remove scheduled state from codeAnalysisRepo
            Bson sourceCodeFilter;
            if (this.codeAnalysisRepo.getSourceCodeType() == CodeAnalysisRepo.SourceCodeType.BITBUCKET) {
                sourceCodeFilter = Filters.or(
                        Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, this.codeAnalysisRepo.getSourceCodeType()),
                        Filters.exists(CodeAnalysisRepo.SOURCE_CODE_TYPE, false)

                );
            } else {
                sourceCodeFilter = Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, this.codeAnalysisRepo.getSourceCodeType());
            }

            Bson filters = Filters.and(
                    Filters.eq(CodeAnalysisRepo.REPO_NAME, this.codeAnalysisRepo.getRepoName()),
                    Filters.eq(CodeAnalysisRepo.PROJECT_NAME, this.codeAnalysisRepo.getProjectName()),
                    sourceCodeFilter
            );

            CodeAnalysisRepoDao.instance.updateOneNoUpsert(filters, Updates.set(CodeAnalysisRepo.LAST_RUN, Context.now()));
            loggerMaker.infoAndAddToDb("Updated last run for project:" + codeAnalysisRepo.getProjectName() + " repo:" + codeAnalysisRepo.getRepoName(), LogDb.DASHBOARD);
        }

        return SUCCESS.toUpperCase();
    }

    public List<CodeAnalysisApi> getCodeAnalysisApisList() {
        return codeAnalysisApisList;
    }

    public void setCodeAnalysisApisList(List<CodeAnalysisApi> codeAnalysisApisList) {
        this.codeAnalysisApisList = codeAnalysisApisList;
    }


    List<CodeAnalysisRepo> reposToRun = new ArrayList<>();
    public String updateRepoLastRun() {
        Bson sourceCodeFilter;
        if (codeAnalysisRepo == null) {
            loggerMaker.errorAndAddToDb("Code analysis repo is null", LogDb.DASHBOARD);
            addActionError("Code analysis repo is null");
            return ERROR.toUpperCase();
        }

        if (this.codeAnalysisRepo.getSourceCodeType() == CodeAnalysisRepo.SourceCodeType.BITBUCKET) {
            sourceCodeFilter = Filters.or(
                    Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, this.codeAnalysisRepo.getSourceCodeType()),
                    Filters.exists(CodeAnalysisRepo.SOURCE_CODE_TYPE, false)

            );
        } else {
            sourceCodeFilter = Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, this.codeAnalysisRepo.getSourceCodeType());
        }

        Bson filters = Filters.and(
                Filters.eq(CodeAnalysisRepo.REPO_NAME, this.codeAnalysisRepo.getRepoName()),
                Filters.eq(CodeAnalysisRepo.PROJECT_NAME, this.codeAnalysisRepo.getProjectName()),
                sourceCodeFilter
        );

        CodeAnalysisRepoDao.instance.updateOneNoUpsert(filters, Updates.set(CodeAnalysisRepo.LAST_RUN, Context.now()));
        loggerMaker.infoAndAddToDb("Updated last run for project:" + codeAnalysisRepo.getProjectName() + " repo:" + codeAnalysisRepo.getRepoName(), LogDb.DASHBOARD);
        return SUCCESS.toUpperCase();
    }
    public String findReposToRun() {
        reposToRun = CodeAnalysisRepoDao.instance.findAll(
                Filters.expr(
                        Document.parse("{ $gt: [ \"$" + CodeAnalysisRepo.SCHEDULE_TIME + "\", \"$" + CodeAnalysisRepo.LAST_RUN + "\" ] }")
                )
        );
        return SUCCESS.toUpperCase();
    }

    public List<CodeAnalysisRepo> getReposToRun() {
        return reposToRun;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public boolean isLastBatch() {
        return isLastBatch;
    }

    public void setLastBatch(boolean lastBatch) {
        isLastBatch = lastBatch;
    }

    public CodeAnalysisRepo getCodeAnalysisRepo() {
        return codeAnalysisRepo;
    }

    public void setCodeAnalysisRepo(CodeAnalysisRepo codeAnalysisRepo) {
        this.codeAnalysisRepo = codeAnalysisRepo;
    }
}
