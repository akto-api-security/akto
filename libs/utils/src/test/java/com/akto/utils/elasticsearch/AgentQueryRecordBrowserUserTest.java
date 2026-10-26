package com.akto.utils.elasticsearch;

import static org.junit.jupiter.api.Assertions.*;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AgentQueryRecordBrowserUserTest {

    private static final Map<String, String> DEVICE_USERS =
            Collections.singletonMap("rohan", "rohan@akto.io");

    private static HttpResponseParams browserRequest() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList("rohan.chrome.claude.ai"));
        HttpRequestParams req = new HttpRequestParams();
        req.setUrl("/api/chat");
        req.setHeaders(headers);
        req.setPayload("{\"body\":\"hi\"}");
        HttpResponseParams resp = new HttpResponseParams();
        resp.setRequestParams(req);
        resp.setPayload("{\"body\":\"ok\"}");
        return resp;
    }

    private static String userFor(String browserUserEmail) {
        Map<String, String> tags = new HashMap<>();
        tags.put("browser-llm", "Browser LLM");
        if (browserUserEmail != null) tags.put("browser-user-email", browserUserEmail);
        return AgentQueryRecord.fromHttpResponseParams(browserRequest(), tags, DEVICE_USERS).getUserName();
    }

    @Test
    public void browserUserEmailTagWins() {
        assertEquals("richie.nprieto@gmail.com", userFor("richie.nprieto@gmail.com"));
    }

    @Test
    public void missingOrInvalidTagFallsBackToProfileIdentity() {
        assertEquals("rohan@akto.io", userFor(null));
        assertEquals("rohan@akto.io", userFor("null"));
        assertEquals("rohan@akto.io", userFor(""));
    }
}
