package com.akto.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KafkaTopicManagerTest {

    private static final String LOGS = "akto.api.logs";
    private static final String THREAT = "akto.api.logs2";
    private static final String HEARTBEATS = "akto.daemonset.producer.heartbeats";
    private static final Set<String> ALL = new LinkedHashSet<>(Arrays.asList(LOGS, THREAT, HEARTBEATS));

    private Admin admin;

    @BeforeEach
    public void setUp() {
        admin = mock(Admin.class);
    }

    // ---- enablement --------------------------------------------------------------------------

    @Test
    public void testEnabledOnlyForMskIamByDefault() {
        assertFalse(KafkaTopicManager.isEnabled(null, false), "plain Kafka must be unchanged");
        assertTrue(KafkaTopicManager.isEnabled(null, true), "Amazon MSK IAM");
    }

    @Test
    public void testEnabledSettingOverridesDefault() {
        assertTrue(KafkaTopicManager.isEnabled("true", false));
        assertTrue(KafkaTopicManager.isEnabled(" TRUE ", false));
        assertFalse(KafkaTopicManager.isEnabled("false", true));
        assertFalse(KafkaTopicManager.isEnabled("False", true));
        // anything else falls back to the default
        assertTrue(KafkaTopicManager.isEnabled("yes", true));
        assertFalse(KafkaTopicManager.isEnabled("", false));
    }

    @Test
    public void testParsePositiveInt() {
        assertEquals(Optional.of(3), KafkaTopicManager.parsePositiveInt("3"));
        assertEquals(Optional.of(3), KafkaTopicManager.parsePositiveInt(" 3 "));
        assertEquals(Optional.empty(), KafkaTopicManager.parsePositiveInt(null));
        assertEquals(Optional.empty(), KafkaTopicManager.parsePositiveInt(""));
        assertEquals(Optional.empty(), KafkaTopicManager.parsePositiveInt("-1"), "-1 means broker default");
        assertEquals(Optional.empty(), KafkaTopicManager.parsePositiveInt("0"));
        assertEquals(Optional.empty(), KafkaTopicManager.parsePositiveInt("abc"));
    }

    // ---- single pass -------------------------------------------------------------------------

    @Test
    public void testAllTopicsPresentCreatesNothing() {
        givenExistingTopics(LOGS, THREAT, HEARTBEATS, "other.topic");

        KafkaTopicManager.Result result = ensure(ALL);

        assertTrue(result.isSuccess());
        assertTrue(result.getCreated().isEmpty());
        verify(admin, never()).createTopics(anyCollection());
    }

    @Test
    public void testCreatesOnlyMissingTopicsWithBrokerDefaults() {
        givenExistingTopics(LOGS);
        givenCreateOutcomes(Collections.emptyMap());

        KafkaTopicManager.Result result = ensure(ALL);

        assertTrue(result.isSuccess());
        assertEquals(new LinkedHashSet<>(Arrays.asList(THREAT, HEARTBEATS)), result.getCreated());
        List<NewTopic> requested = capturedCreateRequest();
        assertEquals(Arrays.asList(THREAT, HEARTBEATS), names(requested));
        for (NewTopic topic : requested) {
            assertEquals(-1, topic.numPartitions(), "unset partitions use the broker default");
            assertEquals(-1, topic.replicationFactor(), "unset replication factor uses the broker default");
        }
    }

    @Test
    public void testPassesConfiguredPartitionsAndReplicationFactor() {
        givenExistingTopics();
        givenCreateOutcomes(Collections.emptyMap());

        KafkaTopicManager.ensureTopics(admin, Collections.singleton(LOGS), Optional.of(6), Optional.of((short) 3), 1000);

        NewTopic topic = capturedCreateRequest().get(0);
        assertEquals(6, topic.numPartitions());
        assertEquals(3, topic.replicationFactor());
    }

    @Test
    public void testTopicCreatedConcurrentlyCountsAsPresent() {
        givenExistingTopics();
        givenCreateOutcomes(failure(LOGS, new TopicExistsException("exists")));

        KafkaTopicManager.Result result = ensure(ALL);

        assertTrue(result.isSuccess(), "TopicExists from another instance is not a failure");
        assertEquals(new LinkedHashSet<>(Arrays.asList(THREAT, HEARTBEATS)), result.getCreated());
    }

    @Test
    public void testCreateDeniedIsReportedAndOtherTopicsAreStillCreated() {
        givenExistingTopics();
        givenCreateOutcomes(failure(THREAT, new TopicAuthorizationException("Authorization failed.")));

        KafkaTopicManager.Result result = ensure(ALL);

        assertFalse(result.isSuccess());
        assertEquals(Collections.singleton(THREAT), result.getFailed().keySet());
        assertTrue(result.getFailed().get(THREAT).contains("TopicAuthorizationException"));
        assertEquals(new LinkedHashSet<>(Arrays.asList(LOGS, HEARTBEATS)), result.getCreated());
    }

    @Test
    public void testListFailureFailsAllTopicsWithoutCreating() {
        givenListFails(new RuntimeException("connection refused"));

        KafkaTopicManager.Result result = ensure(ALL);

        assertEquals(ALL, result.getFailed().keySet());
        assertTrue(result.getCreated().isEmpty());
        verify(admin, never()).createTopics(anyCollection());
    }

    // ---- retries -----------------------------------------------------------------------------

    @Test
    public void testRetriesUntilClusterReachable() {
        ListTopicsResult unreachable = listResult(failedFuture(new RuntimeException("timeout")));
        ListTopicsResult reachable = listResult(KafkaFuture.completedFuture(new HashSet<>()));
        when(admin.listTopics()).thenReturn(unreachable, reachable);
        givenCreateOutcomes(Collections.emptyMap());

        KafkaTopicManager.Result result = KafkaTopicManager.ensureTopicsWithRetries(
                admin, ALL, Optional.empty(), Optional.empty(), 3, 0);

        assertTrue(result.isSuccess());
        assertEquals(ALL, result.getCreated());
        verify(admin, times(2)).listTopics();
    }

    @Test
    public void testRetryReportsTopicsCreatedInEarlierAttempts() {
        // attempt 1: nothing exists, THREAT is denied; attempt 2: the others now exist, THREAT succeeds
        ListTopicsResult firstList = listResult(KafkaFuture.completedFuture(new HashSet<>()));
        ListTopicsResult secondList = listResult(KafkaFuture.completedFuture(new HashSet<>(Arrays.asList(LOGS, HEARTBEATS))));
        when(admin.listTopics()).thenReturn(firstList, secondList);
        CreateTopicsResult firstCreate = createResult(ALL, failure(THREAT, new TopicAuthorizationException("denied")));
        CreateTopicsResult secondCreate = createResult(Collections.singleton(THREAT), Collections.emptyMap());
        when(admin.createTopics(anyCollection())).thenReturn(firstCreate, secondCreate);

        KafkaTopicManager.Result result = KafkaTopicManager.ensureTopicsWithRetries(
                admin, ALL, Optional.empty(), Optional.empty(), 3, 0);

        assertTrue(result.isSuccess());
        assertEquals(ALL, result.getCreated());
    }

    @Test
    public void testGivesUpAfterAllAttempts() {
        givenListFails(new RuntimeException("unreachable"));

        KafkaTopicManager.Result result = KafkaTopicManager.ensureTopicsWithRetries(
                admin, ALL, Optional.empty(), Optional.empty(), 3, 0);

        assertFalse(result.isSuccess());
        assertEquals(ALL, result.getFailed().keySet());
        verify(admin, times(3)).listTopics();
    }

    // ---- entry point -------------------------------------------------------------------------

    @Test
    public void testNoTopicsIsANoOpWithoutConnecting() {
        // Unreachable broker: returning immediately proves no admin client was used.
        KafkaTopicManager.Result result = KafkaTopicManager.ensureTopics("127.0.0.1:1",
                Arrays.asList(null, "", "  "));

        assertTrue(result.isSuccess());
        assertTrue(result.getCreated().isEmpty());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private KafkaTopicManager.Result ensure(Set<String> topics) {
        return KafkaTopicManager.ensureTopics(admin, topics, Optional.empty(), Optional.empty(), 1000);
    }

    private void givenExistingTopics(String... topics) {
        Set<String> names = new HashSet<>(Arrays.asList(topics));
        ListTopicsResult result = listResult(KafkaFuture.completedFuture(names));
        when(admin.listTopics()).thenReturn(result);
    }

    private void givenListFails(Exception e) {
        ListTopicsResult result = listResult(failedFuture(e));
        when(admin.listTopics()).thenReturn(result);
    }

    /** Every topic in a create request succeeds unless it has an entry in {@code failures}. */
    private void givenCreateOutcomes(Map<String, Exception> failures) {
        when(admin.createTopics(anyCollection())).thenAnswer(invocation -> {
            Collection<NewTopic> requested = invocation.getArgument(0);
            return createResult(names(new ArrayList<>(requested)), failures);
        });
    }

    private static ListTopicsResult listResult(KafkaFuture<Set<String>> names) {
        ListTopicsResult result = mock(ListTopicsResult.class);
        when(result.names()).thenReturn(names);
        return result;
    }

    private static CreateTopicsResult createResult(Collection<String> topics, Map<String, Exception> failures) {
        Map<String, KafkaFuture<Void>> futures = new HashMap<>();
        for (String topic : topics) {
            futures.put(topic, failures.containsKey(topic)
                    ? failedFuture(failures.get(topic))
                    : KafkaFuture.completedFuture(null));
        }
        CreateTopicsResult result = mock(CreateTopicsResult.class);
        when(result.values()).thenReturn(futures);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<NewTopic> capturedCreateRequest() {
        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    private static <T> KafkaFuture<T> failedFuture(Exception e) {
        KafkaFutureImpl<T> future = new KafkaFutureImpl<>();
        future.completeExceptionally(e);
        return future;
    }

    private static Map<String, Exception> failure(String topic, Exception e) {
        return Collections.singletonMap(topic, e);
    }

    private static List<String> names(List<NewTopic> topics) {
        return topics.stream().map(NewTopic::name).collect(Collectors.toList());
    }
}
