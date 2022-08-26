package com.akto.action;

import java.io.IOException;
import java.util.*;

import com.akto.dao.APISpecDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.client.model.Filters;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class ApiCollectionsAction extends UserAction {

    public static void main(String[] args) throws IOException {
        HttpPost post = new HttpPost("http://localhost:8092/api/importInBurp");
        Map<String, String> req = new HashMap<>();
        req.put("collectionName", "rzp_1");
        String json = new Gson().toJson(req);
        HttpEntity e = new StringEntity(json);
        post.setEntity(e);
        post.setHeader("Content-type", "application/json");
        post.setHeader("X-API-KEY", "odjxsHGcJlByeFwdgj8m1x5OtwvdnGI4hbOXUjNv");

        CloseableHttpClient httpClient = HttpClientBuilder.create().setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
        CloseableHttpResponse response =  httpClient.execute(post);
        String responseString = EntityUtils.toString(response.getEntity());

        JsonObject jsonResp = new Gson().fromJson(responseString, JsonObject.class); // String to JSONObject
        JsonArray importInBurpResult = jsonResp.get("importInBurpResult").getAsJsonArray();
        for (JsonElement o: importInBurpResult) {
            JsonObject j = o.getAsJsonObject();
            System.out.println(j.get("url").getAsString());
            System.out.println(j.get("req").getAsString());
            System.out.println(j.get("resp").getAsString());
            break;
        }
    }

    List<ApiCollection> apiCollections = new ArrayList<>();
    int apiCollectionId;

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();

    }

    static int maxCollectionNameLength = 25;
    private String collectionName;
    public String createCollection() {
        if (this.collectionName == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        if (this.collectionName.length() > maxCollectionNameLength) {
            addActionError("Custom collections max length: " + maxCollectionNameLength);
            return ERROR.toUpperCase();
        }

        for (char c: this.collectionName.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '.' || c == '_';

            if (!(alphabets || numbers || specialChars)) {
                addActionError("Collection names can only be alphanumeric and contain '-','.' and '_'");
                return ERROR.toUpperCase();
            }
        }

        // unique names
        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if (sameNameCollection != null){
            addActionError("Collection names must be unique");
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName,Context.now(),new HashSet<>(), null, 0);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(apiCollection);
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCollection() {
        if(apiCollectionId == 0) {
            return Action.SUCCESS.toUpperCase();
        }
        ApiCollectionsDao.instance.deleteAll(Filters.eq("_id", apiCollectionId));
        SingleTypeInfoDao.instance.deleteAll(Filters.eq("apiCollectionId", apiCollectionId));
        APISpecDao.instance.deleteAll(Filters.eq("apiCollectionId", apiCollectionId));
        SensitiveParamInfoDao.instance.deleteAll(Filters.eq("apiCollectionId", apiCollectionId));
        // TODO : Markov and Relationship
        // MarkovDao.instance.deleteAll()
        // RelationshipDao.instance.deleteAll();
        return Action.SUCCESS.toUpperCase();
    } 

    public List<ApiCollection> getApiCollections() {
        return this.apiCollections;
    }

    public void setApiCollections(List<ApiCollection> apiCollections) {
        this.apiCollections = apiCollections;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }
  
    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    } 

}
