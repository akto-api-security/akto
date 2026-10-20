package com.akto.utils;

import com.akto.dto.IngestDataBatch;
import com.mongodb.BasicDBObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BedrockSessionResolverTest {

    private static final String AGENT_ARN =
            "arn:aws:sts::123456789012:assumed-role/aria-usertask-role/edf60249dbcf44b19f0c10eedfe1790f";
    private static final String SSO_ARN =
            "arn:aws:sts::123456789012:assumed-role/AWSReservedSSO_Admin_abc/mabba@example.com";
    private static final long T0 = 1792397986L;

    private BedrockSessionResolver resolver;

    @Before
    public void setUp() {
        resolver = new BedrockSessionResolver();
    }

    @Test
    public void sameCallerWithinGap_sharesSession() {
        String first = sessionOf(call(AGENT_ARN, T0));
        String second = sessionOf(call(AGENT_ARN, T0 + BedrockSessionResolver.SESSION_GAP_SECONDS));
        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    public void gapIsMeasuredFromLastCall() {
        String first = sessionOf(call(SSO_ARN, T0));
        sessionOf(call(SSO_ARN, T0 + 15 * 60));
        String third = sessionOf(call(SSO_ARN, T0 + 30 * 60));
        assertEquals(first, third);
    }

    @Test
    public void sameCallerAfterGap_startsNewSession() {
        String first = sessionOf(call(SSO_ARN, T0));
        String second = sessionOf(call(SSO_ARN, T0 + BedrockSessionResolver.SESSION_GAP_SECONDS + 1));
        assertNotEquals(first, second);
    }

    @Test
    public void differentCallers_getDifferentSessions() {
        assertNotEquals(sessionOf(call(AGENT_ARN, T0)), sessionOf(call(SSO_ARN, T0)));
    }

    @Test
    public void outOfOrderCallWithinGap_sharesSession() {
        String later = sessionOf(call(AGENT_ARN, T0 + 60));
        String earlier = sessionOf(call(AGENT_ARN, T0));
        assertEquals(later, earlier);
    }

    @Test
    public void lateCallFromOlderConversation_keepsLiveSession() {
        String live = sessionOf(call(SSO_ARN, T0 + 3600));
        String late = sessionOf(call(SSO_ARN, T0));
        String next = sessionOf(call(SSO_ARN, T0 + 3600 + 60));
        assertNotEquals(live, late);
        assertEquals(live, next);
    }

    @Test
    public void sessionId_isPrefixed() {
        assertTrue(sessionOf(call(AGENT_ARN, T0)).startsWith(BedrockSessionResolver.SESSION_ID_PREFIX));
    }

    @Test
    public void missingTime_stillGetsSession() {
        IngestDataBatch payload = call(AGENT_ARN, T0);
        payload.setTime(null);
        assertFalse(sessionOf(payload).isEmpty());
    }

    @Test
    public void arnFromTag_isUsedWhenHeaderMissing() {
        IngestDataBatch payload = call(null, T0);
        payload.setTag("{\"bedrock-identity-arn\":\"" + AGENT_ARN + "\"}");
        assertFalse(sessionOf(payload).isEmpty());
    }

    @Test
    public void existingSessionHeader_isKept() {
        IngestDataBatch payload = call(AGENT_ARN, T0);
        payload.setRequestHeaders(headers(AGENT_ARN, null).append(BedrockSessionResolver.SESSION_HEADER, "abc").toJson());
        resolver.resolve(payload);
        assertEquals("abc", sessionOf(payload));
    }

    @Test
    public void discoveryMessage_isLeftAlone() {
        IngestDataBatch payload = call(AGENT_ARN, T0);
        payload.setRequestHeaders(headers(AGENT_ARN, "DISCOVERY").toJson());
        String before = payload.getRequestHeaders();
        resolver.resolve(payload);
        assertEquals(before, payload.getRequestHeaders());
    }

    @Test
    public void noIdentityArn_isLeftAlone() {
        IngestDataBatch payload = call(null, T0);
        String before = payload.getRequestHeaders();
        resolver.resolve(payload);
        assertEquals(before, payload.getRequestHeaders());
    }

    @Test
    public void otherHeaders_arePreserved() {
        IngestDataBatch payload = call(AGENT_ARN, T0);
        resolver.resolve(payload);
        BasicDBObject headers = BasicDBObject.parse(payload.getRequestHeaders());
        assertEquals("bedrock-runtime.us-east-1.amazonaws.com", headers.getString("host"));
        assertEquals(AGENT_ARN, headers.getString("bedrock-identity-arn"));
    }

    @Test
    public void apply_isNoOpOutsideGatedAccount() {
        // Test JVM has no abstractor token, so the account gate is closed.
        IngestDataBatch payload = call(AGENT_ARN, T0);
        BedrockSessionResolver.apply(payload);
        assertNull(BasicDBObject.parse(payload.getRequestHeaders()).get(BedrockSessionResolver.SESSION_HEADER));
    }

    private String sessionOf(IngestDataBatch payload) {
        resolver.resolve(payload);
        Object id = BasicDBObject.parse(payload.getRequestHeaders()).get(BedrockSessionResolver.SESSION_HEADER);
        return id == null ? "" : id.toString();
    }

    private static IngestDataBatch call(String arn, long time) {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setRequestHeaders(headers(arn, "Converse").toJson());
        payload.setTime(String.valueOf(time));
        return payload;
    }

    private static BasicDBObject headers(String arn, String operation) {
        BasicDBObject headers = new BasicDBObject("host", "bedrock-runtime.us-east-1.amazonaws.com");
        if (arn != null) {
            headers.append("bedrock-identity-arn", arn);
        }
        if (operation != null) {
            headers.append("bedrock-operation", operation);
        }
        return headers;
    }
}
