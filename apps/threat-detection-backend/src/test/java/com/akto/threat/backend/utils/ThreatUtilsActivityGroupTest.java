package com.akto.threat.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ThreatUtilsActivityGroupTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ARN = "arn:aws:sts::[REDACTED]:assumed-role/aria-usertask-role/b4ad0a7da8704a9d84c9d4806b4e0c60";
    private static final String PROMPT = "{\"messages\":[{\"role\":\"user\",\"content\":\"i dont see the execution is browserstack\"}]}";

    @Test
    public void sameCallerAndPrompt_sameKey() throws Exception {
        String first = ThreatUtils.activityGroupKey(envelope(ARN, PROMPT, "req-1"));
        String second = ThreatUtils.activityGroupKey(envelope(ARN, PROMPT, "req-2"));
        assertNotNull(first);
        assertEquals(first, second);
    }

    @Test
    public void differentPromptOrCaller_differentKey() throws Exception {
        String key = ThreatUtils.activityGroupKey(envelope(ARN, PROMPT, "req-1"));
        String otherPrompt = "{\"messages\":[{\"role\":\"user\",\"content\":\"something else\"}]}";
        assertNotEquals(key, ThreatUtils.activityGroupKey(envelope(ARN, otherPrompt, "req-1")));
        assertNotEquals(key, ThreatUtils.activityGroupKey(envelope(ARN.replace("b4ad0a7d", "00000000"), PROMPT, "req-1")));
    }

    @Test
    public void headerNameIsCaseInsensitive() throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Bedrock-Identity-Arn", ARN);
        assertEquals(ThreatUtils.activityGroupKey(envelope(ARN, PROMPT, "req-1")),
            ThreatUtils.activityGroupKey(envelope(headers, PROMPT)));
    }

    @Test
    public void noArn_noKey() throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        assertNull(ThreatUtils.activityGroupKey(envelope(headers, PROMPT)));
    }

    @Test
    public void unparseableHeaders_noKey() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestPayload", PROMPT);
        fields.put("requestHeaders", "{\"bedrock-identity-arn\":\"" + ARN + "\",\"X-Bedrock-Model-Id\":\"[REDAC");
        assertNull(ThreatUtils.activityGroupKey(MAPPER.writeValueAsString(fields)));
    }

    @Test
    public void olderEnvelopeFormats_noKey() {
        assertNull(ThreatUtils.activityGroupKey("{\"message\":\"Project files: README.md\"}"));
        assertNull(ThreatUtils.activityGroupKey("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"model\":\"m\"}"));
        assertNull(ThreatUtils.activityGroupKey("not json"));
        assertNull(ThreatUtils.activityGroupKey(""));
        assertNull(ThreatUtils.activityGroupKey(null));
    }

    @Test
    public void groupingIsOptInPerAccount() {
        // No AKTO_GROUPED_ACTIVITY_ACCOUNTS in the test JVM, so only the default account is on.
        assertTrue(ThreatUtils.groupsActivities("1703087742"));
        assertFalse(ThreatUtils.groupsActivities("1000000"));
        assertFalse(ThreatUtils.groupsActivities(null));
    }

    private static String envelope(String arn, String prompt, String requestId) throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Request-Id", requestId);
        headers.put("bedrock-identity-arn", arn);
        headers.put("host", "bedrock-runtime.us-east-1.amazonaws.com");
        return envelope(headers, prompt);
    }

    // Shape of the stored envelope (latestApiOrig): requestHeaders is itself a JSON string.
    private static String envelope(Map<String, Object> headers, String prompt) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("method", "POST");
        fields.put("path", "/model/us.anthropic.claude-sonnet-4-6/converse");
        fields.put("requestHeaders", MAPPER.writeValueAsString(headers));
        fields.put("requestPayload", prompt);
        fields.put("responsePayload", "{}");
        return MAPPER.writeValueAsString(fields);
    }
}
