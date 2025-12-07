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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

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
    public void testExecuteCode() throws Exception {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x1.response.body.user.name", "avneesh");
        valuesMap.put("x1.response.body.user.age", 99);

        String payload = "{\"user_name\": \"${x1.response.body.user.name}\", \"user_age\": ${x1.response.body.user.age}}";
        String result = com.akto.testing.workflow_node_executor.Utils.executeCode(payload, valuesMap);
        assertEquals("{\"user_name\": \"avneesh\", \"user_age\": 99}", result);


        payload = "{\"user_name\": '#[\"${x1.response.body.user.name}\".toUpperCase()]#', \"user_age\": #[${x1.response.body.user.age} + 1]#}";
        result = com.akto.testing.workflow_node_executor.Utils.executeCode(payload, valuesMap);
        assertEquals("{\"user_name\": 'AVNEESH', \"user_age\": 100}", result);

        valuesMap.put("x1.response.body.url", "https://api.razorpay.com:443/v1/payments/pay_K6FMfsnyloigxs/callback/941349c12d0e001436ace03ee711367413b176bb/rzp_test_1DP5mmOlF5G5ag");

        String urlPayload = "#[ var a = '${x1.response.body.url}'; var b = a.split('/'); b[5] = 'avneesh'; b.join('/'); ]#";
        result = com.akto.testing.workflow_node_executor.Utils.executeCode(urlPayload, valuesMap);
        assertEquals("https://api.razorpay.com:443/v1/payments/avneesh/callback/941349c12d0e001436ace03ee711367413b176bb/rzp_test_1DP5mmOlF5G5ag", result);

        urlPayload = "#[ '${x1.response.body.url}'.replace(new RegExp('pay_.*?/'), 'avneesh/') ]#";
        result = com.akto.testing.workflow_node_executor.Utils.executeCode(urlPayload, valuesMap);
        assertEquals("https://api.razorpay.com:443/v1/payments/avneesh/callback/941349c12d0e001436ace03ee711367413b176bb/rzp_test_1DP5mmOlF5G5ag", result);

        valuesMap.put("x2.response.body.sentence", "This is sentence with a 'random' quote");
        payload = "#[ '${x2.response.body.sentence}'.replace(new RegExp('random'), 'avneesh') ]#";
        result = com.akto.testing.workflow_node_executor.Utils.executeCode(payload, valuesMap);
        assertEquals("This is sentence with a 'avneesh' quote", result);

    }

    @Test
    public void testPopulateValuesMap() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();
        String payload = "{\"users\": [{\"name\": \"avneesh\", \"age\": 99}, {\"name\": \"ankush\", \"age\": 999} ], \"total\": 2, \"CEO\": {\"name\": \"Ankita\"}}";
        String nodeId = "x1";
        Map<String, List<String>> headers = new HashMap<>();
        String queryParams = "status=online";
        Utils.populateValuesMap(valuesMap, payload, nodeId, headers, true, queryParams);

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
        Utils.populateValuesMap(valuesMap, payload, nodeId, headers, false, null);

        assertEquals(11, valuesMap.size()); // 8 from earlier + 2 values + 1 body string (this time for isRequest false)
        assertEquals("Akto", valuesMap.get("x1.response.body.company"));
        assertEquals("ip1;ip2", valuesMap.get("x1.response.header.x-forwarded-for"));


        payload = "mobNo=999999999&Vehicle=Car";
        Utils.populateValuesMap(valuesMap, payload, nodeId, new HashMap<>(),true, null);
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


    @Test
    public void testValidateTest() {
        ScriptEngineManager factory = new ScriptEngineManager();
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();

        String testValidatorCode = "${x1.response.status_code} === 200";
        valuesMap.put("x1.response.status_code", 401);
        boolean vulnerable = validateTest(testValidatorCode, valuesMap);
        assertTrue(vulnerable);

        testValidatorCode = "'${x1.request.body.user_name}' === '${x1.response.body.user_name}'";
        valuesMap.put("x1.request.body.user_name", "Avneesh");
        valuesMap.put("x1.response.body.user_name", "Ankush");
        vulnerable = validateTest(testValidatorCode, valuesMap);
        assertTrue(vulnerable);

        testValidatorCode = "'${x1.request.body.CTO}' === 'Ankush'";
        valuesMap.put("x1.request.body.CTO", "Ankush");
        vulnerable = validateTest(testValidatorCode, valuesMap);
        assertFalse(vulnerable);

        String p = "<!doctype html><html style=\"height:100%\"><head><title>Razorpay - Payment in progress</title> <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"> <meta charset=\"utf-8\"> <meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\"><style> body{background:#fff;font-family:ubuntu,helvetica,verdana,sans-serif;margin:0;padding:0;width:100%;height:100%;text-align:center;display:table} #text{vertical-align: middle; display: none; text-transform: uppercase; font-weight: bold; font-size: 30px; line-height: 40px} #icon{font-size: 60px;color: #fff; border-radius: 50%; width: 80px; height: 80px; line-height: 80px; margin: -60px auto 20px; display: inline-block} #text.show{display:table-cell} #text.s{color:#61BC6D;} #text.s #icon{background:#61BC6D} #text.f{color:#EF6050;} #text.f #icon{background:#EF6050} #delayed-prompt {position: fixed; top:70%; left: 0; right: 0;} .text {transition: 0.2s opacity; position: absolute; top: 0; width: 100%; opacity: 0; transition-delay: 0.2s;} .show-early .early, .show-late .late {opacity: 1} .show-early .late, .show-late .early {opacity: 0} #proceed-btn {color: #528ff0; text-decoration: underline; cursor: pointer; -webkit-tap-highlight-color: transparent;} </style> <meta name=\"viewport\" content=\"user-scalable=no,width=device-width,initial-scale=1,maximum-scale=1\"> </head><body> <div id=\"text\"><div id=\"icon\"></div><br>Payment<br> </div> <div id=\"delayed-prompt\"> <div class=\"early text\">Redirecting...</div> <div class=\"late text\" id=\"proceed-btn\">Click here to proceed</div> </div> <script> // Callback data // var data = {\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Payment failed\",\"source\":\"gateway\",\"step\":\"payment_authorization\",\"reason\":\"payment_failed\",\"metadata\":{\"payment_id\":\"pay_KCyZAQ3s1TQDHq\"}},\"http_status_code\":400}; // Callback data // var s = 'razorpay_payment_id' in data; data = JSON.stringify(data); if (window.CheckoutBridge) { if (typeof CheckoutBridge.oncomplete == 'function') { function onComplete() { CheckoutBridge.oncomplete(data); } setTimeout(onComplete, 30); setTimeout(function () { g('delayed-prompt').classList.add('show-early'); }, 500); setTimeout(function () { g('delayed-prompt').classList.add('show-late'); g('delayed-prompt').classList.remove('show-early'); }, 2000); g('proceed-btn').onclick = onComplete; } } else { document.cookie = 'onComplete=' + data + ';expires=Fri, 31 Dec 9999 23:59:59 GMT;path=/'; try { localStorage.setItem('onComplete', data); } catch (e) {} } var iosCheckoutBridgeNew = ((window.webkit || {}).messageHandlers || {}) .CheckoutBridge; if (iosCheckoutBridgeNew) { iosCheckoutBridgeNew.postMessage({ action: 'success', body: JSON.parse(data) }); } function g(id) { return document.getElementById(id); } function razorpay_callback() { return data; } var t = g('text'); t.innerHTML += s ? 'Successful' : 'Failed'; t.className = 'show ' + (s ? 's' : 'f'); g('icon').innerHTML = s ? '&#10004' : '!'; if (!window.CheckoutBridge) { try { window.opener.onComplete(data) } catch(e){} try { (window.opener || window.parent).postMessage(data, '*') } catch(e){} setTimeout(close, 999); } </script></body></html>";
        testValidatorCode = "'${x1.response.body}'.indexOf('\"error\"') < 0";
        valuesMap.put("x1.response.body", p);
        vulnerable = validateTest(testValidatorCode, valuesMap);
        assertTrue(vulnerable);
    }

    public boolean validateTest(String testValidatorCode, Map<String, Object> valuesMap) {
        ScriptEngineManager factory = new ScriptEngineManager();
        if (testValidatorCode == null) return false;
        testValidatorCode = testValidatorCode.trim();

        boolean vulnerable = false;
        if (testValidatorCode.length() == 0) return false;

        ScriptEngine engine = factory.getEngineByName("nashorn");
        try {
            String code = com.akto.testing.workflow_node_executor.Utils.replaceVariables(testValidatorCode, valuesMap, true);
            Object o = engine.eval(code);
            vulnerable = ! (boolean) o;
        } catch (Exception e) {
            ;
        }

        return vulnerable;
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
        String payload = com.akto.testing.workflow_node_executor.Utils.replaceVariables(body, valuesMap, false);

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
