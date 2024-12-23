package com.akto.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.OtpTestDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.LoginFlowParams;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ApiWorkflowExecutorTest extends MongoBasedTest {

    @Test
    public void testCombineQueryParams2() {
        String query1 = "";
        String query2 = "blah";
        String combinedQuery = OriginalHttpRequest.combineQueryParams(query1, query2);
        assertEquals("blah", combinedQuery);
    }

    @Test
    public void testPopulateValuesMap() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();
        String payload = "{\"users\": [{\"name\": \"avneesh\", \"age\": 99}, {\"name\": \"ankush\", \"age\": 999} ], \"total\": 2, \"CEO\": {\"name\": \"Ankita\"}}";
        String nodeId = "x1";
        Map<String, List<String>> headers = new HashMap<>();
        String queryParams = "status=online";
        com.akto.testing.workflow_node_executor.Utils.populateValuesMap(valuesMap, payload, nodeId, headers, true, queryParams);

        assertEquals(8, valuesMap.size()); // 7 normal values + entire body string
        assertEquals("online", valuesMap.get("x1.request.query.status"));
        assertEquals("avneesh", valuesMap.get("x1.request.body.users[0].name"));
        assertEquals(99, valuesMap.get("x1.request.body.users[0].age"));
        assertEquals("ankush", valuesMap.get("x1.request.body.users[1].name"));
        assertEquals(999, valuesMap.get("x1.request.body.users[1].age"));
        assertEquals(2, valuesMap.get("x1.request.body.total"));
        assertEquals("Ankita", valuesMap.get("x1.request.body.CEO.name"));

        payload = "{\"company\": \"Akto\"}";
        headers.put("x-forwarded-for", Arrays.asList("ip1", "ip2"));
        com.akto.testing.workflow_node_executor.Utils.populateValuesMap(valuesMap, payload, nodeId, headers, false, null);

        assertEquals(11, valuesMap.size()); // 8 from earlier + 2 values + 1 body string (this time for isRequest false)
        assertEquals("Akto", valuesMap.get("x1.response.body.company"));
        assertEquals("ip1;ip2", valuesMap.get("x1.response.header.x-forwarded-for"));


        payload = "mobNo=999999999&Vehicle=Car";
        com.akto.testing.workflow_node_executor.Utils.populateValuesMap(valuesMap, payload, nodeId, new HashMap<>(),true, null);
        assertEquals(13, valuesMap.size()); // 11 + 2 new (no request.body because already filled)
        assertEquals("999999999", valuesMap.get("x1.request.body.mobNo"));
        assertEquals("Car", valuesMap.get("x1.request.body.Vehicle"));
    }

    @Test
    public void testBuildHttpRequest() throws Exception {
        String orig = "{\"method\":\"GET\",\"requestPayload\":\"{}\",\"responsePayload\":\"{\\\"sold\\\":7,\\\"string\\\":256,\\\"connector\\\":1,\\\"pending\\\":5,\\\"available\\\":679,\\\"verified\\\":1}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/store/inventory?random=hehe\",\"requestHeaders\":\"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:11:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327087\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String queryParams = "user=avneesh";
        String requestPayload = "{\"initials\": \"AH\"}";
        String requestUrl = "https://stud.akto.io/stud_${x1.response.body.id}";
        WorkflowUpdatedSampleData workflowUpdatedSampleData = new WorkflowUpdatedSampleData(
                orig, queryParams, null, requestPayload, requestUrl
        );

        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x1.response.body.id", 10);

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        OriginalHttpRequest originalHttpRequest = com.akto.testing.workflow_node_executor.Utils.buildHttpRequest(workflowUpdatedSampleData, valuesMap);

        assertEquals(originalHttpRequest.getQueryParams(), "user=avneesh");
        assertEquals(originalHttpRequest.getHeaders().size(), 11);
        assertEquals(originalHttpRequest.getBody(), requestPayload);
        assertEquals(originalHttpRequest.getUrl(), "https://stud.akto.io/stud_10");
    }

    private BasicDBObject generateValue(String host, String endpoint, String method) {
        BasicDBObject value = new BasicDBObject();
        value.put("host", host);
        value.put("url", endpoint);
        value.put("method", method);
        return value;
    }

    @Test
    public void testReplaceVariables() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();

        List<BasicDBObject> newSensitiveEndpoints = new ArrayList<>();
        newSensitiveEndpoints.add(generateValue("host1", "endpoint1", "GET"));
        newSensitiveEndpoints.add(generateValue("host2", "endpoint2", "GET"));
        newSensitiveEndpoints.add(generateValue("host3", "endpoint3", "POST"));

        List<BasicDBObject> newEndpoints = new ArrayList<>();
        newEndpoints.add(generateValue("host4", "endpoint1", "GET"));
        newEndpoints.add(generateValue("host5", "endpoint2", "GET"));

        valuesMap.put("AKTO.changes_info.newSensitiveEndpoints", newSensitiveEndpoints);
        valuesMap.put("AKTO.changes_info.newSensitiveEndpointsCount", 139);
        valuesMap.put("AKTO.changes_info.newEndpoints", newEndpoints);
        valuesMap.put("AKTO.changes_info.newEndpointsCount", 2088);
        valuesMap.put("AKTO.changes_info.newSensitiveParametersCount", 305);
        valuesMap.put("AKTO.changes_info.newParametersCount", 0);

        String body = "{" +
                "\"newSensitiveEndpoints\" : ${AKTO.changes_info.newSensitiveEndpoints}," +
                " \"newSensitiveEndpointsCount\" : ${AKTO.changes_info.newSensitiveEndpointsCount}," +
                " \"newEndpoints\" : ${AKTO.changes_info.newEndpoints}," +
                " \"newEndpointsCount\" : ${AKTO.changes_info.newEndpointsCount}," +
                " \"newSensitiveParametersCount\" : ${AKTO.changes_info.newSensitiveParametersCount}," +
                " \"newParametersCount\" : ${AKTO.changes_info.newParametersCount}" +
                "}";

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        String payload = com.akto.testing.workflow_node_executor.Utils.replaceVariables(body, valuesMap, false, true);

        BasicDBObject payloadObject = BasicDBObject.parse(payload);

        assertEquals( 139 ,payloadObject.get("newSensitiveEndpointsCount"));
        assertEquals( 2088 ,payloadObject.get("newEndpointsCount"));
        assertEquals( 305,payloadObject.get("newSensitiveParametersCount"));
        assertEquals( 0,payloadObject.get("newParametersCount"));

        BasicDBList basicDBList = (BasicDBList) payloadObject.get("newEndpoints");
        assertEquals(2, basicDBList.size());

        basicDBList = (BasicDBList) payloadObject.get("newSensitiveEndpoints");
        assertEquals(3, basicDBList.size());

    }

    @Test
    public void testConstructValueMapWithLoginParams() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuemap = com.akto.testing.workflow_node_executor.Utils.constructValueMap(null);
        assertEquals(0, valuemap.size());
    }

    @Test
    public void testConstructValueMapFetchValueMapFalse() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuemap = com.akto.testing.workflow_node_executor.Utils.constructValueMap(new LoginFlowParams(23, false, "x1"));
        assertEquals(0, valuemap.size());
    }

    @Test
    public void testProcessOtpNodeOtpDataMissing() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        WorkflowNodeDetails nodeDetails = new WorkflowNodeDetails(0, "", null, "", null, null, false,
        0, 0, 0, "(/d+){1,6}", "123");
        Map<String, Object> valuemap = new HashMap<>();
        Node node = new Node("x1", nodeDetails);
        NodeResult result = com.akto.testing.workflow_node_executor.Utils.processOtpNode(node, valuemap);
        assertEquals("{\"response\": {\"body\": {\"error\": \"otp data not received for uuid 123\"}}}", result.getMessage());
    }

    @Test
    public void testProcessOtpNodeDataStaleData() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        WorkflowNodeDetails nodeDetails = new WorkflowNodeDetails(0, "", null, "", null, null, false,
        0, 0, 0, "(/d+){1,6}", "123");
        Map<String, Object> valuemap = new HashMap<>();
        Node node = new Node("x1", nodeDetails);

        int curTime = Context.now() - 10 * 60;
        Bson updates = Updates.combine(
                Updates.set("otpText", "Your otp is 123456"),
                Updates.set("createdAtEpoch", curTime)
        );

        OtpTestDataDao.instance.updateOne(Filters.eq("uuid", "123"), updates); 
        
        NodeResult result = com.akto.testing.workflow_node_executor.Utils.processOtpNode(node, valuemap);
        assertEquals("{\"response\": {\"body\": {\"error\": \"otp data not received for uuid 123\"}}}", result.getMessage());
    }

    @Test
    public void testProcessOtpNodeDataInvalidRegex() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        WorkflowNodeDetails nodeDetails = new WorkflowNodeDetails(0, "", null, "", null, null, false,
        0, 0, 0, "(/d+){1,6}", "124");
        Map<String, Object> valuemap = new HashMap<>();
        Node node = new Node("x1", nodeDetails);

        int curTime = Context.now();
        Bson updates = Updates.combine(
                Updates.set("otpText", "Your otp is 123456"),
                Updates.set("createdAtEpoch", curTime)
        );

        OtpTestDataDao.instance.updateOne(Filters.eq("uuid", "124"), updates); 
        
        NodeResult result = com.akto.testing.workflow_node_executor.Utils.processOtpNode(node, valuemap);
        assertEquals("{\"response\": {\"body\": {\"error\": \"unable to extract otp for provided regex\"}}}", result.getMessage());
    }

    @Test
    public void testProcessOtpNodeData() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        WorkflowNodeDetails nodeDetails = new WorkflowNodeDetails(0, "", null, "", null, null, false,
        0, 0, 0, "(\\d+){1,6}", "125");
        Map<String, Object> valuemap = new HashMap<>();
        Node node = new Node("x1", nodeDetails);

        int curTime = Context.now();
        Bson updates = Updates.combine(
                Updates.set("otpText", "Your otp is 123456"),
                Updates.set("createdAtEpoch", curTime)
        );

        OtpTestDataDao.instance.updateOne(Filters.eq("uuid", "125"), updates); 
        
        NodeResult result = com.akto.testing.workflow_node_executor.Utils.processOtpNode(node, valuemap);
        assertEquals(0, result.getErrors().size());
        assertEquals("{\"response\": {\"body\": {\"otp\": \"123456\", \"otpText\": \"Your otp is 123456\"}}}", result.getMessage());
    }
}
