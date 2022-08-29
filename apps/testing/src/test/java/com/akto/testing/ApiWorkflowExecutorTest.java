package com.akto.testing;

import com.akto.dto.type.RequestTemplate;
import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
