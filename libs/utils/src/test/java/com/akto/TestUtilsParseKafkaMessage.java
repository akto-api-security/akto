package com.akto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.Test;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.utils.Utils;

import java.util.*;

public class TestUtilsParseKafkaMessage {

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Headers must be encoded as a JSON string field in the outer message
    // because OriginalHttpRequest.buildHeadersMap expects a String, not an object.
    private static String headersMapToJsonStringField(Map<String, List<String>> headers) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(jsonEscape(e.getKey())).append("\":[");
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(jsonEscape(e.getValue().get(i))).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        // wrap as a JSON string literal for the outer message
        return "\"" + jsonEscape(sb.toString()) + "\"";
    }

    private String buildMessage(String method,
                                String path,
                                String type,
                                Map<String, List<String>> requestHeaders,
                                String requestPayloadRaw, // already JSON-encoded scalar or string literal
                                int statusCode,
                                Map<String, List<String>> responseHeaders,
                                String responsePayloadRaw // plain string, will be JSON-string-encoded
    ) {
        String requestHeadersField = headersMapToJsonStringField(requestHeaders);
        String responseHeadersField = headersMapToJsonStringField(responseHeaders);

        return "{"
                + "\"method\":\"" + jsonEscape(method) + "\","
                + "\"path\":\"" + jsonEscape(path) + "\","
                + "\"type\":\"" + jsonEscape(type) + "\","
                + "\"requestHeaders\":" + requestHeadersField + ","
                + "\"requestPayload\":" + requestPayloadRaw + ","
                + "\"statusCode\":" + statusCode + ","
                + "\"status\":\"OK\","
                + "\"responseHeaders\":" + responseHeadersField + ","
                + "\"responsePayload\":\"" + jsonEscape(responsePayloadRaw) + "\","
                + "\"time\":123,"
                + "\"akto_account_id\":\"acc1\","
                + "\"ip\":\"1.1.1.1\","
                + "\"destIp\":\"2.2.2.2\","
                + "\"direction\":\"INBOUND\""
                + "}";
    }

    @Test
    public void testParseWithJsonPayload() throws Exception {
        Map<String, List<String>> reqH = new HashMap<>();
        reqH.put("Content-Type", Collections.singletonList("application/json"));
        Map<String, List<String>> resH = new HashMap<>();
        resH.put("Content-Type", Collections.singletonList("application/json"));

        String jsonBody = "{\"key\":\"value\"}";
        String msg = buildMessage(
                "POST",
                "/api/json",
                "HTTP",
                reqH,
                "\"" + jsonEscape(jsonBody) + "\"",
                200,
                resH,
                "{\"res\":\"ok\"}"
        );

        HttpResponseParams response = Utils.parseKafkaMessage(msg);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals(jsonBody, response.getRequestParams().getPayload());
        assertEquals("{\"res\":\"ok\"}", response.getPayload());
    }

    @Test
    public void testParseWithXmlPayload() throws Exception {
        Map<String, List<String>> reqH = new HashMap<>();
        reqH.put("Content-Type", Collections.singletonList("application/xml"));
        Map<String, List<String>> resH = new HashMap<>();
        resH.put("Content-Type", Collections.singletonList("application/json"));

        String xml = "<note><to>Tove</to><from>Jani</from></note>";
        String msg = buildMessage(
                "POST",
                "/api/xml",
                "HTTP",
                reqH,
                "\"" + jsonEscape(xml) + "\"",
                200,
                resH,
                "{\"res\":\"ok\"}"
        );

        HttpResponseParams response = Utils.parseKafkaMessage(msg);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode());

        String reqJson = response.getRequestParams().getPayload();
        assertNotNull(reqJson);
        // Loose check: conversion happened and keys survived
        org.junit.Assert.assertTrue(reqJson.contains("Tove"));
        org.junit.Assert.assertTrue(reqJson.startsWith("{") || reqJson.trim().startsWith("{"));
    }

    @Test
    public void testParseWithPlainTextPayload() throws Exception {
        Map<String, List<String>> reqH = new HashMap<>();
        // no content type
        Map<String, List<String>> resH = new HashMap<>();

        String text = "hello world raw text";
        String msg = buildMessage(
                "POST",
                "/api/text",
                "HTTP",
                reqH,
                "\"" + jsonEscape(text) + "\"",
                200,
                resH,
                text
        );

        HttpResponseParams response = Utils.parseKafkaMessage(msg);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        // With no content type, your rawToJsonString should likely pass-through
        assertEquals(text, response.getRequestParams().getPayload());
    }

    @Test
    public void testParseWithEmptyPayload() throws Exception {
        Map<String, List<String>> reqH = new HashMap<>();
        Map<String, List<String>> resH = new HashMap<>();

        String msg = buildMessage(
                "GET",
                "/api/empty",
                "HTTP",
                reqH,
                "\"\"",
                204,
                resH,
                ""
        );

        HttpResponseParams response = Utils.parseKafkaMessage(msg);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode());
        assertEquals("", response.getRequestParams().getPayload());
    }

    // Complex XML similar to the error path you shared earlier
    @Test
    public void testParseWithComplexXmlPayload() throws Exception {
        Map<String, List<String>> reqH = new HashMap<>();
        reqH.put("Content-Type", Collections.singletonList("application/xml"));
        Map<String, List<String>> resH = new HashMap<>();
        resH.put("Content-Type", Collections.singletonList("application/json"));

        String complexXml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!DOCTYPE test [ <!ENTITY x3 SYSTEM \"file:///etc/hosts\"> ]>\n" +
                "<amfx ver=\"3\" xmlns=\"http://www.macromedia.com/2005/amfx\" xmlns:x=\"urn:test\">\n" +
                "  <body>\n" +
                "    <object type=\"flex.messaging.messages.CommandMessage\">\n" +
                "      <traits>\n" +
                "        <string>body</string>\n" +
                "        <string>clientId</string>\n" +
                "        <x:meta enabled=\"true\"/>\n" +
                "      </traits>\n" +
                "      <headers>\n" +
                "        <entry key=\"Accept\">application/json</entry>\n" +
                "        <entry key=\"X-Test\">value &amp; more</entry>\n" +
                "      </headers>\n" +
                "      <payload><![CDATA[{\"k\":\"v\",\"n\":123}]]></payload>\n" +
                "    </object>\n" +
                "  </body>\n" +
                "</amfx>";

        String msg = buildMessage(
                "POST",
                "/api/xml/complex",
                "HTTP",
                reqH,
                "\"" + jsonEscape(complexXml) + "\"",
                200,
                resH,
                "{\"res\":\"ok\"}"
        );

        HttpResponseParams response = Utils.parseKafkaMessage(msg);

        // If jackson-dataformat-xml and jackson-core versions are skewed, this would throw
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());

        String reqJson = response.getRequestParams().getPayload();
        assertNotNull(reqJson);
        // Loose structural checks
        org.junit.Assert.assertTrue(reqJson.contains("\"amfx\"") || reqJson.contains("\"body\""));
        org.junit.Assert.assertTrue(reqJson.contains("CommandMessage") || reqJson.contains("\"object\""));
        org.junit.Assert.assertTrue(reqJson.contains("payload"));
        org.junit.Assert.assertTrue(reqJson.trim().startsWith("{"));
    }
}
