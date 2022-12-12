package com.akto.action;

import com.akto.ApiRequest;
import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.postman.Main;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.policies.AktoPolicy;
import com.akto.utils.SampleDataToSTI;
import com.akto.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.json.JSONObject;

import java.util.*;

public class PostmanAction extends UserAction {

    private static final Logger logger = LoggerFactory.getLogger(PostmanAction.class);
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


    private int apiCollectionId;
    public String createPostmanApi() throws Exception { // TODO: remove exception
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential == null) {
            addActionError("Please add postman credentials in settings");
            return ERROR.toUpperCase();
        }


        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
        if (apiCollection == null) {
            return ERROR.toUpperCase();
        }
        String apiName = "AKTO " + apiCollection.getDisplayName();

        List<SampleData> sampleData = SampleDataDao.instance.findAll(
                Filters.eq("_id.apiCollectionId", apiCollectionId)
            );
        String host =  apiCollection.getHostName();
        SampleDataToSTI sampleDataToSTI = new SampleDataToSTI();    
        sampleDataToSTI.setSampleDataToSTI(sampleData);
        Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = sampleDataToSTI.getSingleTypeInfoMap();
        OpenAPI openAPI = com.akto.open_api.Main.init(apiCollection.getDisplayName(),stiList, true, host);
        String openAPIStringAll = com.akto.open_api.Main.convertOpenApiToJSON(openAPI);

        List<SensitiveSampleData> SensitiveSampleData = SensitiveSampleDataDao.instance.findAll(
            Filters.eq("_id.apiCollectionId", apiCollectionId)
        );
        SampleDataToSTI sensitiveSampleDataToSTI = new SampleDataToSTI();
        sensitiveSampleDataToSTI.setSensitiveSampleDataToSTI(SensitiveSampleData);
        Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> sensitiveStiList = sensitiveSampleDataToSTI.getSingleTypeInfoMap();
        openAPI = com.akto.open_api.Main.init(apiCollection.getDisplayName(), sensitiveStiList, true, host);
        String openAPIStringSensitive = com.akto.open_api.Main.convertOpenApiToJSON(openAPI);

        Main main = new Main(postmanCredential.getApiKey());
        Map<String, String> openApiSchemaMap = new HashMap<>();
        openApiSchemaMap.put("All", openAPIStringAll);
        openApiSchemaMap.put("Sensitive", openAPIStringSensitive);

        main.createApiWithSchema(postmanCredential.getWorkspaceId(),apiName, openApiSchemaMap);

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


    public PostmanCredential fetchPostmanCredential() {
        int userId = getSUser().getId();
        ThirdPartyAccess thirdPartyAccess = ThirdPartyAccessDao.instance.findOne(
                Filters.and(
                        Filters.eq("owner", userId),
                        Filters.eq("credential.type", Credential.Type.POSTMAN)
                )
        );

        if (thirdPartyAccess == null) {
            return null;
        }

        return (PostmanCredential) thirdPartyAccess.getCredential();
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

    private boolean skipKafka = true;

    public String importDataFromPostman() throws Exception {
        PostmanCredential postmanCredential = fetchPostmanCredential();
        if (postmanCredential == null) {
            addActionError("Please add postman credentials in settings");
            return ERROR.toUpperCase();
        }
        Main main = new Main(postmanCredential.getApiKey());
        String workspaceId = this.workspace_id;
        logger.info("Fetching details for workspace_id: {}", workspace_id);
        JsonNode workspaceDetails = main.fetchWorkspace(workspaceId);
        JsonNode workspaceObj = workspaceDetails.get("workspace");
        ArrayNode collectionsObj = (ArrayNode) workspaceObj.get("collections");
        Map<Integer, List<String>> aktoFormat = new HashMap<>();
        for(JsonNode collectionObj: collectionsObj){
            List<String> msgs = new ArrayList<>();
            String collectionId = collectionObj.get("id").asText();
            int aktoCollectionId = collectionId.hashCode();
            aktoCollectionId = aktoCollectionId < 0 ? aktoCollectionId * -1: aktoCollectionId;
            JsonNode collectionDetails = main.fetchCollection(collectionId);
            JsonNode collectionDetailsObj = collectionDetails.get("collection");
            Map<String, String> variablesMap = Utils.getVariableMap((ArrayNode) collectionDetailsObj.get("variable"));
            ArrayList<JsonNode> jsonNodes = new ArrayList<>();
            fetchApisRecursively((ArrayNode) collectionDetailsObj.get("item"), jsonNodes);
            String collectionName = collectionDetailsObj.get("info").get("name").asText();
            if(jsonNodes.size() == 0) {
                logger.info("Collection {} has no requests, skipping it", collectionName);
                continue;
            }
            logger.info("Found {} apis in collection {}", jsonNodes.size(), collectionName);
            for(JsonNode item: jsonNodes){
                String apiName = item.get("name").asText();
                logger.info("Processing api {} if collection {}", apiName, collectionName);
                Map<String, String> apiInAktoFormat = Utils.convertApiInAktoFormat(item, variablesMap);
                if(apiInAktoFormat != null){
                    try{
                        String s = mapper.writeValueAsString(apiInAktoFormat);
                        logger.info("Api name: {}, CollectionName: {}, AktoFormat: {}", apiName, collectionName, s);
                        msgs.add(s);
                    } catch (JsonProcessingException e){
                        logger.error(e.getMessage(), e);
                    }
                }
            }
            if(msgs.size() > 0) {
                aktoFormat.put(aktoCollectionId, msgs);
                if(ApiCollectionsDao.instance.findOne(Filters.eq("_id", aktoCollectionId)) == null){
                    ApiCollectionsDao.instance.insertOne(ApiCollection.createManualCollection(aktoCollectionId, collectionName));
                }
                logger.info("Pushed {} apis from collection {}", msgs.size(), collectionName);
            }

        }
        //Push to Akto
        logger.info("Starting to push data to Akto, pushin data in {} collections", aktoFormat.size());
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        List<HttpResponseParams> responses = new ArrayList<>();
        for(Map.Entry<Integer, List<String>> entry: aktoFormat.entrySet()){
            //For each entry, push a message to Kafka
            int aktoCollectionId = entry.getKey();
            List<String> msgs = entry.getValue();
            for(String msg: msgs){
                if(msg.length() < 0.8 * KafkaListener.BATCH_SIZE_CONFIG){
                    if(!skipKafka){
                        KafkaListener.kafka.send(msg,"har_" + topic);
                    } else {
                        HttpResponseParams responseParams =  HttpCallParser.parseKafkaMessage(msg);
                        responseParams.getRequestParams().setApiCollectionId(aktoCollectionId);
                        responses.add(responseParams);
                        logger.info("Api successfully pushed to Akto");
                    }
                } else {
                    logger.error("Apiinfo too big, not sending to Kafka, msg size: {}", msg.length());
                }
            }

            if(skipKafka) {
                HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                SingleTypeInfo.fetchCustomDataTypes();
                APICatalogSync apiCatalogSync = parser.syncFunction(responses, true, false);
                AktoPolicy aktoPolicy = new AktoPolicy(parser.apiCatalogSync, false); // keep inside if condition statement because db call when initialised
                aktoPolicy.main(responses, apiCatalogSync, false);
                ResourceAnalyser resourceAnalyser = new ResourceAnalyser(300_000, 0.01, 100_000, 0.01);
                for (HttpResponseParams responseParams: responses)  {
                    responseParams.requestParams.getHeaders().put("x-forwarded-for", Collections.singletonList("127.0.0.1"));
                    resourceAnalyser.analyse(responseParams);
                }
                resourceAnalyser.syncWithDb();
            }
            logger.info("Pushed data in apicollection id {}", aktoCollectionId);
        }
        return SUCCESS.toUpperCase();
    }

    private void fetchApisRecursively(ArrayNode items, ArrayList<JsonNode> jsonNodes) {
        if(items == null || items.size() == 0){
            return;
        }
        for(JsonNode item: items){
            if(item.has("item")){
                fetchApisRecursively( (ArrayNode) item.get("item"), jsonNodes);
            } else {
                jsonNodes.add(item);
            }
        }

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
}
