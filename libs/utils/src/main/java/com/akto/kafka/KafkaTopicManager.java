package com.akto.kafka;

import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Creates the Kafka topics a service uses when they do not exist yet.
 *
 * Amazon MSK ships with auto.create.topics.enable=false, so topics that a self-managed broker
 * creates on first use are never created there, and every producer and consumer of them fails
 * until someone creates them by hand. Services call {@link #ensureTopics(String, Collection)} once
 * on startup, before building their producers, with the topics they use.
 *
 * Enabled for Amazon MSK IAM by default and controlled by {@value #ENV_CREATE_TOPICS}; see
 * {@link #isEnabled()}. Existing topics are never modified, and a topic created concurrently by
 * another instance counts as present. Never throws: failures are returned in the {@link Result}
 * so the caller can report them and carry on, exactly as it would have without this check.
 */
public final class KafkaTopicManager {

    /** Unset: only for Amazon MSK IAM. {@code true}: for any broker. {@code false}: never. */
    public static final String ENV_CREATE_TOPICS = "AKTO_KAFKA_CREATE_TOPICS";
    /** Partitions for created topics; unset or not positive uses the broker's num.partitions. */
    public static final String ENV_TOPIC_PARTITIONS = "AKTO_KAFKA_TOPIC_PARTITIONS";
    /** Replication factor for created topics; unset or not positive uses default.replication.factor. */
    public static final String ENV_TOPIC_REPLICATION_FACTOR = "AKTO_KAFKA_TOPIC_REPLICATION_FACTOR";

    static final int ATTEMPTS = 3;
    static final int REQUEST_TIMEOUT_MS = 15_000;
    static final long RETRY_BACKOFF_MS = 5_000;

    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaTopicManager.class);

    private KafkaTopicManager() {
    }

    /** Outcome of ensuring topics: the topics this call created and the ones it could not ensure. */
    public static final class Result {
        private final Set<String> created;
        private final Map<String, String> failed;

        Result(Set<String> created, Map<String, String> failed) {
            this.created = Collections.unmodifiableSet(new LinkedHashSet<>(created));
            this.failed = Collections.unmodifiableMap(new LinkedHashMap<>(failed));
        }

        static Result empty() {
            return new Result(Collections.emptySet(), Collections.emptyMap());
        }

        static Result allFailed(Collection<String> topics, String reason) {
            Map<String, String> failed = new LinkedHashMap<>();
            topics.forEach(topic -> failed.put(topic, reason));
            return new Result(Collections.emptySet(), failed);
        }

        /** Topics created by this call. */
        public Set<String> getCreated() {
            return created;
        }

        /** Topics that could not be confirmed or created, with the reason. */
        public Map<String, String> getFailed() {
            return failed;
        }

        /** True when every requested topic exists. */
        public boolean isSuccess() {
            return failed.isEmpty();
        }
    }

    /**
     * Whether services should check for and create their topics, from {@value #ENV_CREATE_TOPICS}
     * and the Kafka authentication settings.
     */
    public static boolean isEnabled() {
        boolean mskIam = KafkaConfig.isKafkaAuthenticationEnabled() && KafkaConfig.isMskIamAuthentication();
        return isEnabled(System.getenv(ENV_CREATE_TOPICS), mskIam);
    }

    static boolean isEnabled(String setting, boolean mskIam) {
        if (setting != null) {
            String value = setting.trim();
            if (value.equalsIgnoreCase("true")) {
                return true;
            }
            if (value.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return mskIam;
    }

    /**
     * Makes sure the given topics exist on the cluster at {@code brokerUrl}, creating missing ones.
     * Authenticates exactly like the other Kafka clients ({@link KafkaConfig#createAdminProperties}),
     * including Amazon MSK IAM. Retries {@value #ATTEMPTS} times. Never throws.
     */
    public static Result ensureTopics(String brokerUrl, Collection<String> topics) {
        Set<String> wanted = normalize(topics);
        if (wanted.isEmpty()) {
            return Result.empty();
        }

        Properties props = KafkaConfig.createAdminProperties(brokerUrl, KafkaConfig.isKafkaAuthenticationEnabled(),
                KafkaConfig.getKafkaUsername(), KafkaConfig.getKafkaPassword());
        if (props == null) {
            return Result.allFailed(wanted, "kafka authentication is enabled but credentials are missing");
        }
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS);

        Optional<Integer> partitions = parsePositiveInt(System.getenv(ENV_TOPIC_PARTITIONS));
        Optional<Short> replicationFactor = parsePositiveInt(System.getenv(ENV_TOPIC_REPLICATION_FACTOR))
                .map(Integer::shortValue);

        try (Admin admin = Admin.create(props)) {
            return ensureTopicsWithRetries(admin, wanted, partitions, replicationFactor, ATTEMPTS, RETRY_BACKOFF_MS);
        } catch (Exception e) {
            return Result.allFailed(wanted, describe(e));
        }
    }

    static Result ensureTopicsWithRetries(Admin admin, Set<String> topics, Optional<Integer> partitions,
            Optional<Short> replicationFactor, int attempts, long backoffMs) {
        Set<String> created = new LinkedHashSet<>();
        Result result = Result.empty();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            result = ensureTopics(admin, topics, partitions, replicationFactor, REQUEST_TIMEOUT_MS);
            // A later attempt sees topics created by an earlier one as existing, so collect them here.
            created.addAll(result.getCreated());
            if (result.isSuccess()) {
                break;
            }
            loggerMaker.warn("Could not ensure Kafka topics exist (attempt " + attempt + "/" + attempts + "): "
                    + result.getFailed());
            if (attempt < attempts && !sleep(backoffMs)) {
                break;
            }
        }
        return new Result(created, result.getFailed());
    }

    /** One pass: lists the cluster's topics and creates the missing ones. */
    static Result ensureTopics(Admin admin, Set<String> topics, Optional<Integer> partitions,
            Optional<Short> replicationFactor, long timeoutMs) {
        Set<String> existing;
        try {
            existing = admin.listTopics().names().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.allFailed(topics, "interrupted while listing topics");
        } catch (Exception e) {
            return Result.allFailed(topics, "list topics: " + describe(e));
        }

        // A topic this client is not allowed to describe is not listed either. Trying to create it is
        // still right: the broker then answers TopicExists (fine) or an authorization error (reported).
        List<NewTopic> missing = topics.stream()
                .filter(topic -> !existing.contains(topic))
                .map(topic -> new NewTopic(topic, partitions, replicationFactor))
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            return Result.empty();
        }

        Map<String, KafkaFuture<Void>> futures = admin.createTopics(missing).values();
        Set<String> created = new LinkedHashSet<>();
        Map<String, String> failed = new LinkedHashMap<>();
        for (NewTopic topic : missing) {
            String name = topic.name();
            try {
                futures.get(name).get(timeoutMs, TimeUnit.MILLISECONDS);
                created.add(name);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    failed.put(name, describe(e));
                }
                // TopicExists: created by another instance since we listed; that is success.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.put(name, "interrupted while creating topic");
            } catch (Exception e) {
                failed.put(name, describe(e));
            }
        }
        return new Result(created, failed);
    }

    static Optional<Integer> parsePositiveInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            loggerMaker.warn("Ignoring invalid Kafka topic setting '" + value + "', using the broker default");
            return Optional.empty();
        }
    }

    private static Set<String> normalize(Collection<String> topics) {
        Set<String> wanted = new LinkedHashSet<>();
        if (topics != null) {
            for (String topic : topics) {
                if (topic != null && !topic.trim().isEmpty()) {
                    wanted.add(topic.trim());
                }
            }
        }
        return wanted;
    }

    private static String describe(Throwable t) {
        Throwable cause = (t instanceof ExecutionException && t.getCause() != null) ? t.getCause() : t;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
