package com.akto.data_actor;

import com.akto.dto.OriginalHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FetchAgentModelsTest {

    @Test
    public void requestHitsAbstractorFetchAgentModels() {
        OriginalHttpRequest request = ClientActor.buildFetchAgentModelsRequest();

        assertTrue(request.getUrl().endsWith("/api/fetchAgentModels"));
        assertEquals("POST", request.getMethod());
        assertEquals("{}", request.getBody());
        assertTrue(request.getHeaders().containsKey(ClientActor.AUTHORIZATION));
    }

    @Test
    public void parsesModelsWithParams() {
        String body = "[{\"name\":\"red-teaming\",\"type\":\"ANTHROPIC\",\"params\":{\"model\":\"claude-sonnet-4-6\",\"apiKey\":\"k\"}}]";

        List<Map<String, Object>> models = ClientActor.parseAgentModels(200, body);

        assertNotNull(models);
        assertEquals(1, models.size());
        assertEquals("red-teaming", models.get(0).get("name"));
        assertEquals("ANTHROPIC", models.get(0).get("type"));
        Map<?, ?> params = (Map<?, ?>) models.get(0).get("params");
        assertEquals("claude-sonnet-4-6", params.get("model"));
        assertEquals("k", params.get("apiKey"));
    }

    @Test
    public void emptyListMeansNoneSaved() {
        List<Map<String, Object>> models = ClientActor.parseAgentModels(200, "[]");

        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    public void nonOkOrMissingBodyMeansOutage() {
        assertNull(ClientActor.parseAgentModels(422, "{\"actionErrors\":[\"Failed to fetch agent models\"]}"));
        assertNull(ClientActor.parseAgentModels(200, null));
    }

    @Test
    public void dbActorHasNoLocalModels() {
        assertTrue(new DbActor().fetchAgentModels().isEmpty());
    }
}
