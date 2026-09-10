package com.akto.detection;

import com.akto.log.LoggerMaker;
import com.google.gson.Gson;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Puts parameters that need classifying onto a Kafka topic.
 *
 * This is the hand-off that keeps the classifier off the ingestion path: the runtime writes the
 * request and moves on, and {@link ClassificationWorker} does the waiting elsewhere.
 *
 * One request per parameter is enough to classify it, so a parameter already asked about recently is
 * not asked about again. Without that, a busy endpoint would put one message on the topic per
 * request until the answer came back - exactly the per-value volume this design exists to avoid.
 * The suppression expires, so a parameter still unanswered later (a classifier that was down, say)
 * is eventually asked about again.
 *
 * The record key is the parameter, so everything about one parameter lands on one partition.
 */
public class KafkaCandidatePublisher implements CandidatePublisher {

    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaCandidatePublisher.class);
    private static final Gson gson = new Gson();

    /**
     * How long to wait before asking about the same parameter again. Long enough for an answer to
     * come back under normal conditions, short enough that a parameter is retried while a classifier
     * outage is being fixed.
     */
    static final long REASK_AFTER_MS = 60_000L;

    private static final int MAX_TRACKED_PARAMS = 100_000;

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final Map<String, Long> askedAtMs;

    public KafkaCandidatePublisher(String brokerUrl, String topic) {
        this.topic = topic;

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Classification is advisory, so durability is traded for never stalling ingestion: one ack,
        // and a send that fails fast rather than waiting for buffer space.
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000);
        this.producer = new KafkaProducer<>(props);

        this.askedAtMs = Collections.synchronizedMap(new LinkedHashMap<String, Long>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_TRACKED_PARAMS;
            }
        });

        loggerMaker.info("[detection-corrector] candidate publisher ready: topic=" + topic
                + " brokers=" + brokerUrl);
    }

    @Override
    public void publish(List<DetectionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return;

        for (DetectionCandidate candidate : candidates) {
            if (candidate.getLocation() == null) continue;
            String key = candidate.getLocation().asKey();
            if (askedRecently(key)) continue;

            try {
                producer.send(new ProducerRecord<>(topic, key, gson.toJson(candidate)), (metadata, exception) -> {
                    if (exception != null) {
                        loggerMaker.error("[detection-corrector] could not queue parameter " + key
                                + " for classification: " + exception.getMessage());
                    }
                });
            } catch (Exception e) {
                loggerMaker.error("[detection-corrector] could not queue parameter " + key
                        + " for classification: " + e.getMessage());
            }
        }
    }

    /** Whether this parameter has been asked about recently, without recording a new ask. */
    boolean wasAskedRecently(String key) {
        synchronized (askedAtMs) {
            Long previous = askedAtMs.get(key);
            return previous != null && System.currentTimeMillis() - previous < REASK_AFTER_MS;
        }
    }

    /** Records that we are asking now, and reports whether we already asked recently. */
    private boolean askedRecently(String key) {
        long now = System.currentTimeMillis();
        synchronized (askedAtMs) {
            Long previous = askedAtMs.get(key);
            if (previous != null && now - previous < REASK_AFTER_MS) return true;
            askedAtMs.put(key, now);
            return false;
        }
    }

    public void close() {
        try {
            producer.close();
        } catch (Exception e) {
            loggerMaker.error("[detection-corrector] error closing candidate publisher: " + e.getMessage());
        }
    }
}
