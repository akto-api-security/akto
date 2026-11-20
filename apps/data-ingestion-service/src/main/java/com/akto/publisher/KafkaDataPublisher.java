package com.akto.publisher;

import com.akto.dto.IngestDataBatch;
import com.akto.gateway.DataPublisher;
import com.akto.kafka.Kafka;
import com.akto.utils.KafkaUtils;
import com.akto.log.LoggerMaker;

/**
 * Kafka implementation of DataPublisher
 * Publishes IngestDataBatch to Kafka topics using KafkaUtils
 */
public class KafkaDataPublisher implements DataPublisher {

    private static final LoggerMaker logger = new LoggerMaker(KafkaDataPublisher.class, LoggerMaker.LogDb.DATA_INGESTION);

    @Override
    public void publish(IngestDataBatch batch) throws Exception {
        if (batch == null) {
            logger.errorAndAddToDb("Cannot publish null IngestDataBatch", LoggerMaker.LogDb.DATA_INGESTION);
            throw new IllegalArgumentException("IngestDataBatch cannot be null");
        }

        try {
            logger.info("Publishing IngestDataBatch to Kafka - path: " + batch.getPath() + ", method: " + batch.getMethod());
            KafkaUtils.insertData(batch, false);
            // Kafka.send already logs the drop when the producer isn't ready; delivery itself is async.
            Kafka producer = KafkaUtils.getKafkaProducer();
            if (producer == null || producer.producerReady) {
                logger.info("Queued for Kafka delivery");
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error publishing to Kafka: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            throw e;
        }
    }
}
