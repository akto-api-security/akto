package com.akto.detection;

import com.akto.dto.AccountSettings;
import com.akto.dto.settings.DetectionCorrectorSettings;
import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Builds the detection corrector from account settings and installs it.
 *
 * Called on the same refresh cadence as custom data types, so a configuration change reaches the
 * runtime within one refresh interval. Nothing is rebuilt when the configuration has not changed,
 * because that would throw away everything already learned about each parameter and send
 * classification volume back to cold-start levels.
 *
 * Also owns the worker thread that keeps the classifier off the ingestion path, and stops it again
 * when the feature is reconfigured or switched off.
 */
public class DetectionCorrectorInstaller {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DetectionCorrectorInstaller.class);

    private static volatile String installedSignature = null;

    private static ClassificationWorker worker = null;
    private static Thread workerThread = null;
    private static KafkaCandidatePublisher publisher = null;

    /**
     * Where Kafka is. Unlike the classifier settings this is deployment topology rather than account
     * configuration, so it is handed over by the process rather than read from settings.
     */
    private static volatile String kafkaBrokerUrl = null;

    private DetectionCorrectorInstaller() {
    }

    public static void setKafkaBrokerUrl(String brokerUrl) {
        kafkaBrokerUrl = brokerUrl;
    }

    public static synchronized void refresh(AccountSettings accountSettings) {
        try {
            DetectionCorrectorConfig config = fromAccountSettings(accountSettings);

            if (config == null || !config.isUsable()) {
                if (installedSignature != null) {
                    loggerMaker.info("detection corrector disabled, reverting to local detection only");
                    stop();
                    installedSignature = null;
                }
                return;
            }

            if (kafkaBrokerUrl == null || kafkaBrokerUrl.trim().isEmpty()) {
                loggerMaker.error("[detection-corrector] no kafka broker was provided by this process, "
                        + "cannot classify; staying on local detection only");
                return;
            }

            String signature = config.toString();
            if (signature.equals(installedSignature)) return;

            stop();
            start(config);
            installedSignature = signature;
        } catch (Exception e) {
            loggerMaker.error("failed installing detection corrector, keeping local detection only: "
                    + e.getMessage());
        }
    }

    /**
     * Brings up the cache, the queue, and the worker, then points the ingestion path at them.
     *
     * The registry is installed last on purpose: until it is, nothing is queued, so there is no
     * window where values are asked about with no one listening.
     */
    private static void start(DetectionCorrectorConfig config) {
        try {
            if (config.getAuthToken() == null || config.getAuthToken().trim().isEmpty()) {
                // Not fatal: some deployments front the service with mTLS or a service mesh instead.
                loggerMaker.info("[detection-corrector] no auth token configured, "
                        + "calling without an Authorization header");
            }

            ensureTopicExists(config.getCandidateTopic(), config.getCandidateTopicPartitions());

            ParamVerdictCache cache = new ParamVerdictCache(config.getParamCacheSize(),
                    config.getParamCacheTtlSeconds());
            DetectionCorrector corrector = new HttpDetectionCorrector(config);
            publisher = new KafkaCandidatePublisher(kafkaBrokerUrl, config.getCandidateTopic());

            // A group per process, not a shared one: each instance needs answers for the parameters
            // its own traffic touches, and a shared group would hand a parameter to one instance
            // while another kept asking about it forever.
            worker = new ClassificationWorker(kafkaBrokerUrl, config.getCandidateTopic(),
                    "akto-detection-classifier-" + UUID.randomUUID(), corrector, cache);

            workerThread = new Thread(worker, "detection-classification-worker");
            workerThread.setDaemon(true);
            workerThread.start();

            DetectionCorrectorRegistry.install(corrector, cache, publisher);

            loggerMaker.info("detection corrector installed: " + config
                    + " brokers=" + kafkaBrokerUrl
                    + " paramCacheSize=" + config.getParamCacheSize()
                    + " paramCacheTtlSeconds=" + config.getParamCacheTtlSeconds());
        } catch (Exception e) {
            loggerMaker.error("[detection-corrector] could not start, keeping local detection only: "
                    + e.getMessage());
            stop();
        }
    }

    /**
     * Creates the candidate topic if it is not there already.
     *
     * Deployments cannot always add a topic to their Kafka provisioning, and relying on the broker
     * to auto-create it is not good enough: auto-creation may be switched off, and where it is on it
     * uses broker defaults - usually a single partition. Partitions are what let more than one
     * runtime instance share classification work, and the count cannot be raised later without a
     * topic operation, so it is worth asking for the right one up front.
     *
     * Never fatal. An existing topic is the normal case, not an error, and any other failure leaves
     * the feature to start anyway: the topic may exist with permissions that hide it from us, or
     * auto-creation may cover it.
     */
    private static void ensureTopicExists(String topic, int partitions) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);

        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, partitions, (short) 1)))
                    .all().get(15, TimeUnit.SECONDS);
            loggerMaker.info("[detection-corrector] created topic " + topic
                    + " with " + partitions + " partition(s)");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // Expected on every restart and every settings refresh after the first.
                return;
            }
            loggerMaker.error("[detection-corrector] could not create topic " + topic
                    + ", continuing in case it already exists: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            loggerMaker.error("[detection-corrector] could not create topic " + topic
                    + ", continuing in case it already exists: " + e.getMessage());
        }
    }

    private static void stop() {
        // Take the ingestion path off first, so nothing is queued while the worker is going away.
        DetectionCorrectorRegistry.reset();

        if (worker != null) worker.stop();
        if (workerThread != null) {
            try {
                // The worker polls on a short timeout, so this is a bounded wait, not a hope.
                workerThread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (publisher != null) publisher.close();

        worker = null;
        workerThread = null;
        publisher = null;
    }

    static DetectionCorrectorConfig fromAccountSettings(AccountSettings accountSettings) {
        if (accountSettings == null) return null;

        DetectionCorrectorSettings settings = accountSettings.getDetectionCorrector();
        if (settings == null) return null;

        DetectionCorrectorConfig config = new DetectionCorrectorConfig();
        config.setEnabled(settings.isEnabled());
        config.setUrl(settings.getUrl());
        config.setAuthToken(settings.getAuthToken());
        config.setTriggerSubTypesFromList(settings.getTriggerTypes());
        config.setTypeAliases(settings.getTypeAliases());

        // The setters below ignore non-positive or blank values, so an unset field keeps the
        // built-in default.
        config.setTimeoutMs(settings.getTimeoutMs());
        config.setMaxBatchSize(settings.getMaxBatchSize());
        config.setFailureThreshold(settings.getFailureThreshold());
        config.setBreakerCoolOffSeconds(settings.getBreakerCoolOffSeconds());
        config.setCandidateTopic(settings.getCandidateTopic());
        config.setCandidateTopicPartitions(settings.getCandidateTopicPartitions());
        config.setParamCacheSize(settings.getParamCacheSize());
        config.setParamCacheTtlSeconds(settings.getParamCacheTtlSeconds());

        DetectionCorrectorRegistry.setDebugEnabled(settings.isDebug());
        return config;
    }
}
