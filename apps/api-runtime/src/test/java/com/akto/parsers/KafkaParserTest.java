package com.akto.parsers;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaParserTest {

    @Test
    public void testHappyPath() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{\\\"Accept\\\":[\\\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8\\\"],\\\"Accept-Encoding\\\":[\\\"gzip, deflate\\\"],\\\"Accept-Language\\\":[\\\"en-US,en;q=0.5\\\"],\\\"Cache-Control\\\":[\\\"no-cache\\\"],\\\"Connection\\\":[\\\"keep-alive\\\"],\\\"Cookie\\\":[\\\"G_ENABLED_IDPS=google\\\"],\\\"Pragma\\\":[\\\"no-cache\\\"],\\\"Sec-Fetch-Dest\\\":[\\\"document\\\"],\\\"Sec-Fetch-Mode\\\":[\\\"navigate\\\"],\\\"Sec-Fetch-Site\\\":[\\\"cross-site\\\"],\\\"Upgrade-Insecure-Requests\\\":[\\\"1\\\"],\\\"User-Agent\\\":[\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:94.0) Gecko/20100101 Firefox/94.0\\\"]}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"[{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}},{\\\"id\\\":\\\"2\\\",\\\"isbn\\\":\\\"2323\\\",\\\"title\\\":\\\"Book 2\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Ankush\\\",\\\"lastname\\\":\\\"Jain\\\"}}]\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";
        HttpCallParser.HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(message);

        assertEquals("/api/books",httpResponseParams.getRequestParams().getURL());
    }

    @Test
    public void testEmptyHeader() {
        String message = "{\"akto_account_id\":\"1111\",\"contentType\":\"application/json;charset=utf-8\",\"ip\":\"127.0.0.1:48940\",\"method\":\"GET\",\"path\":\"/api/books\",\"requestHeaders\":\"{}\",\"requestPayload\":\"\",\"responseHeaders\":\"{\\\"Content-Type\\\":[\\\"application/json;charset=utf-8\\\"]}\",\"responsePayload\":\"[{\\\"id\\\":\\\"1\\\",\\\"isbn\\\":\\\"3223\\\",\\\"title\\\":\\\"Book 1\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Avneesh\\\",\\\"lastname\\\":\\\"Hota\\\"}},{\\\"id\\\":\\\"2\\\",\\\"isbn\\\":\\\"2323\\\",\\\"title\\\":\\\"Book 2\\\",\\\"author\\\":{\\\"firstname\\\":\\\"Ankush\\\",\\\"lastname\\\":\\\"Jain\\\"}}]\\n\",\"status\":\"null\",\"statusCode\":\"201\",\"time\":\"1638223603\",\"type\":\"HTTP/1.1\"}";
        HttpCallParser.HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(message);

        assertEquals(0,httpResponseParams.getRequestParams().getHeaders().keySet().size());

    }
}
