package com.akto.gateway;

import com.akto.dto.IngestDataBatch;

/**
 * Interface for publishing ingestion data to external systems (e.g., Kafka)
 * This allows Gateway to publish data without depending on specific implementations
 */
public interface DataPublisher {
    /**
     * Publishes an IngestDataBatch to the data pipeline
     *
     * @param batch The data batch to publish
     * @throws Exception if publishing fails
     */
    void publish(IngestDataBatch batch) throws Exception;
}
