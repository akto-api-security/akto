package com.akto.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.springframework.security.access.method.P;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.dto.test_editor.Util;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.type.URLMethods.Method;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.filter.Filter;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class FilterValidationTests {
    

    @Test
    public void testHappyCase() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("happy_case");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(1, varMap.size());
        assertEquals(true, varMap.containsKey("urlVar"));
    }

    @Test
    public void testRequestKeyValueExtraction() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_key_val_extract");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }

    @Test
    public void testRequestKeyNeqConditionExtraction() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_key_val_neq_extract");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertNotEquals("id", varMap.get("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }

    @Test
    public void testRequestValGteConditionExtraction() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_val_gte_extract");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals("id", varMap.get("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }

    @Test
    public void testRequestValGtConditionExtraction() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_val_gt_extract");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals("id", varMap.get("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }
    
    @Test
    public void testRequestValLtConditionExtraction() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_val_lt_extract");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals("id", varMap.get("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }

    @Test
    public void testRequestValLteConditionExtraction() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_val_lte_extract");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals("id", varMap.get("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }

    @Test
    public void testRequestValGteConditionExtractionInvalid() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_val_gte_extract_invalid");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());
        assertEquals(0, varMap.size());
    }

    @Test
    public void testContainsEitherCondition() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("req_payload_val_gte_extract_invalid");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());
        assertEquals(0, varMap.size());
    }

    @Test
    public void testContainsNotContainsConditions() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("contains_either_valid");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());

        filterObj = config.get("contains_either_invalid");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());

        filterObj = config.get("contains_all_valid");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());

        filterObj = config.get("contains_all_invalid");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());

        filterObj = config.get("contains_either_valid_extract");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals("course", varMap.get("keyName"));
        assertEquals(true, varMap.containsKey("keyVal"));
    }

    @Test
    public void testComplexConditions() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("complex_and_condition_filter");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());

        filterObj = config.get("complex_and_condition_filter_invalid");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());

        filterObj = config.get("complex_or_condition_filter");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());

        filterObj = config.get("complex_or_condition_filter_invalid");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());

        filterObj = config.get("complex_and_condition_filter_extract");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals(true, varMap.containsKey("methodName"));

        filterObj = config.get("complex_or_condition_filter_extract");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(2, varMap.size());
        assertEquals(true, varMap.containsKey("keyName"));
        assertEquals(true, varMap.containsKey("methodName"));

    }

    @Test
    public void testContainsJwtConditions() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("contains_jwt");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());

        Map<String, List<String>> headers = rawApi.getRequest().getHeaders();
        headers.remove("authorization");
        rawApi.getRequest().setHeaders(headers);
        filterObj = config.get("contains_jwt");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());
    }

    @Test
    public void testRegexConditions() {
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);

        Object filterObj = config.get("regex_filter");
        com.akto.dao.test_editor.filter.ConfigParser parser = new com.akto.dao.test_editor.filter.ConfigParser();
        ConfigParserResult configParserResult = parser.parse(filterObj);
        Filter filter = new Filter();
        Map<String, Object> varMap = new HashMap<>();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(1, varMap.size());

        filterObj = config.get("regex_filter_invalid");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(false, dataOperandsFilterResponse.getResult());
        assertEquals(0, varMap.size());

        filterObj = config.get("regex_filter_extract_multiple");
        configParserResult = parser.parse(filterObj);
        varMap = new HashMap<>();
        dataOperandsFilterResponse = filter.isEndpointValid(configParserResult.getNode(), rawApi, rawApi, apiInfoKey, null, null, false, "filter", varMap, "logId", false);
        assertEquals(true, dataOperandsFilterResponse.getResult());
        assertEquals(1, varMap.size());
        assertEquals(4, ((List) varMap.get("keyName")).size());
    }

    @Test
    public void testPayloadModificationCases() {
        
        Map<String, Object> config = initconfig();
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "https://epsilon.6sense.com:443/v3/company/details", Method.PUT);
        RawApi rawApi = initRawapi(apiInfoKey);
        ObjectMapper mapper = new ObjectMapper();

        // add key at root
        String keyPath = "newKey";
        Object value = "xyz";
    
        String payload = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"details\": {\"course\": \"MECH\"}}";
        rawApi.getRequest().setBody(payload);
        rawApi = com.akto.test_editor.Utils.modifyRawApiPayload(rawApi, keyPath, value);
        assertEquals(rawApi.getRequest().getBody(), "{\"id\":101,\"name\":\"Stud-101\",\"email\":\"stude_101@example.com\",\"details\":{\"course\":\"MECH\"},\"newKey\":\"xyz\"}");


        // add key inside existing object
        keyPath = "details.newKey";
        value = "xyz";
    
        payload = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"details\": {\"course\": \"MECH\"}}";
        rawApi.getRequest().setBody(payload);
        rawApi = com.akto.test_editor.Utils.modifyRawApiPayload(rawApi, keyPath, value);
        assertEquals(rawApi.getRequest().getBody(), "{\"id\":101,\"name\":\"Stud-101\",\"email\":\"stude_101@example.com\",\"details\":{\"course\":\"MECH\",\"newKey\":\"xyz\"}}");
        
        // add key at nonexisting path
        keyPath = "nonexistent.newKey";
        value = "xyz";
    
        payload = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"details\": {\"course\": \"MECH\"}}";
        rawApi.getRequest().setBody(payload);
        rawApi = com.akto.test_editor.Utils.modifyRawApiPayload(rawApi, keyPath, value);
        assertEquals(rawApi.getRequest().getBody(), "{\"id\":101,\"name\":\"Stud-101\",\"email\":\"stude_101@example.com\",\"details\":{\"course\":\"MECH\"},\"nonexistent\":{\"newKey\":\"xyz\"}}");

        // add key to list
        keyPath = "details[0].newKey";
        value = "xyz";
    
        payload = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"details\": [{\"course\": \"MECH\"}]}";
        rawApi.getRequest().setBody(payload);
        rawApi = com.akto.test_editor.Utils.modifyRawApiPayload(rawApi, keyPath, value);
        assertEquals(rawApi.getRequest().getBody(), "{\"id\":101,\"name\":\"Stud-101\",\"email\":\"stude_101@example.com\",\"details\":[{\"course\":\"MECH\",\"newKey\":\"xyz\"}]}");

        // create new list
        keyPath = "newList[0].newKey";
        value = "xyz";
    
        payload = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"details\": [{\"course\": \"MECH\"}]}";
        rawApi.getRequest().setBody(payload);
        rawApi = com.akto.test_editor.Utils.modifyRawApiPayload(rawApi, keyPath, value);
        System.out.println(rawApi.getRequest().getBody());
        assertEquals(rawApi.getRequest().getBody(), "{\"id\":101,\"name\":\"Stud-101\",\"email\":\"stude_101@example.com\",\"details\":[{\"course\":\"MECH\"}],\"newList\":[{\"newKey\":\"xyz\"}]}");

        // create list inside list
        keyPath = "newList[0].sublist[0].newKeyy";
        value = "xyz";
    
        payload = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"details\": [{\"course\": \"MECH\"}]}";
        rawApi.getRequest().setBody(payload);
        rawApi = com.akto.test_editor.Utils.modifyRawApiPayload(rawApi, keyPath, value);
        System.out.println(rawApi.getRequest().getBody());
        assertEquals(rawApi.getRequest().getBody(), "{\"id\":101,\"name\":\"Stud-101\",\"email\":\"stude_101@example.com\",\"details\":[{\"course\":\"MECH\"}],\"newList\":[{\"sublist\":[{\"newKeyy\":\"xyz\"}]}]}");

    }

    public Map<String, Object> initconfig() {
        Map<String, Object> config = new HashMap<>();
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("filter.yaml");
            config = new ObjectMapper(new YAMLFactory()).readValue(inputStream, Map.class);
        } catch(Exception e) {
            System.out.println("error " + e.getMessage());
        }
        return config;
    }

    public RawApi initRawapi(ApiInfo.ApiInfoKey apiInfoKey) {
        String payload1 = "{\"id\": 101, \"name\": \"Stud-101\", \"email\": \"stude_101@example.com\", \"course\": \"MECH\"}";
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest("https://epsilon.6sense.com:443/v3/company/details", "limit=10&redirect=false", apiInfoKey.getMethod().name(), payload1, new HashMap<>(), "");

        //BasicDBObject basicDBObject =  BasicDBObject.parse(originalHttpRequest.getBody());
        //basicDBObject.containsKey(apiInfoKey);

        Map<String, java.util.List<String>> headers = new HashMap<>();
        headers.put("authorization", createList("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdGF0dXMiOiJzdWNjZXNzIiwiZGF0YSI6eyJpZCI6MjIsInVzZXJuYW1lIjoiYXR0YWNrZXIiLCJlbWFpbCI6ImF0dGFja2VyQGdtYWlsLmNvbSIsInBhc3N3b3JkIjoiMTY2YzU1YjUxZmQ1ZmJkYTg3NWFiNmMzMTg0NDIyNTUiLCJyb2xlIjoiY3VzdG9tZXIiLCJkZWx1eGVUb2tlbiI6IiIsImxhc3RMb2dpbklwIjoiIiwicHJvZmlsZUltYWdlIjoiYXNzZXRzL3B1YmxpYy9pbWFnZXMvdXBsb2Fkcy9kZWZhdWx0LnN2ZyIsInRvdHBTZWNyZXQiOiIiLCJpc0FjdGl2ZSI6dHJ1ZSwiY3JlYXRlZEF0IjoiMjAyMy0wMy0xMCAwNTozOToxOC4yOTkgKzAwOjAwIiwidXBkYXRlZEF0IjoiMjAyMy0wMy0xMCAwNTozOToxOC4yOTkgKzAwOjAwIiwiZGVsZXRlZEF0IjpudWxsfSwiaWF0IjoxNjc4NDI3Mjg1LCJleHAiOjE5OTM3ODcyODV9.hBAPAJm1FZIpDb7fm4nT3GY_u3R0KeyjqK-Ns5pcz22RN5_qhWt-K98y8DdELjUsRKVodAFPOki0QBmAqdhp5umgJB1ZPk4uEKLg2AI6ztr5729UezMbQozbIOu8UFmVm2crJn5YZKCbPKCcDwRUpisICbjDtJ5PD41RhZfLut8"));
        headers.put("f2", createList("http://xyz.com"));
        headers.put("Cfontent-Length", createList(" 762"));
        headers.put("cookie", createList("mutiny.user.token=d7b12062-724d-4e92-9aa2-7b8c2892f71b; mutiny.user.token=d7b12062-724d-4e92-9aa2-7b8c2892f71b; mutiny.user.session=9d9a1cf2-0989-4755-a5a5-1d60059fd00f; mutiny.user.session=9d9a1cf2-0989-4755-a5a5-1d60059fd00f; mutiny.user.session_number=1; mutiny.user.session_number=1; loom_referral_video=313bf71d20ca47b2a35b6634cefdb761; loom_anon_comment=9f0a021fc6d54c159eff17b9dfde1cd6; ajs_anonymous_id=%2288bfc7c8-558e-4a27-8a22-df3fb000b7fa%22; loomhq:thirdPartyCookieSupported=true; _fs_sample_user=false; _rdt_uuid=1709878031567.505f28d9-1e0c-45b2-82fe-85e43db50702; _ga=GA1.1.1327063241.1709878032; _uetsid=1a601440dd1211eea5115bd1dad24a1d; _uetvid=22fe1d80f19711ed9b574bc2df1804bc; _tt_enable_cookie=1; _ttp=7r1MndIyW-ox3IlXOZHKBqgyWvF; _clck=16c4vtk%7C2%7Cfjw%7C0%7C1528; __hstc=185935670.ebc6a4595609b7884ffedd34feaf528e.1709878032459.1709878032459.1709878032459.1; hubspotutk=ebc6a4595609b7884ffedd34feaf528e; __hssrc=1; __hssc=185935670.1.1709878032459; _clsk=15p3ckj%7C1709878033332%7C1%7C1%7Ca.clarity.ms%2Fcollect; _gcl_au=1.1.357658991.1709878031.688003409.1709878043.1709878042; connect.sid=s%3AjvlNRnOz612UAASEg6J7qhAja7EiQk1F.lb0257cK49oOmgXYsNDFYa%2BH91q%2B9e7YId6No8U5azA; loom-sst=lsst-d1efbe35-0ccd-4901-a580-fc31871973af; connect.lpid=p%3Alpid-3199c9ba-3603-4554-8150-c76b61fb38ad.MdTUr9zVztZjDEMwSoP7O9rdPU7obi1TezrMp1ZSBzov1; __Host-psifi.analyticsTrace=1d75acf47eb97ba25cecd0c2fe9d811320f0335f5dd46d4e6c2fa607f3a3564c; __Host-psifi.analyticsTraceV2=2f486cb229c40cf46dd834d2da9b556cdaf58d5ffa92c1b823e6578bd2951e8f9017ad867683eb922ac4b47397c7a8a23a8ba4afe90a1c465033651339ff7af4; ajs_user_id=28411178; ajs_anonymous_id=88bfc7c8-558e-4a27-8a22-df3fb000b7fa; __stripe_mid=6de4f532-7695-4447-9c00-ff6195b3b9a885896f; __stripe_sid=b7e40e6b-4062-4f7b-94c8-a0efb1fbea7d042c40; _ga_H93TGDH6MB=GS1.1.1709878031.1.1.1709878052.39.0.0; AWSALBAuthNonce=bShJlEJn5Ex5PBd6; _dd_s=rum=0&expire=1709880641775&logs=1&id=49832577-cf87-42b2-9bf5-5340bd28dbc7&created=1709879738255"));
        headers.put("host", createList("https://epsilon.6sense.com"));
        headers.put("orig-url", createList("/myservice/v3/company/details"));

        originalHttpRequest.setHeaders(headers);

        // TestConfigYamlParser parser = new TestConfigYamlParser();
        // TestConfig testConfig = parser.parseTemplate("OpenRedirect");
        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
        String message = "{\"method\":\"POST\",\"requestPayload\":\"[\\n  {\\n    \\\"id\\\": 0,\\n    \\\"username\\\": \\\"string\\\",\\n    \\\"firstName\\\": \\\"string\\\",\\n    \\\"lastName\\\": \\\"string\\\",\\n    \\\"email\\\": \\\"string\\\",\\n    \\\"password\\\": \\\"string\\\",\\n    \\\"phone\\\": \\\"string\\\",\\n    \\\"userStatus\\\": 0\\n  }\\n]\",\"responsePayload\":\"{\\\"code\\\":200,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":{\\\"role\\\": \\\"admin\\\", \\\"param2\\\": \\\"ankush\\\"}}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/user/createWithArray?user=1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"195\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:14:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"location\\\":\\\"oldHeaderVal\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327267\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        originalHttpResponse.buildFromSampleMessage(message);
        RawApi rawApi = new RawApi(originalHttpRequest, originalHttpResponse, null);
        rawApi.getRequest().getHeaders().put("host", Collections.singletonList("http://xyz.com"));
        return rawApi;
    }

    public static java.util.List<String> createList(String s) {
        java.util.List<String> ret = new ArrayList<>();
        ret.add(s);
        return ret;
    }

}