package com.akto.imperva;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.dao.context.Context;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.imperva.model.ImpervaSchema;
import com.akto.imperva.parser.ImpervaSchemaParser;
import com.akto.open_api.parser.ParserResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestImpervaSchemaParser {

    private static final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String GET_SCHEMA = "{"
        + "\"hostName\":\"api.example.com\",\"method\":\"GET\",\"resource\":\"/users/*/orders\","
        + "\"authenticationInfo\":{\"authParameterLocations\":[{\"authParameterLocation\":\"http-req-header-authorization\"}]},"
        + "\"request\":{\"queryParamList\":[{\"name\":\"page\",\"dataTypes\":[{\"type\":\"Number\"}]}]},"
        + "\"responses\":{\"200\":{\"contentTypeToResponseBody\":{\"application/json\":[{\"name\":\"body\",\"dataTypes\":[{\"type\":\"Object\","
        + "\"children\":[{\"name\":\"orderId\",\"dataTypes\":[{\"type\":\"String\"}]}]}]}]}}}"
        + "}";

    private static final String POST_SCHEMA = "{"
        + "\"hostName\":\"api.example.com\",\"method\":\"POST\",\"resource\":\"/orders\","
        + "\"request\":{\"contentTypeToRequestBody\":{\"application/json\":[{\"name\":\"body\",\"dataTypes\":[{\"type\":\"Object\","
        + "\"children\":[{\"name\":\"amount\",\"dataTypes\":[{\"type\":\"Number\"}]}]}]}]}}"
        + "}";

    @Before
    public void setUp() {
        Context.accountId.set(1_000_000);
    }

    private static ImpervaSchema schema(String json) throws Exception {
        return mapper.readValue(json, ImpervaSchema.class);
    }

    private static Map<String, Object> aktoMessage(SwaggerUploadLog log) throws Exception {
        return mapper.readValue(log.getAktoFormat(), Map.class);
    }

    @Test
    public void bodylessEndpointSkippedByDefault() throws Exception {
        ParserResult result = ImpervaSchemaParser.convertImpervaSchemaToAkto(schema(GET_SCHEMA), null, true, false);
        assertTrue(result.getUploadLogs().isEmpty());
    }

    @Test
    public void bodylessEndpointGetsSampleWhenEnabled() throws Exception {
        ParserResult result = ImpervaSchemaParser.convertImpervaSchemaToAkto(schema(GET_SCHEMA), null, true, false, true);
        assertEquals(1, result.getUploadLogs().size());

        Map<String, Object> msg = aktoMessage(result.getUploadLogs().get(0));
        assertEquals("GET", msg.get("method"));
        assertEquals("https://api.example.com/users/*/orders?page=123", msg.get("path"));
        assertEquals("", msg.get("requestPayload"));
        assertEquals("{\"orderId\":\"sample_string\"}", msg.get("responsePayload"));
        assertEquals("IMPERVA", msg.get("source"));

        Map<String, Object> requestHeaders = mapper.readValue((String) msg.get("requestHeaders"), Map.class);
        assertEquals("api.example.com", requestHeaders.get("host"));
        assertEquals("sample_token", requestHeaders.get("authorization"));
        assertFalse(requestHeaders.containsKey("Content-Type"));

        Map<String, Object> responseHeaders = mapper.readValue((String) msg.get("responseHeaders"), Map.class);
        assertEquals("application/json", responseHeaders.get("Content-Type"));
    }

    @Test
    public void bodyEndpointUnchangedWhenEnabled() throws Exception {
        List<SwaggerUploadLog> defaultLogs = ImpervaSchemaParser.convertImpervaSchemaToAkto(schema(POST_SCHEMA), null, true, false).getUploadLogs();
        List<SwaggerUploadLog> enabledLogs = ImpervaSchemaParser.convertImpervaSchemaToAkto(schema(POST_SCHEMA), null, true, false, true).getUploadLogs();
        assertEquals(1, defaultLogs.size());
        assertEquals(1, enabledLogs.size());
        assertEquals(aktoMessage(defaultLogs.get(0)).get("requestPayload"), aktoMessage(enabledLogs.get(0)).get("requestPayload"));
        assertEquals("{\"amount\":123}", aktoMessage(enabledLogs.get(0)).get("requestPayload"));
    }

    @Test
    public void replacesWildcardSegmentsOnly() {
        assertEquals("/dps/apps/STRING/users/STRING/secure", ImpervaSchemaParser.replacePathWildcards("/dps/apps/*/users/*/secure"));
        assertEquals("/common/culture/STRING", ImpervaSchemaParser.replacePathWildcards("/common/culture/*"));
        assertEquals("/files/a*.json", ImpervaSchemaParser.replacePathWildcards("/files/a*.json"));
    }
}
