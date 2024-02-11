package com.akto.action;

import com.akto.ApiRequest;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dao.loaders.LoadersDao;
import com.akto.dto.*;
import com.akto.dto.files.File;
import com.akto.dto.loaders.PostmanUploadLoader;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.postman.Main;
import com.akto.util.DashboardMode;
import com.akto.utils.GzipUtils;
import com.akto.utils.SampleDataToSTI;
import com.akto.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.swagger.v3.oas.models.OpenAPI;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PostmanAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(PostmanAction.class);
    private static final ObjectMapper mapper = new ObjectMapper();
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
    public String createPostmanApi() throws Exception { // TODO: remove exception
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential == null) {
            addActionError("Please add postman credentials in settings");
            return ERROR.toUpperCase();
        }
        int accountId = Context.accountId.get();

        Runnable r = () -> {
            loggerMaker.infoAndAddToDb("Starting thread to create postman api", LogDb.DASHBOARD);
            Context.accountId.set(accountId);
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
            if (apiCollection == null) {
                return;
            }
            String apiName = "AKTO " + apiCollection.getDisplayName();

            List<SampleData> sampleData = SampleDataDao.instance.findAll(
                    Filters.eq("_id.apiCollectionId", apiCollectionId)
            );
            String host =  apiCollection.getHostName();
            SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();
            sampleDataToSTI.setSampleDataToSTI(sampleData);
            Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
            OpenAPI openAPI = null;
            try {
                openAPI = com.akto.open_api.Main.init(apiCollection.getDisplayName(),stiList, true, host);
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
            loggerMaker.infoAndAddToDb("Successfully created api in postman", LogDb.DASHBOARD);
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

    private final boolean skipKafka = DashboardMode.isLocalDeployment();

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

        loggerMaker.infoAndAddToDb("Fetched postman creds", LogDb.DASHBOARD);

        ObjectId loaderId = createPostmanLoader();

        executorService.schedule( new Runnable() {
            public void run() {
                loggerMaker.infoAndAddToDb("Starting postman thread", LogDb.DASHBOARD);
                Context.accountId.set(accountId);
                importDataFromPostmanMain(workspace_id, postmanCredential.getApiKey(), skipKafka, allowReplay, loaderId);
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

    private static void importDataFromPostmanMain(String workspaceId, String apiKey, boolean skipKafka, boolean allowReplay, ObjectId loaderId) {
        Main main = new Main(apiKey);
        loggerMaker.infoAndAddToDb("Fetching details for workspace_id:" + workspaceId, LogDb.DASHBOARD);
        JsonNode workspaceDetails = main.fetchWorkspace(workspaceId);
        JsonNode workspaceObj = workspaceDetails.get("workspace");
        ArrayNode collectionsObj = (ArrayNode) workspaceObj.get("collections");
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");

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

            loggerMaker.infoAndAddToDb("Successfully fetched postman collection: " + collectionId, LogDb.DASHBOARD);

            JsonNode collectionDetailsObj = collectionDetails.get("collection");
            int count = apiCount(collectionDetailsObj);

            loggerMaker.infoAndAddToDb("Api count for collection " + collectionId + ": " + count, LogDb.DASHBOARD);

            collectionDetailsToIdMap.put(collectionId, collectionDetailsObj);
            countMap.put(collectionId, count);

            totalApis +=  count;
        }

        LoadersDao.instance.updateTotalCountNormalLoader(loaderId,totalApis);

        for(JsonNode collectionObj: collectionsObj) {
            String collectionId = collectionObj.get("id").asText();
            int aktoCollectionId = collectionId.hashCode();
            aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;
            JsonNode collectionDetailsObj = collectionDetailsToIdMap.get(collectionId);

            String collectionName = collectionDetailsObj.get("info").get("name").asText();

            List<String> msgs = new ArrayList<>();
            try {
                msgs = generateMessages(collectionDetailsObj, aktoCollectionId, collectionName, allowReplay);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"Error getting data from postman for collection " + collectionId + " : " + e.getMessage(), LogDb.DASHBOARD);
                LoadersDao.instance.updateIncrementalCount(loaderId, countMap.get(collectionId));
                continue;
            }

            if(msgs.size() > 0) {
                if(ApiCollectionsDao.instance.findOne(Filters.eq("_id", aktoCollectionId)) == null){
                    ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(aktoCollectionId, "Postman " + collectionName));
                }

                try {
                    Utils.pushDataToKafka(aktoCollectionId, topic, msgs, new ArrayList<>(), skipKafka);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"Error while pushing data to kafka: " + e.getMessage(), LogDb.DASHBOARD);
                    return;
                }
                loggerMaker.infoAndAddToDb(String.format("Pushed data in apicollection id %s", aktoCollectionId), LogDb.DASHBOARD);
            }

            LoadersDao.instance.updateIncrementalCount(loaderId, countMap.get(collectionId));
        }

    }

    String postmanCollectionFile;
    int postmanAktoCollectionId;
    public String importDataFromPostmanFile() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode collectionDetailsObj;
        try {
            collectionDetailsObj = mapper.readTree(postmanCollectionFile);
            String zipped = GzipUtils.zipString(postmanCollectionFile);
            File file = new File(HttpResponseParams.Source.POSTMAN, zipped);
            FilesDao.instance.insertOne(file);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error parsing postman collection file: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error while parsing the file");
            return ERROR.toLowerCase();
        }

        JsonNode infoNode = collectionDetailsObj.get("info");
        String collectionId = infoNode.get("_postman_id").asText();
        int aktoCollectionId = collectionId.hashCode();
        aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;

        String collectionName = infoNode.get("name").asText();


        if(ApiCollectionsDao.instance.findOne(Filters.eq("_id", aktoCollectionId)) == null){
            ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(aktoCollectionId, "Postman " + collectionName));
        }


        int accountId = Context.accountId.get();
        postmanAktoCollectionId = aktoCollectionId;

        ObjectId loaderId = createPostmanLoader();

        executorService.schedule(new Runnable() {
            public void run() {
                loggerMaker.infoAndAddToDb("Starting thread to process postman file", LogDb.DASHBOARD);
                Context.accountId.set(accountId);
                importDataFromPostmanFileMain(collectionDetailsObj, postmanAktoCollectionId, collectionName,allowReplay, skipKafka, loaderId);
            }
        }, 1, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private static void importDataFromPostmanFileMain(JsonNode collectionDetailsObj, int aktoCollectionId, String collectionName, boolean allowReplay, boolean skipKafka, ObjectId loaderId) {
        int count = apiCount(collectionDetailsObj);
        loggerMaker.infoAndAddToDb("API count in postman.json: " + count, LogDb.DASHBOARD);
        LoadersDao.instance.updateTotalCountNormalLoader(loaderId, count);

        List<String> msgs;
        try {
            msgs = generateMessages(collectionDetailsObj, aktoCollectionId, collectionName, allowReplay);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error generating messages: " + e.getMessage(), LogDb.DASHBOARD);
            LoadersDao.instance.updateIncrementalCount(loaderId, count);
            return ;
        }

        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        try {
            Utils.pushDataToKafka(aktoCollectionId, topic, msgs, new ArrayList<>(), skipKafka);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"Error pushing data to kafka", LogDb.DASHBOARD);
            return;
        }
        LoadersDao.instance.updateIncrementalCount(loaderId, count);
        loggerMaker.infoAndAddToDb(String.format("Pushed data in apicollection id %s", aktoCollectionId), LogDb.DASHBOARD);
    }

    public static int apiCount(JsonNode collectionDetailsObj) {
        ArrayList<JsonNode> jsonNodes = new ArrayList<>();
        Utils.fetchApisRecursively((ArrayNode) collectionDetailsObj.get("item"), jsonNodes);

        return jsonNodes.size();
    }

    private static List<String> generateMessages(JsonNode collectionDetailsObj, int aktoCollectionId, String collectionName, boolean allowReplay) {
        int accountId = Context.accountId.get();
        List<String> msgs = new ArrayList<>();
        Map<String, String> variablesMap = Utils.getVariableMap((ArrayNode) collectionDetailsObj.get("variable"));
        Map<String, String> authMap = Utils.getAuthMap(collectionDetailsObj.get("auth"), variablesMap);
        ArrayList<JsonNode> jsonNodes = new ArrayList<>();
        Utils.fetchApisRecursively((ArrayNode) collectionDetailsObj.get("item"), jsonNodes);
        if(jsonNodes.size() == 0) {
            loggerMaker.infoAndAddToDb("Collection "+ collectionName + " has no requests, skipping it", LogDb.DASHBOARD);
            return msgs;
        }
        loggerMaker.infoAndAddToDb(String.format("Found %s apis in collection %s", jsonNodes.size(), collectionName), LogDb.DASHBOARD);
        for(JsonNode item: jsonNodes){
            String apiName = item.get("name").asText();
            loggerMaker.infoAndAddToDb(String.format("Processing api %s if collection %s", apiName, collectionName), LogDb.DASHBOARD);
            Map<String, String> apiInAktoFormat = Utils.convertApiInAktoFormat(item, variablesMap, String.valueOf(accountId), allowReplay, authMap);
            if(apiInAktoFormat != null){
                try{
                    apiInAktoFormat.put("akto_vxlan_id", String.valueOf(aktoCollectionId));
                    String s = mapper.writeValueAsString(apiInAktoFormat);
                    loggerMaker.infoAndAddToDb(String.format("Api name: %s, CollectionName: %s", apiName, collectionName), LogDb.DASHBOARD);
                    msgs.add(s);
                } catch (JsonProcessingException e){
                    loggerMaker.errorAndAddToDb(e, e.toString(), LogDb.DASHBOARD);
                }
            }
        }

        return msgs;
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
}
