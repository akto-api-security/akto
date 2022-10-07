package com.akto.action;

import com.akto.ApiRequest;
import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.APISpec;
import com.akto.dto.ApiCollection;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.User;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.PostmanCredential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.postman.Main;
import com.akto.utils.SampleDataToSTI;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.swagger.v3.oas.models.OpenAPI;
import org.json.JSONObject;

import java.util.*;

public class PostmanAction extends UserAction {
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


    private PostmanCredential fetchPostmanCredential() {
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
