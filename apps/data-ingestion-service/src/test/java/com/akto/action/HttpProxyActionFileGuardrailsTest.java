package com.akto.action;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpProxyActionFileGuardrailsTest {

    private static HttpProxyAction fileCheck(List<Map<String, Object>> files) {
        HttpProxyAction action = new HttpProxyAction();
        action.setFile_guardrails("true");
        action.setAkto_account_id("1000000");
        action.setPath("/v1/chat/completions");
        action.setMethod("POST");
        action.setFiles(files);
        return action;
    }

    private static Map<String, Object> uploaded(String filename, String text) {
        Map<String, Object> file = new HashMap<>();
        file.put("filename", filename);
        file.put("type", "file");
        file.put("content", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        return file;
    }

    private static Map<String, Object> urlOnly() {
        Map<String, Object> file = new HashMap<>();
        file.put("filename", "remote.pdf");
        file.put("type", "file");
        file.put("url", "https://example.com/remote.pdf");
        return file;
    }

    @Test
    public void noFilesAllowWithoutAVerdict() {
        HttpProxyAction action = fileCheck(Collections.emptyList());

        assertEquals("SUCCESS", action.httpProxy());
        assertTrue(action.isSuccess());
        assertEquals(Collections.emptyMap(), action.getData());
    }

    @Test
    public void filesWithNothingToUploadAreAllowedWithoutRunningTheProxyFlow() {
        HttpProxyAction action = fileCheck(Collections.singletonList(urlOnly()));

        assertEquals("SUCCESS", action.httpProxy());
        assertTrue(action.isSuccess());
        assertEquals("the normal proxy flow would have filled data", Collections.emptyMap(), action.getData());
    }

    @Test
    public void uploadedFilesGetAVerdictFromTheGuardrailsService() {
        HttpProxyAction action = fileCheck(Collections.singletonList(uploaded("notes.txt", "quarterly notes")));

        assertEquals("SUCCESS", action.httpProxy());
        assertTrue(action.getData().containsKey("guardrailsResult"));
    }

    @Test
    public void aFullLimiterFailsTheCallSoLitellmsFallbackDecides() {
        int permits = HttpProxyAction.FILE_CHECKS.drainPermits();
        try {
            HttpProxyAction action = fileCheck(Collections.singletonList(uploaded("notes.txt", "quarterly notes")));

            assertEquals("ERROR", action.httpProxy());
            assertFalse(action.isSuccess());
            assertEquals(0, HttpProxyAction.FILE_CHECKS.availablePermits());
        } finally {
            HttpProxyAction.FILE_CHECKS.release(permits);
        }
    }

    @Test
    public void everyFileCheckGivesItsPermitBack() {
        int before = HttpProxyAction.FILE_CHECKS.availablePermits();

        fileCheck(Collections.singletonList(urlOnly())).httpProxy();
        fileCheck(Collections.singletonList(uploaded("notes.txt", "quarterly notes"))).httpProxy();

        assertEquals(before, HttpProxyAction.FILE_CHECKS.availablePermits());
    }
}
