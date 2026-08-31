package com.akto.detection;

/**
 * Interface for publishing locally-detected candidates to an async classifier.
 * Allows DetectionBatch (in libs/dao) to publish without depending on LocalDetectionPublisher (in libs/utils).
 */
public interface CandidatePublisher {
    void publish(DetectionCandidate candidate);
}
