package com.akto.util;

import java.util.HashMap;
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
import com.akto.dao.context.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.akto.ApiRequest;
import com.akto.TimeoutObject;

public class RecordedLoginFlowUtil {

    private static final Logger logger = LoggerFactory.getLogger(RecordedLoginFlowUtil.class);

    public static void triggerFlow(String tokenFetchCommand, String payload, String outputFilePath, String errorFilePath, int userId) throws Exception {

        try {
            String url = System.getenv("PUPPETEER_REPLAY_SERVICE_URL");
            JSONObject requestBody = new JSONObject();
            requestBody.put("replayJson", payload);
            requestBody.put("command", tokenFetchCommand);

            String reqData = requestBody.toString();
            TimeoutObject timeoutObj = new TimeoutObject(300, 300, 300);
            JsonNode node = ApiRequest.postRequestWithTimeout(new HashMap<>(), url, reqData, timeoutObj);

            logger.info("getting token...: " +  outputFilePath);

            String token = node.toString();  //node.get("token").textValue();

            logger.info("token: " + token);

            FileUtils.writeStringToFile(new File(outputFilePath), token, (String) null);

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
                    Updates.set("errorFilePath", errorFilePath)
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
                    Updates.set("errorFilePath", errorFilePath)
                );
                RecordedLoginInputDao.instance.updateOne(filter, update);
            }

            logger.error("error executing recorded login flow " + e.getMessage());
            throw new Exception("error executing recorded login flow " + e.getMessage());
        }

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

