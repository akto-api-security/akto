package com.akto.gateway;

import okhttp3.*;
import okio.Buffer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuardrailsFileValidationTest {

    private static Map<String, Object> file(String filename, String type, String content, String url) {
        Map<String, Object> file = new HashMap<>();
        file.put("filename", filename);
        file.put("type", type);
        if (content != null) {
            file.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        }
        if (url != null) {
            file.put("url", url);
        }
        return file;
    }

    private static List<String> filenames(List<GuardrailsClient.FileUpload> uploads) {
        return uploads.stream().map(upload -> upload.filename).collect(Collectors.toList());
    }

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
            new GuardrailsClient.FileUpload("card.txt", "card 4111 1111 1111 1111".getBytes(StandardCharsets.UTF_8))));

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
        assertFalse(multipart.contains("name=\"url\""));
    }

    @Test
    public void anUnreachableServiceFailsOpen() {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            throw new java.net.ConnectException("refused");
        }).build();
        GuardrailsClient client = new GuardrailsClient("http://guardrails.test", http, (key, message) -> { });

        Map<String, Object> verdict = client.callValidateFile(new HashMap<>(), Collections.singletonList(
            new GuardrailsClient.FileUpload("a.txt", new byte[] {1})));

        assertEquals(true, verdict.get("Allowed"));
    }

    @Test
    public void onlyInlineUploadsAreValidatedNewestFirst() {
        List<GuardrailsClient.FileUpload> uploads = Gateway.uploadsToValidate(Arrays.asList(
            file("old.txt", "file", "old", null),
            file("remote.pdf", "file", null, "https://example.com/remote.pdf"),
            file("blank.txt", "file", "", null),
            file("new.pdf", "file", "new", null)));

        assertEquals(Arrays.asList("new.pdf", "old.txt"), filenames(uploads));
        assertEquals("new", new String(uploads.get(0).content, StandardCharsets.UTF_8));
    }

    @Test
    public void imagesAreDroppedByTypeOrExtensionAndDocumentsAreKept() {
        List<GuardrailsClient.FileUpload> uploads = Gateway.uploadsToValidate(Arrays.asList(
            file("attachment-1.png", "image", "png bytes", null),
            file("Screenshot.JPEG", "file", "jpeg bytes", null),
            file("photo", "IMAGE", "typed image without extension", null),
            file("contract.pdf", "file", "pdf bytes", null),
            file("notes.txt", "audio", "text bytes", null)));

        assertEquals(Arrays.asList("notes.txt", "contract.pdf"), filenames(uploads));
    }

    @Test
    public void invalidBase64IsSkippedAndAMissingFilenameGetsADefault() {
        Map<String, Object> broken = file("broken.txt", "file", null, null);
        broken.put("content", "not base64!!");

        List<GuardrailsClient.FileUpload> uploads = Gateway.uploadsToValidate(Arrays.asList(
            broken, file("", "file", "unnamed", null)));

        assertEquals(Collections.singletonList("attachment"), filenames(uploads));
    }
}
