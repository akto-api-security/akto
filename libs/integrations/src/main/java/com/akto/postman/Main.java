package com.akto.postman;

import com.akto.ApiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;


public class Main {
    private final String apiKey;
    public static final String BASE_URL = "https://api.getpostman.com/";

    public Main(String apiKey) {
        this.apiKey = apiKey;
    }

    public void getPostmanCollection(String collectionId) {
        String url = "https://api.getpostman.com/collections/" + "";
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        jsonNode.get("collection");

    }

    public String createApi(String workspaceId, String apiName) {
        String url = BASE_URL + "apis?workspace="+workspaceId;

        JSONObject child = new JSONObject();
        child.put("name", apiName);
        child.put("summary", "Summary");
        child.put("description", "description");

        JSONObject requestBody = new JSONObject();
        requestBody.put("api", child);

        String json = requestBody.toString();
        JsonNode node = ApiRequest.postRequest(generateHeadersWithAuth(), url,json);

        System.out.println("createApi: " + node.toPrettyString());
        
        String apiId = node.get("api").get("id").textValue();
        return apiId;
    }

    public Map<String,String> getVersion(String apiId, Set<String> versionNameList) {
        String url = BASE_URL + "apis/"+apiId+"/versions";
        JsonNode node = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        Iterator<JsonNode> versionNodes = node.get("versions").elements();
        Map<String, String> versionNameIdMap = new HashMap<>();
        for (String name: versionNameList) {
            versionNameIdMap.put(name, null);
        }
        while (versionNodes.hasNext()) {
            JsonNode n = versionNodes.next();
            String versionName = n.get("name").textValue();
            for (String v: versionNameList) {
                if (versionName.equals(v)) {
                    versionNameIdMap.put(v, n.get("id").textValue());
                }
            }
        }

        for (String name: versionNameIdMap.keySet()) {
            if (versionNameIdMap.get(name) == null) {
                // create version
                String vId = createVersion(name, apiId);
                versionNameIdMap.put(name, vId);
            }
        }


        return versionNameIdMap;
    }

    public String createVersion(String versionName, String apiId) {
        String url = BASE_URL +  "apis/" + apiId + "/versions"; // TODO: created by me

        JSONObject child = new JSONObject();
        child.put("name", versionName);
        JSONObject requestBody = new JSONObject();
        requestBody.put("version", child);
        String json = requestBody.toString();

        JsonNode jsonNode = ApiRequest.postRequest(generateHeadersWithAuth(),url,json);

        return jsonNode.get("version").get("id").textValue();
    }

    public void addSchema(String apiId, String version, String openApiSchema) {
        String url1 = BASE_URL + "apis/" + apiId + "/versions/" + version;
        JsonNode getNode = ApiRequest.getRequest(generateHeadersWithAuth(),url1);
        Iterator<JsonNode> versions = getNode.get("version").get("schema").elements();

        JSONObject child = new JSONObject();
        child.put("language", "json");
        child.put("schema", openApiSchema);
        child.put("type", "openapi3");
        JSONObject requestBody = new JSONObject();
        requestBody.put("schema", child);
        String json = requestBody.toString();

        // if version exists update it else create new one
        if (versions.hasNext()) {
            String url = BASE_URL + "apis/"+apiId+"/versions/" + version + "/schemas/" + versions.next().textValue();
            JsonNode node = ApiRequest.putRequest(generateHeadersWithAuth(), url,json);
        } else {
            String url = BASE_URL + "apis/"+apiId+"/versions/" + version + "/schemas";
            JsonNode node = ApiRequest.postRequest(generateHeadersWithAuth(), url,json);
        }

    }

    public String addSchemaV10(String apiId, String openApiSchema){
        String url = BASE_URL + "apis/" + apiId + "?include=schemas";

        JsonNode getNode = ApiRequest.getRequest(generateHeadersWithAuthForV10(),url);

        Set<String> schemaIds = new HashSet<>();
        if(getNode.has("schemas")) {
            Iterator<JsonNode> schemas = getNode.get("schemas").elements();
            while (schemas.hasNext()) {
                schemaIds.add(schemas.next().get("id").textValue());
            }
        }
        if(schemaIds.isEmpty()){
            return createSchema(apiId, openApiSchema);
        }
        for (String schemaId: schemaIds) {
            String url2 = BASE_URL + "apis/"+apiId+"/schemas/" + schemaId;
            JsonNode response = ApiRequest.getRequest(generateHeadersWithAuthForV10(), url2);
            JsonNode data = response.get("files").get("data");
            while (data.elements().hasNext()){
                JsonNode file = data.elements().next();
                if(file.get("name").textValue().equals("index.json")){
                    String url1 = BASE_URL + "apis/"+apiId+"/schemas/" + schemaId + "/files/index.json";
                    JSONObject obj = new JSONObject();
                    obj.put("content", openApiSchema);
                    JsonNode node = ApiRequest.putRequest(generateHeadersWithAuthForV10(), url1, obj.toString());
                    return node.get("id").textValue();
                }
            }
        }
        return createSchema(apiId, openApiSchema);
    }

    private String createSchema(String apiId, String openApiSchema) {
        String url1 = BASE_URL + "apis/"+ apiId +"/schemas";
        JSONObject fileObj = new JSONObject();
        fileObj.put("content", openApiSchema);
        fileObj.put("path", "index.json");
        JSONArray files = new JSONArray();
        files.put(0, fileObj);

        JSONObject child = new JSONObject();
        child.put("files", files);
        child.put("type", "openapi:3");

        String json = child.toString();
        JsonNode node = ApiRequest.postRequest(generateHeadersWithAuthForV10(), url1,json);
        return node.get("id").textValue();
    }

    public void createApiWithSchema(String workspaceId, String apiName, String openApiSchema) {
        // Get akto_<collectionName> API
        String url = BASE_URL +  "apis?name=" + apiName + "&" + "workspace=" + workspaceId; // TODO: created by me
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        JsonNode apisNode = jsonNode.get("apis"); // TODO:
        String apiId;

        if (apisNode.elements().hasNext()) {
            // get apiId
            apiId = apisNode.get(0).get("id").textValue();
        } else {
            // Create New API
            apiId = createApi(workspaceId,apiName);
        }
        addSchemaV10(apiId, openApiSchema);
    }


    public JsonNode fetchApiCollections() {
        String url = BASE_URL + "collections";
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        if (jsonNode == null) return null;

        return jsonNode.get("collections");
    }

    public JsonNode fetchPostmanCollectionString(String collectionId) {
        String url = BASE_URL + "collections/" + collectionId;
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        if (jsonNode == null) return null;

        return jsonNode;
    }

    public JsonNode fetchWorkspaces() {
        String url = BASE_URL + "workspaces";
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);
        if (jsonNode == null) return null;

        return jsonNode.get("workspaces");
    }

    public Map<String,String> generateHeadersWithAuth() {
        Map<String,String> headersMap = new HashMap<>();
        headersMap.put("X-API-Key",apiKey);
        return headersMap;
    }

    public Map<String,String> generateHeadersWithAuthForV10() {
        Map<String,String> headersMap = new HashMap<>();
        headersMap.put("X-API-Key",apiKey);
        headersMap.put("Accept", "application/vnd.api.v10+json");
        return headersMap;
    }

    public String createWorkspace() {
        String url = BASE_URL + "workspaces";
        JSONObject json = new JSONObject();
        JSONObject workspace = new JSONObject();
        json.put("workspace", workspace);

        workspace.put("name", UUID.randomUUID().toString());
        workspace.put("type", "personal");
        workspace.put("description", "test");
        JsonNode jsonNode = ApiRequest.postRequest(generateHeadersWithAuth(), url, json.toString());
        return jsonNode.get("workspace").get("id").asText();
    }

    public String fetchOneApiFromWorkspace(String workspaceId) {
        String url = BASE_URL + "apis?workspace=" + workspaceId;
        JsonNode jsonNode = ApiRequest.getRequest(generateHeadersWithAuth(), url);

        ArrayNode apiList = (ArrayNode) jsonNode.get("apis");
        String apiId = apiList.get(0).get("id").asText();
        
        url = BASE_URL + "apis/" + apiId;
        return ApiRequest.getRequest(generateHeadersWithAuth(), url).toString();
        
    }

    public String deleteWorkspace(String workspaceId) {
        String url = BASE_URL + "workspaces/" + workspaceId;
        JsonNode jsonNode = ApiRequest.deleteRequest(generateHeadersWithAuth(), url);
        return jsonNode.toString();
    }

    public JsonNode fetchWorkspace(String workspaceId){
        String url = BASE_URL + "workspaces/" + workspaceId;
        return ApiRequest.getRequest(generateHeadersWithAuth(), url);
    }

    public JsonNode fetchCollection(String collectionId){
        String url = BASE_URL + "collections/" + collectionId;
        return ApiRequest.getRequest(generateHeadersWithAuth(), url);
    }
}
