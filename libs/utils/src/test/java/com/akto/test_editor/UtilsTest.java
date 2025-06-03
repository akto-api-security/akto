package com.akto.test_editor;

import com.akto.dto.RawApi;
import com.akto.dto.OriginalHttpRequest;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testBuildRequestIHttpFormat() {
        // Setup request
        String method = "POST";
        String url = "https://example.com/api";
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
        headers.put("Authorization", Arrays.asList("Bearer token123"));
        String body = "{\"foo\":\"bar\"}";

        OriginalHttpRequest req = new OriginalHttpRequest();
        req.setMethod(method);
        req.setUrl(url);
        req.setHeaders(headers);
        req.setBody(body);

        RawApi rawApi = new RawApi();
        rawApi.setRequest(req);

        String expected = "POST https://example.com/api\n" +
                "Content-Type: application/json\n" +
                "Authorization: Bearer token123\n" +
                "\n{\"foo\":\"bar\"}";

        String actual = Utils.buildRequestIHttpFormat(rawApi).trim();
        assertEquals(expected, actual);
    }
}
