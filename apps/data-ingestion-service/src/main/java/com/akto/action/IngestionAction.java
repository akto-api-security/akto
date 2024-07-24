package com.akto.action;

import java.util.List;

import com.akto.dao.context.Context;
import com.akto.kafka.Kafka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.IngestDataBatch;
import com.akto.utils.KafkaUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

public class IngestionAction extends ActionSupport {
    List<IngestDataBatch> batchData;
    private static final Logger logger = LoggerFactory.getLogger(IngestionAction.class);

    public String ingestData() {
        try {
            logger.info("ingestData batch size " + batchData.size());
            for (IngestDataBatch payload: batchData) {
                KafkaUtils.insertData(payload);
            }
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public List<IngestDataBatch> getBatchData() {
        return batchData;
    }

    public void setBatchData(List<IngestDataBatch> batchData) {
        this.batchData = batchData;
    }
    
}
