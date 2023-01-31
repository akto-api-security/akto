package com.akto.runtime;


import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.Relationship;
import com.akto.parsers.HttpCallParser;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.HttpRequestParams;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelationshipTest {
    ObjectMapper mapper = new ObjectMapper();
    JsonFactory factory = mapper.getFactory();
    @Test
    public void testExtractAllValuesFromPayloadNullCase() {
        Map<String,Set<String>> values= new HashMap<>();
        RelationshipSync.extractAllValuesFromPayload(null, new ArrayList<>(), values);
        assertEquals(0,values.keySet().size());
    }

    @Test
    public void testExtractAllValuesFromPayload1() throws IOException {
        JsonParser jp = factory.createParser("{\"name\":\"avneesh\", \"age\":99, \"finalAge\": 99}");
        JsonNode node = mapper.readTree(jp);
        Map<String,Set<String>> values= new HashMap<>();
        RelationshipSync.extractAllValuesFromPayload(node, new ArrayList<>(), values);
        assertEquals(3,values.keySet().size());
        assertTrue(values.get("name").contains("avneesh"));
        assertTrue(values.get("age").contains("99"));
        assertTrue(values.get("finalAge").contains("99"));
    }

    @Test
    public void testExtractAllValuesFromPayload2() throws IOException {
        JsonParser jp = factory.createParser("[{\"name\":\"avneesh\", \"age\":99, \"finalAge\": 99}]");
        JsonNode node = mapper.readTree(jp);
        Map<String,Set<String>> values= new HashMap<>();
        RelationshipSync.extractAllValuesFromPayload(node, new ArrayList<>(), values);
        assertEquals(3,values.keySet().size());
        assertTrue(values.get("$#name").contains("avneesh"));
        assertTrue(values.get("$#age").contains("99"));
        assertTrue(values.get("$#finalAge").contains("99"));
    }

    @Test
    public void testExtractAllValuesFromPayload3() throws IOException {
        JsonParser jp = factory.createParser("{\"interests\": [{\"name\":\"football\"}]}");
        JsonNode node = mapper.readTree(jp);
        Map<String,Set<String>> values= new HashMap<>();
        RelationshipSync.extractAllValuesFromPayload(node, new ArrayList<>(), values);
        assertEquals(1,values.keySet().size());
        assertTrue(values.get("interests#$#name").contains("football"));
    }

    @Test
    public void testExtractAllValuesFromPayload4() throws IOException {
        JsonParser jp = factory.createParser("{\"name\":\"null\", \"age\":99, \"finalAge\": 99, \"hobby\":\"   \"}");
        JsonNode node = mapper.readTree(jp);
        Map<String,Set<String>> values= new HashMap<>();
        RelationshipSync.extractAllValuesFromPayload(node, new ArrayList<>(), values);
        assertEquals(4,values.keySet().size());
        assertTrue(values.get("age").contains("99"));
        assertTrue(values.get("finalAge").contains("99"));
    }

    public Map<String, List<String>> generateHeaders(String userId) {
        Map<String, List<String>> headers = new HashMap<>();
        String name = "Access-Token";
        headers.put(name, Collections.singletonList(userId));
        return headers;
    }

    public HttpResponseParams generateHttpResponseParamsForRelationship(String url, String method,
                                                                                       String userId, int statusCode,
                                                                                       String reqPayload, String respPayload) {

        HttpRequestParams  httpRequestParams = new HttpRequestParams(
                method, url, "",generateHeaders(userId),reqPayload, 0
        );
        return new HttpResponseParams(
                "",statusCode,"ok", generateHeaders(userId), respPayload, httpRequestParams, Context.now(), "1111",false, Source.OTHER, "", ""
        );
    }

    @Test
    public void testBuildParameterMap() throws Exception {
        List<HttpResponseParams> respList = new ArrayList<>();
        String req, resp;

        req = "{\"name\":\"avneesh\", \"age\":99}";
        resp = "{\"status\":\"done\", \"id\": 11111}";
        respList.add(generateHttpResponseParamsForRelationship("api/a", "post", "1",200,req,resp));

        req = "{\"name\":\"ankush\", \"age\":93}";
        resp = "{\"status\":\"done\", \"id\": 22222}";
        respList.add(generateHttpResponseParamsForRelationship("api/a", "post", "2",200,req,resp));

        req = "{\"user_id\":\"11111\"}";
        resp = "{\"familyName\":\"hota\"}";
        respList.add(generateHttpResponseParamsForRelationship("api/b", "get", "1",200,req,resp));

        req = "{\"user_id\":\"22222\"}";
        resp = "{\"familyName\":\"jain\"}";
        respList.add(generateHttpResponseParamsForRelationship("api/b", "get", "2",200,req,resp));

        req = "{\"user_id\":\"22222\"}";
        resp = "{\"familyName\":\"jain\"}";
        respList.add(generateHttpResponseParamsForRelationship("api/b", "get", "2",200,req,resp));

        req = "{\"familyName\":\"jain\"}";
        resp = "{\"state\":\"Rajasthan\"}";
        respList.add(generateHttpResponseParamsForRelationship("api/c", "get", "2",200,req,resp));

        RelationshipSync relationshipSync = new RelationshipSync(2,100,1000000);
        for (HttpResponseParams responseParams: respList) {
            relationshipSync.buildParameterMap(responseParams,"Access-Token");
        }


        Map<String,Map<String, Map<String, Set<Relationship.ApiRelationInfo>>>> userWiseParameterMap = relationshipSync.userWiseParameterMap;

        assertEquals(2, userWiseParameterMap.keySet().size());
        assertEquals(5, userWiseParameterMap.get("1").keySet().size());
        assertEquals(6, userWiseParameterMap.get("2").keySet().size());
        assertEquals(1, userWiseParameterMap.get("1").get("11111").get("request").size());
        assertEquals("api/b", userWiseParameterMap.get("1").get("11111").get("request").iterator().next().getUrl());
        assertEquals(1, userWiseParameterMap.get("1").get("11111").get("response").size());
        assertEquals("api/a", userWiseParameterMap.get("1").get("11111").get("response").iterator().next().getUrl());
        assertEquals(1, userWiseParameterMap.get("1").get("avneesh").get("request").size());
        assertEquals(0, userWiseParameterMap.get("1").get("avneesh").get("response").size());
        assertEquals(1, userWiseParameterMap.get("2").get("jain").get("request").size());
        assertEquals(1, userWiseParameterMap.get("2").get("jain").get("response").size());

        relationshipSync.buildRelationships();

        Map<String,Relationship> relations = relationshipSync.relations;
        assertEquals(relations.size(), 2);

        Relationship.ApiRelationInfo parent = new Relationship.ApiRelationInfo("api/a","post", false,"id" ,200);
        Relationship.ApiRelationInfo child = new Relationship.ApiRelationInfo("api/b","get", false, "user_id",200);
        String key = parent.hashCode() + "." + child.hashCode();
        assertEquals(2,relations.get(key).getUserIds().size());
        assertEquals(2,relations.get(key).getCountMap().get(Flow.calculateTodayKey()));

        relationshipSync.getBulkUpdates();
        assertEquals(2, relations.size());

        parent = new Relationship.ApiRelationInfo("api/b","get", false,"familyName" ,200);
        child = new Relationship.ApiRelationInfo("api/c","get", false, "familyName",200);
        key = parent.hashCode() + "." + child.hashCode();
        assertEquals(1,relations.get(key).getUserIds().size());
        assertEquals(1,relations.get(key).getCountMap().get(Flow.calculateTodayKey()));

    }
}
