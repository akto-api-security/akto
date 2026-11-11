package com.akto.gateway;

import okhttp3.*;
import okio.Buffer;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class GuardrailsValidateFileTest {

    private final List<Request> sent = new ArrayList<>();
    private final List<String> sentBodies = new ArrayList<>();

    private GuardrailsClient client(String responseBody, IOException failure) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            sent.add(request);
            sentBodies.add(buffer.readString(StandardCharsets.UTF_8));
            if (failure != null) throw failure;
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                    .body(ResponseBody.create(responseBody, MediaType.get("application/json"))).build();
        }).build();
        return new GuardrailsClient("http://guardrails.test", http, (key, message) -> { });
    }

    private static File tempFile(String content) throws IOException {
        File file = File.createTempFile("upload", ".tmp");
        file.deleteOnExit();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test public void sendsFilesUrlsAndFieldsAsMultipartToValidateFile() throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("path", "/v1/messages");
        fields.put("contextSource", "AGENTIC");
        fields.put("tag", null);
        List<GuardrailsClient.FilePart> files = Collections.singletonList(
                new GuardrailsClient.FilePart(tempFile("FILE_BYTES"), "report.pdf", "application/pdf"));

        Map<String, Object> result = client("{\"allowed\":false,\"reason\":\"PII\"}", null)
                .callValidateFile(fields, files, Collections.singletonList("https://example.test/a.pdf"));

        assertEquals(Boolean.FALSE, result.get("allowed"));
        assertEquals("PII", result.get("reason"));
        assertEquals("http://guardrails.test/api/validate/file", sent.get(0).url().toString());
        assertTrue(sent.get(0).body().contentType().toString().startsWith("multipart/form-data"));

        String body = sentBodies.get(0);
        assertTrue(body.contains("name=\"file\"; filename=\"report.pdf\""));
        assertTrue(body.contains("Content-Type: application/pdf"));
        assertTrue(body.contains("FILE_BYTES"));
        assertTrue(body.contains("name=\"url\""));
        assertTrue(body.contains("https://example.test/a.pdf"));
        assertTrue(body.contains("name=\"path\""));
        assertTrue(body.contains("name=\"contextSource\""));
        assertFalse("null fields are omitted", body.contains("name=\"tag\""));
    }

    @Test public void failsOpenWhenGuardrailsTimesOut() throws IOException {
        List<GuardrailsClient.FilePart> files = Collections.singletonList(
                new GuardrailsClient.FilePart(tempFile("x"), "a.txt", null));

        Map<String, Object> result = client("{}", new SocketTimeoutException("timed out"))
                .callValidateFile(new HashMap<>(), files, Collections.emptyList());

        assertEquals(Boolean.TRUE, result.get("allowed"));
        assertEquals(Boolean.TRUE, result.get("failOpen"));
    }
}
