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

    private static int MAX_INFO_PRINT = 500000;
    private boolean success;

    private static final int ACCOUNT_ID_TO_ADD_DEFAULT_DATA = getAccountId();

    private boolean sendLogsToCustomAccount(List<IngestDataBatch> batchData){
        if (batchData == null || batchData.isEmpty()) {
            return false;
        }


        // for (IngestDataBatch batch : batchData) {
        //     String requestHeaders = batch.getRequestHeaders();
        //     if (requestHeaders != null) {
        //         String lowerHeaders = requestHeaders.toLowerCase();
        //         if (lowerHeaders.contains("\"host\":") || lowerHeaders.contains("\"host \":")) {
        //             if (lowerHeaders.contains("hollywoodbets") ||
        //                 lowerHeaders.contains("betsolutions") ||
        //                 lowerHeaders.contains("betnix") ||
        //                 lowerHeaders.contains("betsoft")) {
        //                 return true;
        //             }
        //         }
        //     }
        // }

        return true;
    }

    public String ingestData() {
        try {
            if(sendLogsToCustomAccount(batchData)){
                System.setProperty("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoiaW52aXRlX3VzZXIiLCJhY2NvdW50SWQiOjE2NjI2ODA0NjMsImlhdCI6MTc2MDU5NzM0OCwiZXhwIjoxNzc2MzIyMTQ4fQ.b-aqZEiTinzE1tavKDe6t7Ec7TsnsGoVRdxCiMmeOM20JcJ7aEgOZaJxD7O9zyoD6AEXmpEghd04wGhGCECBOKWivDS8Y_fdatLw8R7hH0Y-pu8QEMC1whbXXJrNhsRGXihLIiQ80nDKbrv6ObbyDwy4NPYoCFK8Mpu2i4W8qZHBJXnxmVkCp8Cp_LyeDLotXvc8DAp9huHASil0BSOxiUwHsw3Efk4BkRlHADfAwGFz4j-ozdbiK0SHHvOZNicl1wgpvDk0nHRLhIg3Ynx-Fk4Pp0agb0MCpS55-CRMBbx3zy9xRdkhIGdOydEzZKK5p311hwPnxxeL6Dp1C2f89g");
            }

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
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while inserting data to Kafka: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public static void printLogs(String msg) {
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
