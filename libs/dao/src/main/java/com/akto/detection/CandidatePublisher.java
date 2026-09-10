package com.akto.detection;

import java.util.List;

/**
 * Hands values that need classifying to whatever transport carries them out of the runtime.
 *
 * Exists so {@link DetectionBatch}, which lives in the dao module, can queue work for the external
 * classifier without knowing that Kafka is involved. The Kafka implementation lives in the utils
 * module alongside the rest of the transport code.
 */
public interface CandidatePublisher {

    /** Queues these values for classification. Must not block the caller. */
    void publish(List<DetectionCandidate> candidates);
}
