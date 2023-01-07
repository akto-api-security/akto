package com.akto.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.google.gson.Gson;

public class RecordedLoginFlowUtil {

    private static final Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(RecordedLoginFlowUtil.class);

    public static void triggerFlow(String tokenFetchCommand, String payload) throws Exception {
        String filePath = new File("").getAbsolutePath();
        String fileUrl = filePath.concat("/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/recordedFlowScript.mjs");

        ProcessBuilder pb = new ProcessBuilder("node", fileUrl, tokenFetchCommand, payload);

        try {
            Process process = pb.start();
            filePath = new File("").getAbsolutePath();
            fileUrl = filePath.concat("/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/recordedFlowOutput.txt");

            BufferedReader reader = 
                new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ( (line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
            String result = builder.toString();
            logger.info(result);
            
            FileUtils.writeStringToFile(new File(fileUrl), result, (String) null);

        } catch (IOException e) {
            logger.error("error processing file operations " + e.getMessage());
            throw new Exception("error processing file operations " + e.getMessage());
        }
        catch (Exception e) {
            logger.error("error executing recorded login flow " + e.getMessage());
            throw new Exception("error executing recorded login flow " + e.getMessage());
        }
    }

    public static String fetchToken() throws Exception {

        String fileContent;
        Double createdAt;

        try {
            String filePath = new File("").getAbsolutePath();
            String fileUrl = filePath.concat("/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/recordedFlowOutput.txt");
            
            FileInputStream fstream = new FileInputStream(fileUrl);

            String error = null;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {
                String line;
                while ((line = br.readLine()) != null) {
                   if(line.toLowerCase().contains("error") && !line.toLowerCase().contains("return")){
                        error = line;
                        break;
                   }
                }
            }

            if (error != null) {
                throw new Exception("error executing recording" + error);
            }

        } catch (IOException e) {
            logger.error("error processing file operations " + e.getMessage());
            throw new Exception("error fetching token data, " + e.getMessage());
        }
        try {
            String filePath = new File("").getAbsolutePath();
            String fileUrl = filePath.concat("/apps/dashboard/src/main/java/com/akto/action/testing/recordedLoginFlowScript/output.json");
            fileContent = FileUtils.readFileToString(new File(fileUrl), StandardCharsets.UTF_8);

            Map<String, Object> json = gson.fromJson(fileContent, Map.class);

            createdAt = (Double) json.get("created_at");

            if(createdAt.intValue() < (Context.now() - 5 * 60)) {
                logger.error("old token data");
                throw new Exception("old token data");
            }
            
            return (String) json.get("token");

          } catch (IOException e) {
            logger.error("error processing file operations " + e.getMessage());
            throw new Exception("error fetching token data, " + e.getMessage());
          } catch (Exception e) {
            logger.error("error fetching recorded flow output " + e.getMessage());
            throw new Exception("token data not found, " + e.getMessage());
          }
    }
}
