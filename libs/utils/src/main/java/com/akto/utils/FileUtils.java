package com.akto.utils;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.utils.Utils;
import com.mongodb.BasicDBObject;
import java.io.File;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileUtils {

    public static File createRequestFile(String originalMessage, String message) {
        try {
            String origCurl = CurlUtils.getCurl(originalMessage);
            String testCurl = CurlUtils.getCurl(message);
            HttpResponseParams origObj = Utils.parseKafkaMessage(originalMessage);
            BasicDBObject testRespObj = BasicDBObject.parse(message);
            BasicDBObject testPayloadObj = BasicDBObject.parse(testRespObj.getString("response"));
            String testResp = testPayloadObj.getString("body");

            File tmpOutputFile = File.createTempFile("output", ".txt");

            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, "Original Curl ----- \n\n", (String) null);
            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, origCurl + "\n\n", (String) null, true);
            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, "Original Api Response ----- \n\n", (String) null, true);
            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, origObj.getPayload() + "\n\n", (String) null, true);

            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, "Test Curl ----- \n\n", (String) null, true);
            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, testCurl + "\n\n", (String) null, true);
            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, "Test Api Response ----- \n\n", (String) null, true);
            org.apache.commons.io.FileUtils.writeStringToFile(tmpOutputFile, testResp + "\n\n", (String) null, true);

            return tmpOutputFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
