package com.akto.dto;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestOriginalHttpResponse {

    @Test
    public void testBuildFromSampleMessage() {
        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse();
        String message = "{\"method\":\"POST\",\"requestPayload\":\"[\\n  {\\n    \\\"id\\\": 0,\\n    \\\"username\\\": \\\"string\\\",\\n    \\\"firstName\\\": \\\"string\\\",\\n    \\\"lastName\\\": \\\"string\\\",\\n    \\\"email\\\": \\\"string\\\",\\n    \\\"password\\\": \\\"string\\\",\\n    \\\"phone\\\": \\\"string\\\",\\n    \\\"userStatus\\\": 0\\n  }\\n]\",\"responsePayload\":\"{\\\"code\\\":200,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":\\\"ok\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1661807253\",\"path\":\"https://petstore.swagger.io/v2/user/createWithArray?user=1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"195\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Tue, 04 Jan 2022 20:14:27 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327267\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        originalHttpResponse.buildFromSampleMessage(message);

        assertEquals(200, originalHttpResponse.getStatusCode());
        assertEquals(7, originalHttpResponse.getHeaders().size());
        assertEquals("{\"code\":200,\"type\":\"unknown\",\"message\":\"ok\"}", originalHttpResponse.getBody());


    }

    @Test
    public void testCopy() {
        int statusCode = 201;
        String body = "{}";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("something", Collections.singletonList("value"));

        OriginalHttpResponse originalHttpResponse = new OriginalHttpResponse(
                body, headers, statusCode
        );

        OriginalHttpResponse copyOriginalHttpResponse= originalHttpResponse.copy();

        assertEquals(body, copyOriginalHttpResponse.getBody());
        assertEquals(headers, copyOriginalHttpResponse.getHeaders());
        assertEquals(statusCode, copyOriginalHttpResponse.getStatusCode());

    }
}
