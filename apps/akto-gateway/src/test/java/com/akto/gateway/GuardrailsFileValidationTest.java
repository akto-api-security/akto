package com.akto.gateway;

import okhttp3.*;
import okio.Buffer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuardrailsFileValidationTest {

    @Test
    public void filesAreUploadedAsMultipartWithTheRequestContext() throws Exception {
        AtomicReference<Request> sent = new AtomicReference<>();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            sent.set(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("ok")
                    .body(ResponseBody.create("{\"Allowed\":false,\"Reason\":\"PII in file\"}",
                        MediaType.get("application/json"))).build();
        }).build();
        GuardrailsClient client = new GuardrailsClient("http://guardrails.test", http, (key, message) -> { });
        Map<String, Object> fields = new HashMap<>();
        fields.put("akto_account_id", "1000000");
        fields.put("policyName", "PII Strict");

        Map<String, Object> verdict = client.callValidateFile(fields, Collections.singletonList(
            new GuardrailsClient.FileUpload("card.txt", "card 4111 1111 1111 1111".getBytes(StandardCharsets.UTF_8))),
            Collections.singletonList("https://example.com/spec.pdf"));

        assertEquals(false, verdict.get("Allowed"));
        assertEquals("PII in file", verdict.get("Reason"));
        Request request = sent.get();
        assertEquals("/api/validate/file", request.url().encodedPath());
        assertTrue(request.body().contentType().toString().startsWith("multipart/form-data"));
        Buffer body = new Buffer();
        request.body().writeTo(body);
        String multipart = body.readUtf8();
        assertTrue(multipart.contains("name=\"akto_account_id\"") && multipart.contains("1000000"));
        assertTrue(multipart.contains("name=\"policyName\"") && multipart.contains("PII Strict"));
        assertTrue(multipart.contains("name=\"file\"; filename=\"card.txt\"")
            && multipart.contains("card 4111 1111 1111 1111"));
        assertTrue(multipart.contains("name=\"url\"") && multipart.contains("https://example.com/spec.pdf"));
    }

    @Test
    public void anUnreachableServiceFailsOpen() {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            throw new java.net.ConnectException("refused");
        }).build();
        GuardrailsClient client = new GuardrailsClient("http://guardrails.test", http, (key, message) -> { });

        Map<String, Object> verdict = client.callValidateFile(new HashMap<>(), Collections.singletonList(
            new GuardrailsClient.FileUpload("a.txt", new byte[] {1})), Collections.emptyList());

        assertEquals(true, verdict.get("Allowed"));
    }

    @Test
    public void imagesAreSkippedAndDocumentsAreValidated() {
        assertTrue(Gateway.isSkippedFile("attachment-1.png"));
        assertTrue(Gateway.isSkippedFile("Screenshot.JPEG"));
        assertFalse(Gateway.isSkippedFile("contract.pdf"));
        assertFalse(Gateway.isSkippedFile("notes.txt"));
    }
}
