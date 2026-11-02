package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.trafficFilter.ParamFilter;
import com.akto.utils.KafkaUtils;
import com.akto.utils.MongoBasedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises {@link DbAction#bulkWriteSampleData()} and {@link DbAction#bulkWriteSensitiveSampleData()}
 * on the Kafka-write path (same logic as POST {@code api/bulkWriteSampleData} /
 * {@code api/bulkWriteSensitiveSampleData}). A recording {@link KafkaUtils} stands in for the broker.
 */
public class SampleDataBloomFilterActionTest extends MongoBasedTest {

    private static final int SAMPLE_API_COLLECTION_ID = 888_001;
    private static final int SENSITIVE_API_COLLECTION_ID = 888_002;
    private static final String URL = "/e2e/bloom-filter-test";
    private static final String METHOD = "GET";

    private RecordingKafkaUtils recordingKafka;

    @Before
    public void setUp() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        recordingKafka = new RecordingKafkaUtils();
    }

    @After
    public void tearDown() {
        Context.accountId.set(ACCOUNT_ID);
    }

    @Test
    public void bulkWriteSampleData_kafkaPath_secondIdenticalApiIsBloomSkipped() throws Exception {
        DbAction action = new DbAction();
        injectKafka(action, recordingKafka);

        List<BulkUpdates> writes = new ArrayList<>();
        writes.add(sampleDataBulkUpdate(SAMPLE_API_COLLECTION_ID));

        action.setWritesForSampleData(writes);
        assertEquals("SUCCESS", action.bulkWriteSampleData());
        assertEquals(1, recordingKafka.insertDataCallCount);
        assertEquals(1, recordingKafka.lastInsertBatchSize);
        assertEquals("bulkWriteSampleData", recordingKafka.lastTriggerMethod);

        action.setWritesForSampleData(new ArrayList<>(writes));
        assertEquals("SUCCESS", action.bulkWriteSampleData());
        assertEquals("Second call should not enqueue Kafka after bloom hit", 1, recordingKafka.insertDataCallCount);
    }

    @Test
    public void bulkWriteSensitiveSampleData_kafkaPath_secondIdenticalApiIsBloomSkipped() throws Exception {
        DbAction action = new DbAction();
        injectKafka(action, recordingKafka);

        List<BulkUpdates> writes = new ArrayList<>();
        writes.add(sensitiveSampleDataBulkUpdate(SENSITIVE_API_COLLECTION_ID));

        action.setWritesForSensitiveSampleData(writes);
        assertEquals("SUCCESS", action.bulkWriteSensitiveSampleData());
        assertEquals(1, recordingKafka.insertDataCallCount);
        assertEquals(1, recordingKafka.lastInsertBatchSize);
        assertEquals("bulkWriteSensitiveSampleData", recordingKafka.lastTriggerMethod);

        action.setWritesForSensitiveSampleData(new ArrayList<>(writes));
        assertEquals("SUCCESS", action.bulkWriteSensitiveSampleData());
        assertEquals(1, recordingKafka.insertDataCallCount);
    }

    @Test
    public void bulkWriteSampleData_kafkaPath_bloomUsesSyntheticParamNotStiParam() throws Exception {
        int collectionId = 888_003;
        assertTrue(ParamFilter.isNewEntry(ACCOUNT_ID, collectionId, URL, METHOD, "email"));
        assertFalse(ParamFilter.isNewEntry(ACCOUNT_ID, collectionId, URL, METHOD, "email"));

        DbAction action = new DbAction();
        injectKafka(action, recordingKafka);
        action.setWritesForSampleData(List.of(sampleDataBulkUpdate(collectionId)));
        assertEquals("SUCCESS", action.bulkWriteSampleData());
        assertEquals(1, recordingKafka.insertDataCallCount);
    }

    private static BulkUpdates sampleDataBulkUpdate(int apiCollectionId) {
        Map<String, Object> id = new HashMap<>();
        id.put("apiCollectionId", apiCollectionId);
        id.put("url", URL);
        id.put("method", METHOD);
        id.put("responseCode", 200);
        id.put("bucketStartEpoch", 1_700_000_000);
        id.put("bucketEndEpoch", 1_700_000_300);

        Map<String, Object> filters = new HashMap<>();
        filters.put("_id", id);

        ArrayList<String> updates = new ArrayList<>();
        updates.add("{\"field\":\"samples\",\"op\":\"push\",\"val\":[\"{\\\"requestPayload\\\":\\\"{}\\\"}\"]}");
        return new BulkUpdates(filters, updates);
    }

    private static BulkUpdates sensitiveSampleDataBulkUpdate(int apiCollectionId) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("_id.apiCollectionId", apiCollectionId);
        filters.put("_id.url", URL);
        filters.put("_id.method", METHOD);
        filters.put("_id.responseCode", 200);
        filters.put("_id.isHeader", false);
        filters.put("_id.param", "email");
        filters.put("_id.subType", "EMAIL");

        ArrayList<String> updates = new ArrayList<>();
        updates.add("{\"field\":\"sampleData\",\"op\":\"push\",\"val\":[\"redacted\"]}");
        return new BulkUpdates(filters, updates);
    }

    private static void injectKafka(DbAction action, KafkaUtils kafkaUtils) throws Exception {
        Field field = DbAction.class.getDeclaredField("kafkaUtils");
        field.setAccessible(true);
        field.set(action, kafkaUtils);
    }

    static final class RecordingKafkaUtils extends KafkaUtils {
        int insertDataCallCount;
        int lastInsertBatchSize;
        String lastTriggerMethod;

        @Override
        public boolean isWriteEnabled() {
            return true;
        }

        @Override
        public void insertData(List<BulkUpdates> writes, String triggerMethod, int accountId) {
            insertDataCallCount++;
            lastInsertBatchSize = writes == null ? 0 : writes.size();
            lastTriggerMethod = triggerMethod;
        }
    }
}
