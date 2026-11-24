package com.akto.utils.elasticsearch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Bedrock records reach Akto with the model as a tag and a header, never in the response body,
 * while every reader of a record (Traces UI, top-models aggregation) looks for "model" in
 * responsePayload. withBedrockModel bridges the two.
 */
public class AgentQueryRecordBedrockModelTest {

    private static final String MODEL = "anthropic.claude-3-5-sonnet-20240620-v1:0";
    private static final String CONVERSE_RESPONSE =
        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"hi\"}]}},"
        + "\"usage\":{\"inputTokens\":12,\"outputTokens\":3}}";

    private static Map<String, String> tags(String model) {
        Map<String, String> t = new HashMap<>();
        t.put("source", "AWS_BEDROCK");
        if (model != null) t.put("model", model);
        return t;
    }

    private static Map<String, List<String>> headers(String model) {
        Map<String, List<String>> h = new HashMap<>();
        if (model != null) h.put("x-bedrock-model-id", Collections.singletonList(model));
        return h;
    }

    @Test
    public void modelTagIsWrittenIntoTheResponse() throws Exception {
        String out = AgentQueryRecord.withBedrockModel(CONVERSE_RESPONSE, tags(MODEL), headers(null));
        JSONObject obj = new JSONObject(out);
        assertEquals(MODEL, obj.getString("model"));
        assertEquals(12, obj.getJSONObject("usage").getInt("inputTokens"), "the rest of the body is kept");
    }

    @Test
    public void headerIsUsedWhenTheTagIsMissing() throws Exception {
        String out = AgentQueryRecord.withBedrockModel(CONVERSE_RESPONSE, tags(null), headers(MODEL));
        assertEquals(MODEL, new JSONObject(out).getString("model"));
    }

    @Test
    public void aModelAlreadyInTheResponseWins() throws Exception {
        String withOwn = "{\"model\":\"set-by-provider\",\"usage\":{}}";
        assertEquals(withOwn, AgentQueryRecord.withBedrockModel(withOwn, tags(MODEL), headers(MODEL)));
    }

    @Test
    public void theLogProcessorsPlaceholderIsNotAModel() throws Exception {
        assertEquals(CONVERSE_RESPONSE,
            AgentQueryRecord.withBedrockModel(CONVERSE_RESPONSE, tags("unknown-model"), headers(null)));
    }

    @Test
    public void unknownTagFallsBackToARealHeader() throws Exception {
        String out = AgentQueryRecord.withBedrockModel(CONVERSE_RESPONSE, tags("unknown-model"), headers(MODEL));
        assertEquals(MODEL, new JSONObject(out).getString("model"));
    }

    @Test
    public void nonJsonAndEmptyPayloadsAreLeftAlone() throws Exception {
        assertEquals("not json", AgentQueryRecord.withBedrockModel("not json", tags(MODEL), headers(null)));
        assertEquals("", AgentQueryRecord.withBedrockModel("", tags(MODEL), headers(null)));
        assertNull(AgentQueryRecord.withBedrockModel(null, tags(MODEL), headers(null)));
    }
}
