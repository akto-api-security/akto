package com.akto.detection;

import com.akto.log.LoggerMaker;
import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Does the classifier call that the ingestion thread refuses to wait for.
 *
 * Takes parameters off the queue, asks the classifier about them, and records the answers in the
 * cache the ingestion thread reads. This is the only place that blocks on the classifier, and it
 * runs on its own thread, so a slow or unavailable one costs queue depth rather than throughput.
 *
 * A parameter the classifier declines to refine is remembered too. That negative answer is what
 * stops the runtime asking about the same parameter over and over.
 *
 * The consumer group is unique per process and starts from the end of the queue. This is not work to
 * be divided up: each instance needs answers for the parameters its own traffic touches, and a
 * shared group would hand a parameter to one instance while another kept asking about it forever.
 * Starting at the end is deliberate too - a restarted instance rebuilds its cache from the traffic
 * it actually sees rather than replaying every parameter ever queued.
 */
public class ClassificationWorker implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ClassificationWorker.class);
    private static final Gson gson = new Gson();

    private final String brokerUrl;
    private final String topic;
    private final String groupId;
    private final DetectionCorrector corrector;
    private final ParamVerdictCache cache;

    private volatile boolean running = true;

    public ClassificationWorker(String brokerUrl, String topic, String groupId,
                                DetectionCorrector corrector, ParamVerdictCache cache) {
        this.brokerUrl = brokerUrl;
        this.topic = topic;
        this.groupId = groupId;
        this.corrector = corrector;
        this.cache = cache;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            loggerMaker.info("[detection-corrector] classification worker started: topic=" + topic
                    + " group=" + groupId);

            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) continue;

                // The ingestion side already sends one request per parameter, but several batches can
                // queue up between polls, so collapse again before spending classifier calls.
                Map<String, DetectionCandidate> byParam = new LinkedHashMap<>();
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        DetectionCandidate candidate = gson.fromJson(record.value(), DetectionCandidate.class);
                        if (candidate != null && candidate.getLocation() != null) {
                            byParam.putIfAbsent(candidate.getLocation().asKey(), candidate);
                        }
                    } catch (Exception e) {
                        loggerMaker.error("[detection-corrector] unreadable candidate, skipping: " + e.getMessage());
                    }
                }
                if (byParam.isEmpty()) continue;

                classify(new ArrayList<>(byParam.values()));
            }
        } catch (Exception e) {
            loggerMaker.error("[detection-corrector] classification worker stopped: " + e.getMessage());
        }
        loggerMaker.info("[detection-corrector] classification worker exited");
    }

    private void classify(List<DetectionCandidate> candidates) {
        long startedAtMs = System.currentTimeMillis();

        // Re-index: the classifier answers by position in the list it was handed, and these arrived
        // from unrelated batches with meaningless indexes of their own.
        List<DetectionCandidate> request = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            DetectionCandidate c = candidates.get(i);
            request.add(new DetectionCandidate(i, c.getJsonPath(), c.getValue(), c.getType(), c.getLocation()));
        }

        Map<Integer, String> answers;
        try {
            Map<Integer, String> result = corrector.correct(request);
            answers = result == null ? Collections.<Integer, String>emptyMap() : result;
        } catch (Exception e) {
            // Nothing is recorded, so these parameters stay unknown and are offered again next time
            // their traffic is seen.
            loggerMaker.error("[detection-corrector] classifier call failed for " + request.size()
                    + " parameters, leaving them unclassified: " + e.getMessage());
            return;
        }

        int refined = 0;
        for (int i = 0; i < request.size(); i++) {
            DetectionCandidate c = request.get(i);
            String label = answers.get(i);
            boolean declined = (label == null || label.isEmpty() || label.equals(c.getType()));

            cache.put(c.getLocation(), declined ? ParamVerdictCache.NO_CORRECTION : label);
            if (!declined) refined++;

            if (DetectionCorrectorRegistry.isDebugEnabled()) {
                loggerMaker.info("[detection-corrector] learned " + c.getLocation() + " -> "
                        + (declined ? "no correction" : label));
            }
        }

        loggerMaker.info("[detection-corrector] classified parameters=" + request.size()
                + " refined=" + refined
                + " declined=" + (request.size() - refined)
                + " knownParams=" + cache.size()
                + " tookMs=" + (System.currentTimeMillis() - startedAtMs));
    }
}
