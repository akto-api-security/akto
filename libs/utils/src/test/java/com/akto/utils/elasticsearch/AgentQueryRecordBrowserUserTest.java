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
            Collections.singletonMap("jdoe", "jdoe@example.com");

    private static HttpResponseParams browserRequest() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList("jdoe.chrome.claude.ai"));
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
        assertEquals("personal.user@example.com", userFor("personal.user@example.com"));
    }

    @Test
    public void missingOrInvalidTagFallsBackToProfileIdentity() {
        assertEquals("jdoe@example.com", userFor(null));
        assertEquals("jdoe@example.com", userFor("null"));
        assertEquals("jdoe@example.com", userFor(""));
    }
}
