package com.akto.action;

import java.util.List;

import com.akto.data_actor.ClientActor;
import com.akto.dto.IngestDataBatch;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.utils.KafkaUtils;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import org.apache.commons.lang3.StringUtils;

@lombok.Getter
@lombok.Setter
public class IngestionAction extends ActionSupport {
    List<IngestDataBatch> batchData;
    private static final LoggerMaker loggerMaker = new LoggerMaker(IngestionAction.class, LoggerMaker.LogDb.DATA_INGESTION);

    private static int MAX_INFO_PRINT = 500000;
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

                payload.setTag(setObserveMode(payload.getTag()));
                KafkaUtils.insertData(payload, Boolean.TRUE.equals(payload.getPublishToGuardrails()));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while inserting data to Kafka: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    private static String setObserveMode(String tag) {
        try {
            BasicDBObject tagObj = (tag != null && !tag.isEmpty()) ? BasicDBObject.parse(tag) : new BasicDBObject();
            tagObj.put(Constants.AKTO_GUARDRAIL_MODE, Constants.AKTO_GUARDRAIL_MODE_OBSERVE);
            return tagObj.toJson();
        } catch (Exception e) {
            return tag;
        }
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
        Integer id = ClientActor.getAbstractorAccountIdFromEnvOrNull();
        return id != null ? id : 0;
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

    public String authCheck() {
        success = true;
        return Action.SUCCESS.toUpperCase();
    }
    
}
