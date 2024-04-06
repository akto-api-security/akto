package com.akto.parsers;

import com.akto.dto.HttpResponseParams;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KafkaParserTest {
    private final static Gson gson = new Gson();

    @Test
    public void testHappyPath() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.5\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Cookie\\\":[\\\"G_ENABLED_IDPS=google\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Sec-Fetch-Dest\\\":[\\\"document\\\"],\\\"Sec-Fetch-Mode\\\":[\\\"navigate\\\"],\\\"Sec-Fetch-Site\\\":[\\\"cross-site\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:94.0) Gecko/20100101 Firefox/94.0\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"[{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}},{\\\"id\\\":\\\"2\\\",\\\"isbn\\\":\\\"2323\\\",\\\"title\\\":\\\"Book 2\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Ankush\\\",\\\"lastname\\\":\\\"Jain\\\"}}]\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            assertEquals("/api/books",httpResponseParams.getRequestParams().getURL());
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);
        assertEquals(1, httpResponseParams.getHeaders().size());
        assertEquals(12, httpResponseParams.requestParams.getHeaders().size());

    }

    @Test
    public void testEmptyHeader() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"[{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}},{\\\"id\\\":\\\"2\\\",\\\"isbn\\\":\\\"2323\\\",\\\"title\\\":\\\"Book 2\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Ankush\\\",\\\"lastname\\\":\\\"Jain\\\"}}]\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            assertEquals(0,httpResponseParams.getRequestParams().getHeaders().keySet().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);

    }

    @Test
    public void testPostQueryString() {
        String message = "{\"method\":\"POST\",\"requestPayload\":\"name=a&status=available\",\"responsePayload\":\"{\\\"code\\\":200,\\\"type\\\":\\\"unknown\\\",\\\"message\\\":\\\"4\\\"}\",\"ip\":\"null\",\"type\":\"HTTP/2\",\"path\":\"https://petstore.swagger.io/v2/pet/4\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"https://petstore.swagger.io\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"23\\\",\\\"Content-Type\\\":\\\"application/x-www-form-urlencoded\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 03 Jan 2022 07:18:20 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641194300\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(message);
            String payload = httpResponseParams.getRequestParams().getPayload();
            Map<String, Object> json = gson.fromJson(payload, Map.class);
            System.out.println(json);
            assertEquals("a", json.get("name"));
            assertEquals("available", json.get("status"));

        } catch (Exception e) {
            e.printStackTrace();
        }
        assertNotNull(httpResponseParams);

    }
}
