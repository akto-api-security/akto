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
    public void testCombineQueryParams1() {
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
    public void testCombineQueryParams2() {
        String query1 = "";
        String query2 = "blah";
        String combinedQuery = new ApiWorkflowExecutor().combineQueryParams(query1, query2);
        assertEquals("blah", combinedQuery);
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

        valuesMap.put("x2.response.body.sentence", "This is sentence with a 'random' quote");
        payload = "#[ '${x2.response.body.sentence}'.replace(new RegExp('random'), 'avneesh') ]#";
        result = apiWorkflowExecutor.executeCode(payload, valuesMap);
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
        apiWorkflowExecutor.populateValuesMap(valuesMap, payload, nodeId, headers, true, queryParams);

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
        apiWorkflowExecutor.populateValuesMap(valuesMap, payload, nodeId, headers, false, null);

        assertEquals(11, valuesMap.size()); // 8 from earlier + 2 values + 1 body string (this time for isRequest false)
        assertEquals("Akto", valuesMap.get("x1.response.body.company"));
        assertEquals("ip2", valuesMap.get("x1.response.header.x-forwarded-for"));


        payload = "mobNo=999999999&Vehicle=Car";
        apiWorkflowExecutor.populateValuesMap(valuesMap, payload, nodeId, new HashMap<>(),true, null);
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
        OriginalHttpRequest originalHttpRequest = apiWorkflowExecutor.buildHttpRequest(workflowUpdatedSampleData, valuesMap);

        assertEquals(originalHttpRequest.getQueryParams(), "random=hehe&user=avneesh");
        assertEquals(originalHttpRequest.getHeaders().size(), 11);
        assertEquals(originalHttpRequest.getBody(), requestPayload);
        assertEquals(originalHttpRequest.getUrl(), "https://stud.akto.io/stud_10");
    }


    @Test
    public void testValidateTest() {
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        Map<String, Object> valuesMap = new HashMap<>();

        String testValidatorCode = "${x1.response.status_code} === 200";
        valuesMap.put("x1.response.status_code", 401);
        boolean vulnerable = apiWorkflowExecutor.validateTest(testValidatorCode, valuesMap);
        assertTrue(vulnerable);

        testValidatorCode = "'${x1.request.body.user_name}' === '${x1.response.body.user_name}'";
        valuesMap.put("x1.request.body.user_name", "Avneesh");
        valuesMap.put("x1.response.body.user_name", "Ankush");
        vulnerable = apiWorkflowExecutor.validateTest(testValidatorCode, valuesMap);
        assertTrue(vulnerable);

        testValidatorCode = "'${x1.request.body.CTO}' === 'Ankush'";
        valuesMap.put("x1.request.body.CTO", "Ankush");
        vulnerable = apiWorkflowExecutor.validateTest(testValidatorCode, valuesMap);
        assertFalse(vulnerable);

        String p = "<!doctype html><html style=\"height:100%\"><head><title>Razorpay - Payment in progress</title> <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"> <meta charset=\"utf-8\"> <meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\"><style> body{background:#fff;font-family:ubuntu,helvetica,verdana,sans-serif;margin:0;padding:0;width:100%;height:100%;text-align:center;display:table} #text{vertical-align: middle; display: none; text-transform: uppercase; font-weight: bold; font-size: 30px; line-height: 40px} #icon{font-size: 60px;color: #fff; border-radius: 50%; width: 80px; height: 80px; line-height: 80px; margin: -60px auto 20px; display: inline-block} #text.show{display:table-cell} #text.s{color:#61BC6D;} #text.s #icon{background:#61BC6D} #text.f{color:#EF6050;} #text.f #icon{background:#EF6050} #delayed-prompt {position: fixed; top:70%; left: 0; right: 0;} .text {transition: 0.2s opacity; position: absolute; top: 0; width: 100%; opacity: 0; transition-delay: 0.2s;} .show-early .early, .show-late .late {opacity: 1} .show-early .late, .show-late .early {opacity: 0} #proceed-btn {color: #528ff0; text-decoration: underline; cursor: pointer; -webkit-tap-highlight-color: transparent;} </style> <meta name=\"viewport\" content=\"user-scalable=no,width=device-width,initial-scale=1,maximum-scale=1\"> </head><body> <div id=\"text\"><div id=\"icon\"></div><br>Payment<br> </div> <div id=\"delayed-prompt\"> <div class=\"early text\">Redirecting...</div> <div class=\"late text\" id=\"proceed-btn\">Click here to proceed</div> </div> <script> // Callback data // var data = {\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Payment failed\",\"source\":\"gateway\",\"step\":\"payment_authorization\",\"reason\":\"payment_failed\",\"metadata\":{\"payment_id\":\"pay_KCyZAQ3s1TQDHq\"}},\"http_status_code\":400}; // Callback data // var s = 'razorpay_payment_id' in data; data = JSON.stringify(data); if (window.CheckoutBridge) { if (typeof CheckoutBridge.oncomplete == 'function') { function onComplete() { CheckoutBridge.oncomplete(data); } setTimeout(onComplete, 30); setTimeout(function () { g('delayed-prompt').classList.add('show-early'); }, 500); setTimeout(function () { g('delayed-prompt').classList.add('show-late'); g('delayed-prompt').classList.remove('show-early'); }, 2000); g('proceed-btn').onclick = onComplete; } } else { document.cookie = 'onComplete=' + data + ';expires=Fri, 31 Dec 9999 23:59:59 GMT;path=/'; try { localStorage.setItem('onComplete', data); } catch (e) {} } var iosCheckoutBridgeNew = ((window.webkit || {}).messageHandlers || {}) .CheckoutBridge; if (iosCheckoutBridgeNew) { iosCheckoutBridgeNew.postMessage({ action: 'success', body: JSON.parse(data) }); } function g(id) { return document.getElementById(id); } function razorpay_callback() { return data; } var t = g('text'); t.innerHTML += s ? 'Successful' : 'Failed'; t.className = 'show ' + (s ? 's' : 'f'); g('icon').innerHTML = s ? '&#10004' : '!'; if (!window.CheckoutBridge) { try { window.opener.onComplete(data) } catch(e){} try { (window.opener || window.parent).postMessage(data, '*') } catch(e){} setTimeout(close, 999); } </script></body></html>";
        testValidatorCode = "'${x1.response.body}'.indexOf('\"error\"') < 0";
        valuesMap.put("x1.response.body", p);
        vulnerable = apiWorkflowExecutor.validateTest(testValidatorCode, valuesMap);
        assertTrue(vulnerable);
    }

    @Test
    public void testReplaceVariables() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();

        valuesMap.put("AKTO.changes_info.newSensitiveEndpoints", 123);
        valuesMap.put("AKTO.changes_info.newEndpoints", 234);
        valuesMap.put("AKTO.changes_info.newSensitiveParameters",345);
        valuesMap.put("AKTO.changes_info.newParameters",456);

        valuesMap.put("x1.response.body.name","Avneesh");

        String body = "{\"newSensitiveEndpoints\" : ${AKTO.changes_info.newSensitiveEndpoints}, \"name\" : \"${x1.response.body.name}\"}";
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        String payload = apiWorkflowExecutor.replaceVariables(body, valuesMap);

        assertEquals("{\"newSensitiveEndpoints\" : 123, \"name\" : \"Avneesh\"}", payload );


        body = "There are {${AKTO.changes_info.newParameters}} new $parameters and ${x}";
        payload = apiWorkflowExecutor.replaceVariables(body, valuesMap);
        assertEquals("There are {456} new $parameters and ${x}", payload );

    }
}
