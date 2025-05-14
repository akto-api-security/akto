package com.akto.action;

import java.util.List;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.Log;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.IngestDataBatch;
import com.akto.utils.KafkaUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

public class IngestionAction extends ActionSupport {
    List<IngestDataBatch> batchData;
    private static final LoggerMaker loggerMaker = new LoggerMaker(IngestionAction.class, LoggerMaker.LogDb.DATA_INGESTION);

    private static int MAX_INFO_PRINT = 500;

    public String ingestData() {
        try {
            printLogs("ingestData batch size " + batchData.size());
            for (IngestDataBatch payload: batchData) {
                printLogs("Inserting data to kafka...");
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
            loggerMaker.infoAndAddToDb(msg, LoggerMaker.LogDb.DATA_INGESTION);
        }

        if(MAX_INFO_PRINT == 0) {
            loggerMaker.infoAndAddToDb("Info log print limit reached.", LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    public List<IngestDataBatch> getBatchData() {
        return batchData;
    }

    public void setBatchData(List<IngestDataBatch> batchData) {
        this.batchData = batchData;
    }
    
}
