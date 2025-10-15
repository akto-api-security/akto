package com.akto.action;

import java.util.Base64;
import java.util.List;

import com.akto.log.LoggerMaker;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import com.akto.dto.IngestDataBatch;
import com.akto.utils.KafkaUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

@lombok.Getter
@lombok.Setter
public class IngestionAction extends ActionSupport {
    List<IngestDataBatch> batchData;
    private static final LoggerMaker loggerMaker = new LoggerMaker(IngestionAction.class, LoggerMaker.LogDb.DATA_INGESTION);

    private static int MAX_INFO_PRINT = 5000;
    private boolean success;

    private static final int ACCOUNT_ID_TO_ADD_DEFAULT_DATA = getAccountId();

    public String ingestData() {
        try {
            printLogs("ingestData batch size " + batchData.size());
            for (IngestDataBatch payload: batchData) {
                printLogs("Inserting data to kafka...");

                // Adding this if we are getting empty method from traffic connector
                if(ACCOUNT_ID_TO_ADD_DEFAULT_DATA == 1745303931 && StringUtils.isEmpty(payload.getMethod())) {
                    payload.setMethod("POST");
                }

                if(ACCOUNT_ID_TO_ADD_DEFAULT_DATA == 1745303931 && StringUtils.isEmpty(payload.getPath())) {
                    payload.setPath("/");
                }

                KafkaUtils.insertData(payload);
                printLogs("Data has been inserted to kafka.");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while inserting data to Kafka: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    private static void printLogs(String msg) {
        MAX_INFO_PRINT--;
        if(MAX_INFO_PRINT > 0) {
            loggerMaker.warnAndAddToDb(msg);
        }

        if(MAX_INFO_PRINT == 0) {
            loggerMaker.warnAndAddToDb("Debug log print limit reached.");
        }
    }

    public static int getAccountId() {
        try {
            String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            DecodedJWT jwt = JWT.decode(token);
            String payload = jwt.getPayload();
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String decodedPayload = new String(decodedBytes);
            BasicDBObject basicDBObject = BasicDBObject.parse(decodedPayload);
            return (int) basicDBObject.getInt("accountId");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("checkaccount error" + e.getStackTrace());
            return 0;
        }
    }

    public List<IngestDataBatch> getBatchData() {
        return batchData;
    }

    public void setBatchData(List<IngestDataBatch> batchData) {
        this.batchData = batchData;
    }

    public String healthCheck() {
        success = true;
        return Action.SUCCESS.toUpperCase();
    }
    
}
