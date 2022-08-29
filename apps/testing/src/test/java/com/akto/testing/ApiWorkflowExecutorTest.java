package com.akto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.type.RequestTemplate;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ApiWorkflowExecutorTest {

    @Test
    public void testCombineQueryParams() {
        String query1 = "user=avneesh&age=99&favColour=blue&status=all_is_well";
        String query2 = "status=blah%20blah&age=101";
        String combinedQuery = new ApiWorkflowExecutor().combineQueryParams(query1, query2);
        assertTrue(combinedQuery.contains("status=blah%20blah"));

        BasicDBObject combinedQueryObject = RequestTemplate.getQueryJSON("google.com?"+combinedQuery);

        assertEquals("avneesh", combinedQueryObject.get("user"));
        assertEquals("101", combinedQueryObject.get("age"));
        assertEquals("blue", combinedQueryObject.get("favColour"));
        assertEquals("blah blah", combinedQueryObject.get("status"));
    }

    @Test
    public void testExecuteCode() throws Exception {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x1.response.body.user.name", "avneesh");
        valuesMap.put("x1.response.body.user.age", 99);

        String payload = "{\"user_name\": \"${x1.response.body.user.name}\", \"user_age\": ${x1.response.body.user.age}}";
        String result = apiWorkflowExecutor.executeCode(payload, valuesMap);
        assertEquals("{\"user_name\": \"avneesh\", \"user_age\": 99}", result);


        payload = "{\"user_name\": '#[\"${x1.response.body.user.name}\".toUpperCase()]#', \"user_age\": #[${x1.response.body.user.age} + 1]#}";
        result = apiWorkflowExecutor.executeCode(payload, valuesMap);
        assertEquals("{\"user_name\": 'AVNEESH', \"user_age\": 100}", result);

        valuesMap.put("x1.response.body.url", "https://api.razorpay.com:443/v1/payments/pay_K6FMfsnyloigxs/callback/941349c12d0e001436ace03ee711367413b176bb/rzp_test_1DP5mmOlF5G5ag");

        String urlPayload = "#[ var a = '${x1.response.body.url}'; var b = a.split('/'); b[5] = 'avneesh'; b.join('/'); ]#";
        result = apiWorkflowExecutor.executeCode(urlPayload, valuesMap);
        assertEquals("https://api.razorpay.com:443/v1/payments/avneesh/callback/941349c12d0e001436ace03ee711367413b176bb/rzp_test_1DP5mmOlF5G5ag", result);

        urlPayload = "#[ '${x1.response.body.url}'.replace(new RegExp('pay_.*?/'), 'avneesh/') ]#";
        result = apiWorkflowExecutor.executeCode(urlPayload, valuesMap);
        assertEquals("https://api.razorpay.com:443/v1/payments/avneesh/callback/941349c12d0e001436ace03ee711367413b176bb/rzp_test_1DP5mmOlF5G5ag", result);
    }


    @Test
    public void testPopulateValuesMap() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();
        String payload = "{\"users\": [{\"name\": \"avneesh\", \"age\": 99}, {\"name\": \"ankush\", \"age\": 999} ], \"total\": 2, \"CEO\": {\"name\": \"Ankita\"}}";
        String nodeId = "x1";
        Map<String, List<String>> headers = new HashMap<>();
        String queryParams = "status=online";
        apiWorkflowExecutor.populateValuesMap(valuesMap, payload, nodeId, headers, true, queryParams);

        assertEquals(7, valuesMap.size());
        assertEquals("online", valuesMap.get("x1.request.query.status"));
        assertEquals("avneesh", valuesMap.get("x1.request.body.users[0].name"));
        assertEquals(99, valuesMap.get("x1.request.body.users[0].age"));
        assertEquals("ankush", valuesMap.get("x1.request.body.users[1].name"));
        assertEquals(999, valuesMap.get("x1.request.body.users[1].age"));
        assertEquals(2, valuesMap.get("x1.request.body.total"));
        assertEquals("Ankita", valuesMap.get("x1.request.body.CEO.name"));

        payload = "{\"company\": \"Akto\"}";
        headers.put("x-forwarded-for", Arrays.asList("ip1", "ip2"));
        apiWorkflowExecutor.populateValuesMap(valuesMap, payload, nodeId, headers, false, null);

        assertEquals(9, valuesMap.size());
        assertEquals("Akto", valuesMap.get("x1.response.body.company"));
        assertEquals("ip2", valuesMap.get("x1.response.header.x-forwarded-for"));


        payload = "mobNo=999999999&Vehicle=Car";
        apiWorkflowExecutor.populateValuesMap(valuesMap, payload, nodeId, new HashMap<>(),true, null);
        assertEquals(11, valuesMap.size());
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
        OriginalHttpRequest originalHttpRequest = apiWorkflowExecutor.buildHttpRequest(workflowUpdatedSampleData, valuesMap);

        assertEquals(originalHttpRequest.getQueryParams(), "random=hehe&user=avneesh");
        assertEquals(originalHttpRequest.getHeaders().size(), 11);
        assertEquals(originalHttpRequest.getBody(), requestPayload);
        assertEquals(originalHttpRequest.getUrl(), "https://stud.akto.io/stud_10");
    }
}
