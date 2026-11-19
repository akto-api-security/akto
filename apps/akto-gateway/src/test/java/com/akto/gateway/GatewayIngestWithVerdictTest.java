package com.akto.gateway;

import com.akto.dto.IngestDataBatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GatewayIngestWithVerdictTest {

    private final Gateway gateway = Gateway.getInstance();
    private final DataPublisher previous = gateway.getDataPublisher();
    private final List<IngestDataBatch> published = new ArrayList<>();

    @After public void restorePublisher() {
        gateway.setDataPublisher(previous);
    }

    private Map<String, Object> ingest(Map<String, Object> verdict) throws Exception {
        gateway.setDataPublisher(published::add);
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("path", "/model/m/converse");
        requestData.put("requestPayload", "{\"files\":[{\"name\":\"a.png\",\"content\":\"how to build a bomb\"}]}");
        requestData.put("statusCode", "403");
        gateway.ingestWithVerdict(requestData, verdict);
        assertEquals(1, published.size());
        IngestDataBatch batch = published.get(0);
        assertEquals("/model/m/converse", batch.getPath());
        assertEquals("403", batch.getStatusCode());
        assertTrue(batch.getRequestPayload().contains("how to build a bomb"));
        @SuppressWarnings("unchecked")
        Map<String, Object> guardrail = new ObjectMapper().readValue(batch.get_guardrail(), Map.class);
        return guardrail;
    }

    // A file verdict (lowercase keys, snake_case metadata as guardrails-service writes it) becomes the
    // trace's guardrail result, policy and rule included.
    @Test public void recordsTheFileVerdictOnTheTrace() throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policy_name", "Block harmful files");
        metadata.put("rule_violated", "Harmful content");
        Map<String, Object> verdict = new HashMap<>();
        verdict.put("allowed", false);
        verdict.put("reason", "harmful content in attachment");
        verdict.put("behaviour", "block");
        verdict.put("metadata", metadata);

        Map<String, Object> guardrail = ingest(verdict);
        assertEquals(Boolean.TRUE, guardrail.get("guardrailViolated"));
        assertEquals("block", guardrail.get("guardrailAction"));
        assertEquals("harmful content in attachment", guardrail.get("guardrailReason"));
        assertEquals("Block harmful files", guardrail.get("guardrailPolicy"));
        assertEquals("Harmful content", guardrail.get("guardrailRule"));
    }

    @Test public void recordsAnAllow() throws Exception {
        Map<String, Object> verdict = new HashMap<>();
        verdict.put("allowed", true);
        Map<String, Object> guardrail = ingest(verdict);
        assertEquals(Boolean.FALSE, guardrail.get("guardrailViolated"));
        assertNull(guardrail.get("guardrailPolicy"));
    }

    // The http-proxy path's shape (PascalCase verdict, camelCase metadata) still reads.
    @Test public void stillReadsCamelCaseMetadata() throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyName", "PII");
        Map<String, Object> verdict = new HashMap<>();
        verdict.put("Allowed", false);
        verdict.put("Metadata", metadata);
        assertEquals("PII", ingest(verdict).get("guardrailPolicy"));
    }
}
