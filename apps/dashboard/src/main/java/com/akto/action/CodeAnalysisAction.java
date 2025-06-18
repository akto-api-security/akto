package com.akto.action;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.*;
import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import org.bson.conversions.Bson;
import org.bson.types.Code;
import org.bson.types.ObjectId;
import org.checkerframework.checker.units.qual.s;
import org.json.JSONObject;

import com.akto.action.observe.Utils;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.RBAC.Role;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.util.DashboardMode;
import com.akto.util.EmailAccountName;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.util.HttpRequestResponseUtils.generateSTIsFromPayload;

public class CodeAnalysisAction extends UserAction {

    private String projectDir;
    private String apiCollectionName;
    private List<CodeAnalysisApi> codeAnalysisApisList;
    private CodeAnalysisRepo.SourceCodeType sourceCodeType;
    public static final int MAX_BATCH_SIZE = 100;

    private static final LoggerMaker loggerMaker = new LoggerMaker(CodeAnalysisAction.class, LogDb.DASHBOARD);;
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public void sendMixpanelEvent() {
        try {
            int accountId = Context.accountId.get();
            DashboardMode dashboardMode = DashboardMode.getDashboardMode();        
            RBAC record = RBACDao.instance.findOne(RBAC.ACCOUNT_ID, accountId, RBAC.ROLE, Role.ADMIN.name());
            if (record == null) {
                return;
            }
            BasicDBObject mentionedUser = UsersDao.instance.getUserInfo(record.getUserId());
            String userEmail = (String) mentionedUser.get("name");
            String distinct_id = userEmail + "_" + dashboardMode;
            EmailAccountName emailAccountName = new EmailAccountName(userEmail);
            String accountName = emailAccountName.getAccountName();

            JSONObject props = new JSONObject();
            props.put("Email ID", userEmail);
            props.put("Dashboard Mode", dashboardMode);
            props.put("Account Name", accountName);

            int codeAnalysisApiCount = 0;
            Set<String> fileExtensions = new HashSet<>();
            if (codeAnalysisApisList != null) {
                codeAnalysisApiCount = codeAnalysisApisList.size();

                for (CodeAnalysisApi codeAnalysisApi: codeAnalysisApisList) {
                    CodeAnalysisApiLocation location = codeAnalysisApi.getLocation();
                    if (location != null) {
                        String fileName = location.getFileName();
                        String[] fileNameParts = fileName.split("\\.");
                        if (fileNameParts.length > 1) {
                            fileExtensions.add(fileNameParts[fileNameParts.length - 1]);
                        }
                    }
                }
            } 
            props.put("codeAnalysisApiCount", codeAnalysisApiCount);
            props.put("fileExtensions", fileExtensions);

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, "CODE_ANALYSIS_SYNC", props);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error sending CODE_ANALYSIS_SYNC mixpanel event: " + e.getMessage(), LogDb.DASHBOARD);
        }
    }
    
    public String syncExtractedAPIs() {
        loggerMaker.debugAndAddToDb("Syncing code analysis endpoints for collection: " + apiCollectionName, LogDb.DASHBOARD);

        if (codeAnalysisApisList == null) {
            loggerMaker.errorAndAddToDb("Code analysis api's list is null", LogDb.DASHBOARD);
            addActionError("Code analysis api's list is null");
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

        // todo:  If API collection does exist, create it
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

                Bson update = Updates.combine(Updates.max(SingleTypeInfo.LAST_SEEN, now),
                        Updates.setOnInsert("timestamp", now),
                        Updates.set(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollection.getId())));

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

        loggerMaker.debugAndAddToDb("Updated code analysis collection: " + apiCollectionName, LogDb.DASHBOARD);
        loggerMaker.debugAndAddToDb("Source code endpoints count: " + codeAnalysisApisMap.size(), LogDb.DASHBOARD);

        // Send mixpanel event
        int accountId = Context.accountId.get();
        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                sendMixpanelEvent();
            }
        }, 0, TimeUnit.SECONDS);
        

        return SUCCESS.toUpperCase();
    }

    public String addCodeAnalysisRepo() {
        if (codeAnalysisRepos == null || codeAnalysisRepos.isEmpty()) {
            addActionError("Can't add empty repo");
            return ERROR.toUpperCase();
        }
        List<WriteModel<CodeAnalysisRepo>> updates = new ArrayList<>();
        for (CodeAnalysisRepo c: codeAnalysisRepos) {
            updates.add(new UpdateOneModel<>(
                Filters.and(
                        Filters.eq(CodeAnalysisRepo.REPO_NAME, c.getRepoName()),
                        Filters.eq(CodeAnalysisRepo.PROJECT_NAME, c.getProjectName()),
                        Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, c.getSourceCodeType())
                ),
                Updates.combine(
                        Updates.setOnInsert(CodeAnalysisRepo.LAST_RUN, 0),
                        Updates.setOnInsert(CodeAnalysisRepo.SCHEDULE_TIME, Context.now())
                ),
                new UpdateOptions().upsert(true)
            ));
        }

        CodeAnalysisRepoDao.instance.getMCollection().bulkWrite(updates);
        return SUCCESS.toUpperCase();
    }

    public String runCodeAnalysisRepo() {
        if (codeAnalysisRepos == null || codeAnalysisRepos.isEmpty()) {
            addActionError("Can't run empty repo");
            return ERROR.toUpperCase();
        }
        List<WriteModel<CodeAnalysisRepo>> updates = new ArrayList<>();
        for (CodeAnalysisRepo c: codeAnalysisRepos) {
            updates.add(new UpdateOneModel<>(
                    Filters.and(
                            Filters.eq(CodeAnalysisRepo.REPO_NAME, c.getRepoName()),
                            Filters.eq(CodeAnalysisRepo.PROJECT_NAME, c.getProjectName())
                    ),
                    Updates.set(CodeAnalysisRepo.SCHEDULE_TIME, Context.now()),
                    new UpdateOptions().upsert(false)
            ));
        }

        CodeAnalysisRepoDao.instance.getMCollection().bulkWrite(updates);
        return SUCCESS.toUpperCase();
    }

    CodeAnalysisRepo codeAnalysisRepo;
    public String deleteCodeAnalysisRepo() {
        if (codeAnalysisRepo == null) {
            addActionError("Can't delete null repo");
            return ERROR.toUpperCase();
        }
        CodeAnalysisRepoDao.instance.deleteAll(
                Filters.and(
                        Filters.eq(CodeAnalysisRepo.REPO_NAME, codeAnalysisRepo.getRepoName()),
                        Filters.eq(CodeAnalysisRepo.PROJECT_NAME, codeAnalysisRepo.getProjectName())
                )
        );
        return SUCCESS.toUpperCase();
    }

    List<CodeAnalysisRepo> codeAnalysisRepos;
    public String fetchCodeAnalysisRepos() {
        if (sourceCodeType == null) {
            sourceCodeType = CodeAnalysisRepo.SourceCodeType.BITBUCKET;
        }
        Bson filters;
        if (sourceCodeType == CodeAnalysisRepo.SourceCodeType.BITBUCKET) {
            filters = Filters.or(
                    Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, sourceCodeType),
                    Filters.exists(CodeAnalysisRepo.SOURCE_CODE_TYPE, false)

            );
        } else {
            filters = Filters.eq(CodeAnalysisRepo.SOURCE_CODE_TYPE, sourceCodeType);
        }
        codeAnalysisRepos = CodeAnalysisRepoDao.instance.findAll(filters);
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

    public List<CodeAnalysisApi> getCodeAnalysisApisList() {
        return codeAnalysisApisList;
    }

    public void setCodeAnalysisApisList(List<CodeAnalysisApi> codeAnalysisApisList) {
        this.codeAnalysisApisList = codeAnalysisApisList;
    }

    public void setCodeAnalysisRepo(CodeAnalysisRepo codeAnalysisRepo) {
        this.codeAnalysisRepo = codeAnalysisRepo;
    }

    public List<CodeAnalysisRepo> getCodeAnalysisRepos() {
        return codeAnalysisRepos;
    }

    public void setCodeAnalysisRepos(List<CodeAnalysisRepo> codeAnalysisRepos) {
        this.codeAnalysisRepos = codeAnalysisRepos;
    }

    public CodeAnalysisRepo.SourceCodeType getSourceCodeType() {
        return sourceCodeType;
    }

    public void setSourceCodeType(CodeAnalysisRepo.SourceCodeType sourceCodeType) {
        this.sourceCodeType = sourceCodeType;
    }
}
