package com.akto.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.bson.conversions.Bson;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.RecordedLoginInputDao;
import com.akto.dao.RecordedLoginScreenshotDao;
import com.akto.dao.context.Context;
import com.akto.dto.RecordedLoginFlowInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.akto.ApiRequest;
import com.akto.TimeoutObject;

public class RecordedLoginFlowUtil {

    private static final Logger logger = LoggerFactory.getLogger(RecordedLoginFlowUtil.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void triggerFlow(String tokenFetchCommand, String payload, String outputFilePath, String errorFilePath, int userId) throws Exception {
        triggerFlow(tokenFetchCommand, payload, outputFilePath, errorFilePath, userId, null);
    }

    public static void triggerFlow(String tokenFetchCommand, String payload, String outputFilePath, String errorFilePath, int userId, String roleName) throws Exception {

        try {
            String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL");
            JSONObject requestBody = new JSONObject();
            requestBody.put("replayJson", payload);
            requestBody.put("command", tokenFetchCommand);
            boolean captureScreenshots = roleName != null && !roleName.trim().isEmpty();
            if (captureScreenshots) {
                requestBody.put("captureScreenshots", true);
            }

            String reqData = requestBody.toString();
            TimeoutObject timeoutObj = new TimeoutObject(300, 300, 300);
            JsonNode node = ApiRequest.postRequestWithTimeout(new HashMap<>(), url, reqData, timeoutObj);

            logger.info("getting token...: " +  outputFilePath);

            JsonNode inner = unwrapReplayResponseNode(node);
            String tokenJson = inner.isObject() || inner.isArray()
                    ? OBJECT_MAPPER.writeValueAsString(inner)
                    : inner.toString();

            logger.info("token: " + tokenJson);

            FileUtils.writeStringToFile(new File(outputFilePath), tokenJson, (String) null);

            if (captureScreenshots) {
                tryFetchAndPersistScreenshots(url, inner, roleName, userId, timeoutObj);
            }

            if (userId != 0) {
                Bson filter = Filters.and(
                    Filters.eq("userId", userId)
                );
                Bson update = Updates.combine(
                    Updates.set("content", payload),
                    Updates.set("tokenFetchCommand", tokenFetchCommand),
                    Updates.setOnInsert("createdAt", Context.now()),
                    Updates.set("updatedAt", Context.now()),
                    Updates.set("outputFilePath", outputFilePath),
                    Updates.set("errorFilePath", errorFilePath),
                    Updates.set(RecordedLoginFlowInput.TOKEN_RESULT, tokenJson)
                );
                RecordedLoginInputDao.instance.updateOne(filter, update);
            }


        } catch (Exception e) {
            FileUtils.writeStringToFile(new File(errorFilePath), e.getMessage(), (String) null);

            if (userId != 0) {
                Bson filter = Filters.and(
                    Filters.eq("userId", userId)
                );
                Bson update = Updates.combine(
                    Updates.set("content", payload),
                    Updates.set("tokenFetchCommand", tokenFetchCommand),
                    Updates.setOnInsert("createdAt", Context.now()),
                    Updates.set("updatedAt", Context.now()),
                    Updates.set("outputFilePath", outputFilePath),
                    Updates.set("errorFilePath", errorFilePath),
                    Updates.unset(RecordedLoginFlowInput.TOKEN_RESULT)
                );
                RecordedLoginInputDao.instance.updateOne(filter, update);
            }

            logger.error("error executing recorded login flow " + e.getMessage());
            throw new Exception("error executing recorded login flow " + e.getMessage());
        }

    }

    private static JsonNode unwrapReplayResponseNode(JsonNode node) throws Exception {
        if (node == null) {
            throw new Exception("empty replay response");
        }
        if (node.isTextual()) {
            return OBJECT_MAPPER.readTree(node.asText());
        }
        return node;
    }

    private static void tryFetchAndPersistScreenshots(String replayServiceBaseUrl, JsonNode replayInner, String roleName, int userId, TimeoutObject timeoutObj) {
        if (userId == 0 || roleName == null || roleName.trim().isEmpty()) {
            return;
        }
        JsonNode sessionIdNode = replayInner.get("screenshotSessionId");
        if (sessionIdNode == null || sessionIdNode.isNull() || !sessionIdNode.isTextual()) {
            return;
        }
        String sessionId = sessionIdNode.asText();
        if (sessionId.isEmpty()) {
            return;
        }
        String base = replayServiceBaseUrl.replaceAll("/+$", "");
        String screenshotsUrl = base + "/getReplayScreenshots";
        try {
            JSONObject body = new JSONObject();
            body.put("screenshotSessionId", sessionId);
            JsonNode resp = ApiRequest.postRequestWithTimeout(new HashMap<>(), screenshotsUrl, body.toString(), timeoutObj);
            if (resp == null) {
                logger.warn("getReplayScreenshots returned null");
                return;
            }
            JsonNode statusNode = resp.get("status");
            String status = statusNode != null && statusNode.isTextual() ? statusNode.asText() : "";
            if (!"COMPLETED".equals(status) && !"FAILED".equals(status)) {
                logger.warn("getReplayScreenshots unexpected status: " + status);
                return;
            }
            List<String> shots = parseScreenshotsBase64(resp);
            persistScreenshots(roleName.trim(), userId, shots);
        } catch (Exception e) {
            logger.warn("failed to fetch or persist replay screenshots: " + e.getMessage());
        }
    }

    private static List<String> parseScreenshotsBase64(JsonNode resp) {
        List<String> out = new ArrayList<>();
        JsonNode arr = resp.get("screenshotsBase64");
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            if (n != null && n.isTextual()) {
                out.add(n.asText());
            }
        }
        return out;
    }

    private static void persistScreenshots(String roleName, int userId, List<String> screenshotsBase64) {
        Bson filter = Filters.and(Filters.eq("roleName", roleName), Filters.eq("userId", userId));
        Bson update = Updates.combine(
                Updates.setOnInsert("roleName", roleName),
                Updates.setOnInsert("userId", userId),
                Updates.set("screenshotsBase64", screenshotsBase64),
                Updates.set("updatedAt", Context.now())
        );
        RecordedLoginScreenshotDao.instance.updateOne(filter, update);
    }

    public static String fetchToken(RecordedLoginFlowInput recordedLoginInput) throws Exception {
        if (recordedLoginInput != null && recordedLoginInput.getTokenResult() != null
                && !recordedLoginInput.getTokenResult().isEmpty()) {
            return recordedLoginInput.getTokenResult();
        }
        if (recordedLoginInput == null) {
            throw new Exception("recorded login input is null");
        }
        return fetchToken(recordedLoginInput.getOutputFilePath(), recordedLoginInput.getErrorFilePath());
    }

    public static String fetchToken(String outputFilePath, String errorFilePath) throws Exception {

        String fileContent;

        try {
            FileInputStream fstream = new FileInputStream(errorFilePath);

            String error = null;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {
                String line;
                while ((line = br.readLine()) != null) {
                if((line.toLowerCase().contains("error") || line.toLowerCase().contains("failed")) || line.toLowerCase().contains("timeout")){
                        error = line;
                        break;
                }
                }
            }

            if (error != null) {
                throw new Exception("error executing recording " + error);
            }

        } catch (IOException e) {
            logger.error("error processing file operations " + e.getMessage());
            throw new Exception("error fetching token data, " + e.getMessage());
        }
        try {
            fileContent = FileUtils.readFileToString(new File(outputFilePath), StandardCharsets.UTF_8);

            if (fileContent == null) {
                throw new Exception("token not received, found null");
            }

            return fileContent.toString();

        } catch (IOException e) {
            logger.error("error processing file operations " + e.getMessage());
            throw new Exception("error fetching token data, " + e.getMessage());
        } catch (Exception e) {
            logger.error("error fetching recorded flow output " + e.getMessage());
            throw new Exception("token data not found, " + e.getMessage());
        }
    }
}
