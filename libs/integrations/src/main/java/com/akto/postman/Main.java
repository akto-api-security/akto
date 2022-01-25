package com.akto.postman;

import com.akto.ApiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONObject;

import java.util.*;


public class Main {
    private final String apiKey;
    public static final String BASE_URL = "https://api.getpostman.com/";

    public Main(String apiKey) {
        this.apiKey = apiKey;
    }

    public static void main(String[] args) {
        Main main = new Main("PMAK-61dda7b35e30ce28fc2a131e-ccf78be950e3026bec27313d4472b7259b");
//        main.fetchWorkspaces();
        String workspaceId = "d0510f32-2e14-4f87-8024-b85bd85de3f1";
        String collectionId = "223645de-9ede-4f12-93c4-1dcf80487ee6";

//        main.createApiWithSchema(workspaceId,"Test", );
//        String apiId = main.createApi(workspaceId,"sixth API");
//        System.out.println(apiId);
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

    public void createApiWithSchema(String workspaceId, String apiName, Map<String, String> openApiSchemaMap) {
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

        // get versions (if not present create them)
        Map<String, String> apiVersionNameMap = getVersion(apiId,openApiSchemaMap.keySet());


        for (String name: apiVersionNameMap.keySet()) {
            // Finally, replace schema for all versions
            addSchema(apiId, apiVersionNameMap.get(name), openApiSchemaMap.get(name));
        }



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

}
