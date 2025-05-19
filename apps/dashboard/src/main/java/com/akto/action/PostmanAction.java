package com.akto.action;

import com.akto.ApiRequest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dao.loaders.LoadersDao;
import com.akto.dao.upload.FileUploadLogsDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dto.*;
import com.akto.dto.files.File;
import com.akto.dto.loaders.PostmanUploadLoader;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.upload.FileUpload;
import com.akto.dto.upload.FileUploadError;
import com.akto.dto.upload.PostmanWorkspaceUpload;
import com.akto.dto.upload.PostmanUploadLog;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.postman.Main;
import com.akto.util.DashboardMode;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.ApiInfoKeyToSampleData;
import com.akto.utils.GzipUtils;
import com.akto.utils.SampleDataToSTI;
import com.akto.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import io.swagger.v3.oas.models.OpenAPI;
import okhttp3.OkHttpClient;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PostmanAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(PostmanAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();

    static{
        loggerMaker.debugAndAddToDb("Initializing http client for postman operations");
        ApiRequest.initCommonHttpClient(client);
    }

    @Override
    public String execute() {
        return SUCCESS;
    }

    private String api_key;
    private String workspace_id;
    public String addOrUpdateApiKey() {
        if (api_key == null || workspace_id == null) {
            return ERROR.toUpperCase();
        }
        User user = getSUser();
        PostmanCredential credential = new PostmanCredential(getSUser().getId()+"", workspace_id, api_key);
        ThirdPartyAccess thirdPartyAccess = new ThirdPartyAccess(
                Context.now(), user.getId(), 0, credential
        );

        ReplaceOptions replaceOptions= new ReplaceOptions();
        replaceOptions.upsert(true);
        ThirdPartyAccessDao.instance.getMCollection().replaceOne(
                Filters.and(
                        Filters.eq("owner", user.getId()),
                        Filters.eq("credential.type", Credential.Type.POSTMAN)
                ),
                thirdPartyAccess,
                replaceOptions
        );
        return SUCCESS.toUpperCase();
    }


    public void setApi_key(String api_key) {
        this.api_key = api_key;
    }

    public void setWorkspace_id(String workspace_id) {
        this.workspace_id = workspace_id;
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor();


    private int apiCollectionId;
    private List<ApiInfo.ApiInfoKey> apiInfoKeyList;
    public String createPostmanApi() throws Exception { // TODO: remove exception
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential == null) {
            addActionError("Please add postman credentials in settings");
            return ERROR.toUpperCase();
        }
        int accountId = Context.accountId.get();

        Runnable r = () -> {
            loggerMaker.debugAndAddToDb("Starting thread to create postman api", LogDb.DASHBOARD);
            Context.accountId.set(accountId);
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
            if (apiCollection == null) {
                return;
            }

            List<SampleData> sampleData;
            if(apiInfoKeyList != null && !apiInfoKeyList.isEmpty()) {
                sampleData = ApiInfoKeyToSampleData.convertApiInfoKeyToSampleData(apiInfoKeyList);
            } else {
                sampleData = SampleDataDao.instance.findAll(
                        Filters.in("collectionIds", apiCollectionId)
                );
            }


            String apiName = "AKTO " + apiCollection.getDisplayName();
            String host =  apiCollection.getHostName();
            String apiCollectionName = apiCollection.getDisplayName();

            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleData);
            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = null;
            try {
                openAPI = com.akto.open_api.Main.init(apiCollectionName, stiList, true, host);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error while creating open api: " + e.getMessage(), LogDb.DASHBOARD);
                return;
            }
            String openAPIStringAll = null;
            try {
                openAPIStringAll = com.akto.open_api.Main.convertOpenApiToJSON(openAPI);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error while converting open api to json: " + e.getMessage(), LogDb.DASHBOARD);
                return;
            }

            Main main = new Main(postmanCredential.getApiKey());
            try {
                main.createApiWithSchema(postmanCredential.getWorkspaceId(), apiName, openAPIStringAll);
            } catch (Exception e){
                loggerMaker.errorAndAddToDb(e,"Error while creating api in postman: " + e.getMessage(), LogDb.DASHBOARD);
            }
            loggerMaker.debugAndAddToDb("Successfully created api in postman", LogDb.DASHBOARD);
        };

        executorService.submit(r);

        return SUCCESS.toUpperCase();
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    private String postmanCollectionId;
    public String savePostmanCollection() {
        int userId = getSUser().getId();
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential == null) {
            addActionError("Please add postman credentials in settings");
            return ERROR.toUpperCase();
        }


        Main main = new Main(postmanCredential.getApiKey());
        JsonNode postmanCollection = main.fetchPostmanCollectionString(postmanCollectionId);
        // TODO: error handling
        String collectionName = postmanCollection.get("collection").get("info").get("name").asText();
        String postmanCollectionString = postmanCollection.get("collection").toString();

        String node_url = System.getenv("AKTO_NODE_URL");
        if (node_url == null) {
            // TODO:
        }
        String url =  node_url+ "/api/postman/endpoints";

        JSONObject requestBody = new JSONObject();
        requestBody.put("postman_string", postmanCollectionString);

        String json = requestBody.toString();
        JsonNode node = ApiRequest.postRequest(new HashMap<>(), url,json);
        JsonNode valueNode = node.get("open_api");
        String open_api_from_postman = valueNode.textValue();

        APISpec apiSpec = new APISpec(APISpec.Type.JSON, userId,collectionName,open_api_from_postman, apiCollectionId);
        APISpecDao.instance.replaceOne(Filters.eq("apiCollectionId", apiCollectionId), apiSpec);

        return SUCCESS.toUpperCase();
    }

    private final BasicDBObject postmanCred = new BasicDBObject();
    public String fetchPostmanCred() {
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential != null) {
            postmanCred.put("api_key",postmanCredential.getApiKey());
            postmanCred.put("workspace_id",postmanCredential.getWorkspaceId());
        }
        return SUCCESS.toUpperCase();
    }


    private PostmanCredential fetchPostmanCredential() {
        User u = getSUser();
        int userId = u.getId();
        return Utils.fetchPostmanCredential(userId);
    }

    private List<BasicDBObject> workspaces;
    public String fetchWorkspaces() {
        workspaces = new ArrayList<>();
        if (api_key == null || api_key.isEmpty()) {
            return SUCCESS.toUpperCase();
        }

        Main main = new Main(api_key);
        JsonNode postmanCollection = main.fetchWorkspaces();
        
        if (postmanCollection == null) return SUCCESS.toUpperCase();
        Iterator<JsonNode> a = postmanCollection.elements();
        while (a.hasNext()) {
            JsonNode node = a.next();
            BasicDBObject workspace = new BasicDBObject();
            workspace.put("id", node.get("id").asText());
            workspace.put("name", node.get("name").asText());
            workspace.put("type", node.get("type").asText());
            workspaces.add(workspace);
        }
        return SUCCESS.toUpperCase();
    }

    List<BasicDBObject> collections = new ArrayList<>();
    public String fetchCollections() {
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential == null) {
            addActionError("Please add postman credentials in settings");
            return ERROR.toUpperCase();
        }

        Main main = new Main(postmanCredential.getApiKey());
        JsonNode postmanCollectionsNode = main.fetchApiCollections();

        Iterator<JsonNode> a = postmanCollectionsNode.elements();
        while (a.hasNext()) {
            JsonNode node = a.next();
            BasicDBObject collection = new BasicDBObject();
            collection.put("uid", node.get("uid").asText());
            collection.put("name", node.get("name").asText());
            collections.add(collection);
        }

        return SUCCESS.toUpperCase();
    }

    private final boolean skipKafka = DashboardMode.isLocalDeployment() || DashboardMode.isKubernetes();

    private boolean allowReplay;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String importDataFromPostman() throws Exception {
        int accountId = Context.accountId.get();
        if (api_key == null || api_key.length() == 0) {
            addActionError("Invalid postman key");
            return ERROR.toUpperCase();
        }

        if (workspace_id == null || workspace_id.length() == 0) {
            addActionError("Invalid workspace id");
            return ERROR.toUpperCase();
        }

        try {
            String result = addOrUpdateApiKey();
            if ( result == null || !result.equals(SUCCESS.toUpperCase())) throw new Exception("Returned Error");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error while adding/updating postman key+ " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error while adding/updating postman key.");
            return ERROR.toUpperCase();
        }

        PostmanCredential postmanCredential = fetchPostmanCredential();

        loggerMaker.debugAndAddToDb("Fetched postman creds", LogDb.DASHBOARD);

        PostmanWorkspaceUpload upload = new PostmanWorkspaceUpload(FileUpload.UploadType.POSTMAN_WORKSPACE, FileUpload.UploadStatus.IN_PROGRESS);
        upload.setPostmanWorkspaceId(workspace_id);
        ObjectId uploadId = FileUploadsDao.instance.insertOne(upload).getInsertedId().asObjectId().getValue();
        this.uploadId = uploadId.toString();


        executorService.schedule( new Runnable() {
            public void run() {
                try {
                    loggerMaker.debugAndAddToDb("Starting postman thread", LogDb.DASHBOARD);
                    Context.accountId.set(accountId);
                    importDataFromPostmanMain(workspace_id, postmanCredential.getApiKey(), allowReplay, uploadId);
                } catch (Exception e){
                    loggerMaker.errorAndAddToDb(e,"Error while importing data from postman: " + e.getMessage(), LogDb.DASHBOARD);
                    FileUploadsDao.instance.updateOne(Filters.eq("_id", uploadId), new BasicDBObject("$set", new BasicDBObject("uploadStatus", FileUpload.UploadStatus.FAILED)));
                }
            }
        }, 0, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private ObjectId createPostmanLoader() {
        int userId = getSUser().getId();
        PostmanUploadLoader postmanUploadLoader = new PostmanUploadLoader(
                userId, 0,0, true
        );
        LoadersDao.instance.createNormalLoader(postmanUploadLoader);
        return postmanUploadLoader.getId();
    }

    private void importDataFromPostmanMain(String workspaceId, String apiKey, boolean allowReplay, ObjectId uploadId) {
        PostmanWorkspaceUpload upload = FileUploadsDao.instance.getPostmanMCollection().find(Filters.eq("_id", uploadId)).first();
        if(upload == null){
            loggerMaker.debugAndAddToDb("No upload found with id: " + uploadId, LogDb.DASHBOARD);
            return;
        }
        Main main = new Main(apiKey);
        loggerMaker.debugAndAddToDb("Fetching details for workspace_id:" + workspaceId, LogDb.DASHBOARD);
        JsonNode workspaceDetails = main.fetchWorkspace(workspaceId);
        if(workspaceDetails == null || workspaceDetails.get("workspace") == null){
            loggerMaker.debugAndAddToDb("No workspace found", LogDb.DASHBOARD);
            upload.setFatalError("No workspace found");
            upload.setUploadStatus(FileUpload.UploadStatus.FAILED);
            FileUploadsDao.instance.replaceOne(Filters.eq("_id", uploadId), upload);
            return;
        }
        JsonNode workspaceObj = workspaceDetails.get("workspace");
        ArrayNode collectionsObj = (ArrayNode) workspaceObj.get("collections");
        if(collectionsObj == null){
            loggerMaker.debugAndAddToDb("No collections found in workspace", LogDb.DASHBOARD);
            upload.setFatalError("No collections found in workspace");
            upload.setUploadStatus(FileUpload.UploadStatus.FAILED);
            FileUploadsDao.instance.replaceOne(Filters.eq("_id", uploadId), upload);
            return;
        }

        int totalApis = 0;
        Map<String, JsonNode> collectionDetailsToIdMap = new HashMap<>();
        Map<String, Integer> countMap = new HashMap<>();
        for(JsonNode collectionObj: collectionsObj) {
            String collectionId = collectionObj.get("id").asText();
            JsonNode collectionDetails;
            try {
                collectionDetails = main.fetchCollection(collectionId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error getting data from postman for collection " + collectionId + " : " + e.getMessage(), LogDb.DASHBOARD);
                continue;
            }

            loggerMaker.debugAndAddToDb("Successfully fetched postman collection: " + collectionId, LogDb.DASHBOARD);

            JsonNode collectionDetailsObj = collectionDetails.get("collection");
            String collectionName = collectionDetailsObj.get("info").get("name").asText();

            String fileContent = collectionDetailsObj.toString();
            File file = new File(FileUpload.UploadType.POSTMAN_WORKSPACE.toString(), GzipUtils.zipString(fileContent));
            InsertOneResult insertOneResult = FilesDao.instance.insertOne(file);
            ObjectId fileId = insertOneResult.getInsertedId().asObjectId().getValue();
            upload.addCollection(collectionId, collectionName, fileId.toString());

            int count = apiCount(collectionDetailsObj);

            loggerMaker.debugAndAddToDb("Api count for collection " + collectionId + ": " + count, LogDb.DASHBOARD);

            collectionDetailsToIdMap.put(collectionId, collectionDetailsObj);
            countMap.put(collectionId, count);

            totalApis +=  count;
        }
        upload.setCount(totalApis);
        FileUploadsDao.instance.replaceOne(Filters.eq("_id", uploadId), upload);

        for(JsonNode collectionObj: collectionsObj) {
            String collectionId = collectionObj.get("id").asText();
            int aktoCollectionId = collectionId.hashCode();
            aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;
            JsonNode collectionDetailsObj = collectionDetailsToIdMap.get(collectionId);

            String collectionName = collectionDetailsObj.get("info").get("name").asText();

            loggerMaker.debugAndAddToDb("Processing collection " + collectionName, LogDb.DASHBOARD);
            List<FileUploadError> collectionErrors = generateMessages(collectionDetailsObj, workspaceId, aktoCollectionId, collectionName, collectionId, allowReplay, upload);
            if(!collectionErrors.isEmpty()){
                upload.addError(collectionId, collectionErrors);
            }
            loggerMaker.debugAndAddToDb("Finished processing collection " + collectionName, LogDb.DASHBOARD);
        }
        upload.setUploadStatus(FileUpload.UploadStatus.SUCCEEDED);
        FileUploadsDao.instance.replaceOne(Filters.eq("_id", uploadId), upload);
    }

    String postmanCollectionFile;
    int postmanAktoCollectionId;
    public String importDataFromPostmanFile() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode collectionDetailsObj;
        String fileInsertId;
        try {
            collectionDetailsObj = mapper.readTree(postmanCollectionFile);
            String zipped = GzipUtils.zipString(postmanCollectionFile);
            File file = new File(FileUpload.UploadType.POSTMAN_FILE.toString(), zipped);
            InsertOneResult insertOneResult = FilesDao.instance.insertOne(file);
            fileInsertId = insertOneResult.getInsertedId().asObjectId().getValue().toString();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error parsing postman collection file: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error while parsing the file");
            return ERROR.toLowerCase();
        }

        JsonNode infoNode = collectionDetailsObj.get("info");
        String collectionId = infoNode.get("_postman_id").asText();
        String collectionName = infoNode.get("name").asText();

        int accountId = Context.accountId.get();

        PostmanWorkspaceUpload postmanWorkspaceUpload = new PostmanWorkspaceUpload(FileUpload.UploadType.POSTMAN_FILE, FileUpload.UploadStatus.IN_PROGRESS);
        postmanWorkspaceUpload.setPostmanCollectionIds(new HashMap<String, String>(){{
            put(collectionId, collectionName);
        }});
        postmanWorkspaceUpload.setCollectionIdToFileIdMap(new HashMap<String, String>(){{
            put(collectionId, fileInsertId);
        }});
        InsertOneResult insertOneResult = FileUploadsDao.instance.insertOne(postmanWorkspaceUpload);
        ObjectId uploadId = insertOneResult.getInsertedId().asObjectId().getValue();

        this.uploadId = uploadId.toString();
        executorService.schedule(new Runnable() {
            public void run() {
                loggerMaker.debugAndAddToDb("Starting thread to process postman file", LogDb.DASHBOARD);
                Context.accountId.set(accountId);
                try {
                    importDataFromPostmanFileMain(collectionDetailsObj, postmanAktoCollectionId, collectionId, collectionName, allowReplay, uploadId);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while importing data from postman file: " + e);
                    FileUploadsDao.instance.updateOne(Filters.eq("_id", uploadId), new BasicDBObject("$set", new BasicDBObject("uploadStatus", FileUpload.UploadStatus.FAILED)));
                }
            }
        }, 1, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }
    private List<PostmanUploadLog> postmanUploadLogs;
    private FileUpload.UploadStatus uploadStatus;
    private String miniTestingName;

    private BasicDBObject uploadDetails;
    public String fetchImportLogs(){
        if(this.uploadId == null){
            addActionError("Upload id is required");
            return ERROR.toUpperCase();
        }
        PostmanWorkspaceUpload id = FileUploadsDao.instance.getPostmanMCollection().find(Filters.eq("_id", new ObjectId(uploadId))).first();
        if(id == null){
            addActionError("Invalid upload id");
            return ERROR.toUpperCase();
        }
        postmanUploadLogs = new ArrayList<>();
        uploadStatus = id.getUploadStatus();
        if(id.getUploadStatus() == FileUpload.UploadStatus.SUCCEEDED){
            postmanUploadLogs = FileUploadLogsDao.instance.getPostmanMCollection().find(Filters.eq("uploadId", uploadId)).into(new ArrayList<>());
        }
        BasicDBList list = new BasicDBList();
        if(id.getCollectionErrors() != null && !id.getCollectionErrors().isEmpty()){
            Map<String, String> map = new HashMap<>();
            map.put("term", "Collection name");
            map.put("description", "Errors");
            list.add(map);
            for (Map.Entry<String, List<FileUploadError>> entryMap : id.getCollectionErrors().entrySet()) {
                String collectionId = entryMap.getKey();
                List<FileUploadError> errors = entryMap.getValue();
                map = new HashMap<>();
                map.put("term", id.getPostmanCollectionIds().get(collectionId));
                map.put("description", String.join(",", errors.stream().map(FileUploadError::getError).collect(Collectors.toList())));
                list.add(new BasicDBObject(map));
            }
        }

        BasicDBList modifiedLogs = new BasicDBList();
        int correctlyParsedApis = 0;
        int apisWithErrorsAndParsed = 0;
        int apisWithErrorsAndCannotBeImported = 0;
        if(postmanUploadLogs != null && !postmanUploadLogs.isEmpty()){
            Map<String, String> map = new HashMap<>();
            map.put("term", "Url");
            map.put("description", "Errors");
            modifiedLogs.add(map);
            for (PostmanUploadLog log : postmanUploadLogs) {
                if(log.getErrors() == null || log.getErrors().isEmpty()){
                    correctlyParsedApis++;
                } else {
                    List<String> errors = log.getErrors().stream().map(FileUploadError::getError).collect(Collectors.toList());
                    modifiedLogs.add(new BasicDBObject("term", log.getUrl()).append("description", String.join(",", errors)));
                    if(log.getAktoFormat() == null){
                        apisWithErrorsAndCannotBeImported++;
                    } else {
                        apisWithErrorsAndParsed++;
                    }
                }

            }
        }
        uploadDetails = new BasicDBObject();
        uploadDetails.put("uploadStatus", uploadStatus);
        uploadDetails.put("uploadId", uploadId);
        uploadDetails.put("collectionErrors", list);
        uploadDetails.put("logs", modifiedLogs);
        uploadDetails.put("errorCount", modifiedLogs.size());
        uploadDetails.put("totalCount", id.getCount());
        uploadDetails.put("correctlyParsedApis", correctlyParsedApis);
        uploadDetails.put("apisWithErrorsAndParsed", apisWithErrorsAndParsed);
        uploadDetails.put("apisWithErrorsAndCannotBeImported", apisWithErrorsAndCannotBeImported);
        return SUCCESS.toUpperCase();
    }

    public BasicDBObject getUploadDetails() {
        return uploadDetails;
    }

    public void setUploadDetails(BasicDBObject uploadDetails) {
        this.uploadDetails = uploadDetails;
    }

    public String uploadId;
    public ImportType importType;

    public String getMiniTestingName() {
        return miniTestingName;
    }

    public void setMiniTestingName(String miniTestingName) {
        this.miniTestingName = miniTestingName;
    }

    public enum ImportType{
        ONLY_SUCCESSFUL_APIS,
        ALL_APIS
    }
    public String importFile(){
        if(importType == null || uploadId == null){
            addActionError("Invalid import type or upload id");
            return ERROR.toUpperCase();
        }
        PostmanWorkspaceUpload postmanWorkspaceUpload = FileUploadsDao.instance.getPostmanMCollection().find(Filters.eq("_id", new ObjectId(uploadId))).first();
        if(postmanWorkspaceUpload == null){
            addActionError("Invalid upload id");
            return ERROR.toUpperCase();
        }
        List<PostmanUploadLog> uploads;

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("uploadId", uploadId));
        filters.add(Filters.exists("aktoFormat", true));

        if(importType == ImportType.ONLY_SUCCESSFUL_APIS){
            filters.add(Filters.exists("errors", false));
        }

        uploads = FileUploadLogsDao.instance.getPostmanMCollection().find(Filters.and(filters.toArray(new Bson[0]))).into(new ArrayList<>());
        int accountId = Context.accountId.get();

        new Thread(()-> {
            Context.accountId.set(accountId);
            loggerMaker.debugAndAddToDb(String.format("Starting thread to import %d postman apis, import type: %s", uploads.size(), importType), LogDb.DASHBOARD);
            String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
            Map<String, List<PostmanUploadLog>> collectionToUploadsMap = new HashMap<>();
            for(PostmanUploadLog upload: uploads){
                String collectionId = upload.getPostmanCollectionId();
                if(!collectionToUploadsMap.containsKey(collectionId)){
                    collectionToUploadsMap.put(collectionId, new ArrayList<>());
                }
                collectionToUploadsMap.get(collectionId).add(upload);
            }

            try {
                for (Map.Entry<String, List<PostmanUploadLog>> postmanCollectionToApis : collectionToUploadsMap.entrySet()) {

                    String collectionId = postmanCollectionToApis.getKey();
                    int aktoCollectionId = collectionId.hashCode();
                    aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;
                    List<String> msgs = new ArrayList<>();
                    loggerMaker.debugAndAddToDb(String.format("Processing postman collection %s, aktoCollectionId: %s", postmanCollectionToApis.getKey(), aktoCollectionId), LogDb.DASHBOARD);
                    for(PostmanUploadLog upload: postmanCollectionToApis.getValue()){
                        String aktoFormat = upload.getAktoFormat();
                        msgs.add(aktoFormat);
                    }
                    if(ApiCollectionsDao.instance.findOne(Filters.eq("_id", aktoCollectionId)) == null){
                        String collectionName = postmanWorkspaceUpload.getPostmanCollectionIds().getOrDefault(collectionId, "Postman " + collectionId);
                        loggerMaker.debugAndAddToDb(String.format("Creating manual collection for aktoCollectionId: %s and name: %s", aktoCollectionId, collectionName), LogDb.DASHBOARD);
                        ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(aktoCollectionId, collectionName));
                    }
                    loggerMaker.debugAndAddToDb(String.format("Pushing data in akto collection id %s", aktoCollectionId), LogDb.DASHBOARD);
                    Utils.pushDataToKafka(aktoCollectionId, topic, msgs, new ArrayList<>(), skipKafka, true, true);
                    loggerMaker.debugAndAddToDb(String.format("Pushed data in akto collection id %s", aktoCollectionId), LogDb.DASHBOARD);
                }
                FileUploadsDao.instance.getPostmanMCollection().updateOne(Filters.eq("_id", new ObjectId(uploadId)), new BasicDBObject("$set", new BasicDBObject("ingestionComplete", true).append("markedForDeletion", true)), new UpdateOptions().upsert(false));
                loggerMaker.debugAndAddToDb("Ingestion complete for " + postmanWorkspaceUpload.getId().toString(), LogDb.DASHBOARD);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error pushing data to kafka", LogDb.DASHBOARD);
            }
        }).start();


        return SUCCESS.toUpperCase();
    }

    private void importDataFromPostmanFileMain(JsonNode collectionDetailsObj, int aktoCollectionId, String collectionId, String collectionName, boolean allowReplay, ObjectId uploadId) {
        PostmanWorkspaceUpload postmanWorkspaceUpload = FileUploadsDao.instance.getPostmanMCollection().find(Filters.eq("_id", uploadId)).first();
        if(postmanWorkspaceUpload == null){
            loggerMaker.debugAndAddToDb("No upload found with id: " + uploadId, LogDb.DASHBOARD);
            return;
        }
        int count = apiCount(collectionDetailsObj);
        postmanWorkspaceUpload.setCount(count);
        loggerMaker.debugAndAddToDb("API count in postman.json: " + count, LogDb.DASHBOARD);

        List<FileUploadError> fileUploadErrors = generateMessages(collectionDetailsObj, null, aktoCollectionId, collectionName, collectionId, allowReplay, postmanWorkspaceUpload);

        if (!fileUploadErrors.isEmpty()) {
            postmanWorkspaceUpload.setCollectionErrors(new HashMap<String, List<FileUploadError>>() {{
                put(collectionId, fileUploadErrors);
            }});
        }
        postmanWorkspaceUpload.setUploadStatus(FileUpload.UploadStatus.SUCCEEDED);
        FileUploadsDao.instance.replaceOne(Filters.eq("_id", uploadId), postmanWorkspaceUpload);
    }

    public static int apiCount(JsonNode collectionDetailsObj) {
        ArrayList<JsonNode> jsonNodes = new ArrayList<>();
        Utils.fetchApisRecursively((ArrayNode) collectionDetailsObj.get("item"), jsonNodes);

        return jsonNodes.size();
    }

    private List<FileUploadError> generateMessages(JsonNode collectionDetailsObj, String workspaceId, int aktoCollectionId, String collectionName, String postmanCollectionId, boolean allowReplay, PostmanWorkspaceUpload fileUpload) {
        int accountId = Context.accountId.get();
        int noUrls = 0;
        List<FileUploadError> collectionErrors = new ArrayList<>();
        Map<String, String> variablesMap = Utils.getVariableMap((ArrayNode) collectionDetailsObj.get("variable"));
        Map<String, String> authMap = new HashMap<>();
        try {
            authMap = Utils.getAuthMap(collectionDetailsObj.get("auth"), variablesMap);
        } catch (Exception e) {
            collectionErrors.add(new FileUploadError("Error while getting auth map: " + e.getMessage(), FileUploadError.ErrorType.WARNING));
        }
        ArrayList<JsonNode> jsonNodes = new ArrayList<>();
        Utils.fetchApisRecursively((ArrayNode) collectionDetailsObj.get("item"), jsonNodes);
        if(jsonNodes.isEmpty()) {
            loggerMaker.debugAndAddToDb("Collection "+ collectionName + " has no requests, skipping it", LogDb.DASHBOARD);
            collectionErrors.add(new FileUploadError("Collection has no requests", FileUploadError.ErrorType.ERROR));
            return collectionErrors;
        }
        loggerMaker.debugAndAddToDb(String.format("Found %s apis in collection %s", jsonNodes.size(), collectionName), LogDb.DASHBOARD);
        for(JsonNode item: jsonNodes){
            PostmanUploadLog uploadLog = new PostmanUploadLog();
            uploadLog.setUploadId(fileUpload.getId().toString());
            uploadLog.setPostmanCollectionId(postmanCollectionId);
            if(workspaceId != null) {
                uploadLog.setPostmanWorkspaceId(workspaceId);
            }
            String apiName = item.get("name").asText();
            loggerMaker.debugAndAddToDb(String.format("Processing api %s in collection %s", apiName, collectionName), LogDb.DASHBOARD);

            JsonNode request = item.get("request");
            String path;
            try {
                path = Utils.getPath(request);
            } catch (Exception e){
                noUrls++;
                continue;
            }
            uploadLog.setUrl(path);
            Pair<Map<String, String>,List<FileUploadError>> result = Utils.convertApiInAktoFormat(item, variablesMap, String.valueOf(accountId), allowReplay, authMap, this.miniTestingName);
            List<FileUploadError> errors = result.getRight();
            if(result.getLeft() != null){
                Map<String, String> apiInAktoFormat = result.getLeft();
                apiInAktoFormat.put("akto_vxlan_id", String.valueOf(aktoCollectionId));
                try {
                    String s = mapper.writeValueAsString(apiInAktoFormat);
                    uploadLog.setAktoFormat(s);
                }catch (JsonProcessingException e){
                    loggerMaker.errorAndAddToDb(e,"Error while converting akto format to json: " + e.getMessage(), LogDb.DASHBOARD);
                    uploadLog.addError(new FileUploadError("Error while converting akto format to json: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
                }
            }
            if(!errors.isEmpty()){
                uploadLog.setErrors(errors);
            }
            FileUploadLogsDao.instance.insertOne(uploadLog);
        }

        if(noUrls > 0){
            collectionErrors.add(new FileUploadError("No urls found in " + noUrls + " apis", FileUploadError.ErrorType.WARNING));
        }

        return collectionErrors;
    }

    public String markImportForDeletion(){
        if(uploadId == null){
            addActionError("Upload id is required");
            return ERROR.toUpperCase();
        }
        FileUpload id = FileUploadsDao.instance.findOne(Filters.eq("_id", new ObjectId(uploadId)));
        if(id == null){
            addActionError("Invalid upload id");
            return ERROR.toUpperCase();
        }
        id.setMarkedForDeletion(true);
        FileUploadsDao.instance.updateOneNoUpsert(Filters.eq("_id", new ObjectId(uploadId)), Updates.set("markedForDeletion", true));
        return SUCCESS.toUpperCase();
    }


    public List<BasicDBObject> getCollections() {
        return collections;
    }

    public void setPostmanCollectionId(String postmanCollectionId) {
        this.postmanCollectionId = postmanCollectionId;
    }

    public List<BasicDBObject> getWorkspaces() {
        return workspaces;
    }

    public BasicDBObject getPostmanCred() {
        return postmanCred;
    }


    public void setAllowReplay(boolean allowReplay) {
        this.allowReplay = allowReplay;
    }


    public void setPostmanCollectionFile(String postmanCollectionFile) {
        this.postmanCollectionFile = postmanCollectionFile;
    }

    public int getPostmanAktoCollectionId() {
        return postmanAktoCollectionId;
    }

    public List<PostmanUploadLog> getPostmanUploadLogs() {
        return postmanUploadLogs;
    }

    public void setPostmanUploadLogs(List<PostmanUploadLog> postmanUploadLogs) {
        this.postmanUploadLogs = postmanUploadLogs;
    }

    public FileUpload.UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(FileUpload.UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public ImportType getImportType() {
        return importType;
    }

    public void setImportType(ImportType importType) {
        this.importType = importType;
    }

    public List<ApiInfo.ApiInfoKey> getApiInfoKeyList() {
        return apiInfoKeyList;
    }

    public void setApiInfoKeyList(List<ApiInfo.ApiInfoKey> apiInfoKeyList) {
        this.apiInfoKeyList = apiInfoKeyList;
    }
}
