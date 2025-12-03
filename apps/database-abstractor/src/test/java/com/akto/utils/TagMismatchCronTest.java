package com.akto.utils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Integration tests for TagMismatchCron using actual MongoDB.
 */
public class TagMismatchCronTest extends MongoBasedTest {

    private TagMismatchCron tagMismatchCron;
    private static final int TEST_ACCOUNT_ID = 1000000;

    @Before
    public void setup() {
        Context.accountId.set(TEST_ACCOUNT_ID);
        tagMismatchCron = new TagMismatchCron();

        // Clear collections
        SampleDataDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
    }

    @After
    public void teardown() {
        // Clean up
        SampleDataDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();

    }


    // Test 2: All Samples Have Mismatch
    @Test
    public void testEvaluateTagsMismatch_AllSamplesWithMismatch() throws Exception {
        // Setup: Create 5 ApiCollections
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(100, "Collection 1", "server1.akto.io"),
            TagMismatchDataMother.createApiCollection(200, "Collection 2", "server2.akto.io"),
            TagMismatchDataMother.createApiCollection(300, "Collection 3", "server3.akto.io"),
            TagMismatchDataMother.createApiCollection(400, "Collection 4", "server4.akto.io"),
            TagMismatchDataMother.createApiCollection(500, "Collection 5", "server5.akto.io")
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // Create 10 SampleData per collection (all with mismatch)
        List<SampleData> sampleDataList = new ArrayList<>();
        Random random = new Random();
        for (int collectionId : Arrays.asList(100, 200, 300, 400, 500)) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                collectionId, "GET", "https://server.akto.io/api/test/" + random.nextInt(), 10
            ));
        }
        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify all 5 collections have "tags-mismatch" tag
        for (int collectionId : Arrays.asList(100, 200, 300, 400, 500)) {
            List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(collectionId);
            assertNotNull("Tags should exist for collection " + collectionId, tags);

            CollectionTags mismatchTag = TagMismatchDataMother.findTagByKeyName(tags, "tags-mismatch");
            assertNotNull("tags-mismatch tag should exist for collection " + collectionId, mismatchTag);
            assertEquals("Tag value should be 'true'", "true", mismatchTag.getValue());
            assertEquals("Tag source should be USER", CollectionTags.TagSource.USER, mismatchTag.getSource());
        }
    }

    // Test 3: No Samples Have Mismatch
    @Test
    public void testEvaluateTagsMismatch_NoSamplesWithMismatch() throws Exception {
        // Setup: Create 3 ApiCollections
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(100, "Collection 1", "server1.akto.io"),
            TagMismatchDataMother.createApiCollection(200, "Collection 2", "server2.akto.io"),
            TagMismatchDataMother.createApiCollection(300, "Collection 3", "server3.akto.io")
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // Create 20 SampleData (all without mismatch)
        List<SampleData> sampleDataList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithNoMismatchSamples(
                100 + (i % 3) * 100, "GET", "https://server.akto.io/api/test/" + i, 10
            ));
        }
        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify no tags added
        for (int collectionId : Arrays.asList(100, 200, 300)) {
            List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(collectionId);
            if (tags != null) {
                CollectionTags mismatchTag = TagMismatchDataMother.findTagByKeyName(tags, "tags-mismatch");
                assertNull("tags-mismatch tag should NOT exist for collection " + collectionId, mismatchTag);
            }
        }
    }

    // Test 4: Mixed Collections
    @Test
    public void testEvaluateTagsMismatch_MixedCollections() throws Exception {
        // Setup: Create 3 ApiCollections
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(100, "Collection 1", "server1.akto.io"),
            TagMismatchDataMother.createApiCollection(200, "Collection 2", "server2.akto.io"),
            TagMismatchDataMother.createApiCollection(300, "Collection 3", "server3.akto.io")
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // Collection 100: 10 SampleData all with mismatch
        List<SampleData> sampleDataList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                100, "GET", "https://server.akto.io/api/test100/" + i, 3
            ));
        }

        // Collection 200: 15 SampleData all without mismatch
        for (int i = 0; i < 15; i++) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithNoMismatchSamples(
                200, "GET", "https://server.akto.io/api/test200/" + i, 3
            ));
        }

        // Collection 300: 5 SampleData all with mismatch
        for (int i = 0; i < 5; i++) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                300, "GET", "https://server.akto.io/api/test300/" + i, 3
            ));
        }

        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify collections 100 and 300 have tags, 200 does not
        List<CollectionTags> tags100 = TagMismatchDataMother.getApiCollectionTags(100);
        assertNotNull("Collection 100 should have tags-mismatch tag",
            TagMismatchDataMother.findTagByKeyName(tags100, "tags-mismatch"));

        List<CollectionTags> tags200 = TagMismatchDataMother.getApiCollectionTags(200);
        if (tags200 != null) {
            assertNull("Collection 200 should NOT have tags-mismatch tag",
                TagMismatchDataMother.findTagByKeyName(tags200, "tags-mismatch"));
        }

        List<CollectionTags> tags300 = TagMismatchDataMother.getApiCollectionTags(300);
        assertNotNull("Collection 300 should have tags-mismatch tag",
            TagMismatchDataMother.findTagByKeyName(tags300, "tags-mismatch"));
    }

    // Test 5: Mixed Samples - requires ALL samples to match
    @Test
    public void testIsTagsMismatch_RequiresAllSamplesMatch() throws Exception {
        // Setup: Create 2 ApiCollections
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(100, "Collection 1", "server1.akto.io"),
            TagMismatchDataMother.createApiCollection(200, "Collection 2", "server2.akto.io")
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // SampleData 1: 2 samples with mismatch, 1 without -> Should return FALSE
        SampleData mixed = TagMismatchDataMother.createSampleDataWithMixedSamples(100, "GET",
            "https://server.akto.io/api/mixed", 2, 1);

        // SampleData 2: All 3 samples with mismatch -> Should return TRUE
        SampleData allMismatch = TagMismatchDataMother.createSampleDataWithAllMismatchSamples(200, "GET",
            "https://server.akto.io/api/all", 3);

        TagMismatchDataMother.insertSampleDataBatch(Arrays.asList(mixed, allMismatch));

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify only collection 200 gets tag
        List<CollectionTags> tags100 = TagMismatchDataMother.getApiCollectionTags(100);
        if (tags100 != null) {
            assertNull("Collection 100 should NOT have tag (mixed samples)",
                TagMismatchDataMother.findTagByKeyName(tags100, "tags-mismatch"));
        }

        List<CollectionTags> tags200 = TagMismatchDataMother.getApiCollectionTags(200);
        assertNotNull("Collection 200 should have tag (all mismatch)",
            TagMismatchDataMother.findTagByKeyName(tags200, "tags-mismatch"));
    }

    // Test 6: Batch Processing (2500 documents)
    @Test
    public void testEvaluateTagsMismatch_BatchProcessing() throws Exception {
        // Setup: Create 10 ApiCollections
        List<ApiCollection> collections = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            collections.add(TagMismatchDataMother.createApiCollection(i * 100, "Collection " + i, "server.akto.io"));
        }
        TagMismatchDataMother.insertApiCollections(collections);

        // Create 2500 SampleData
        List<SampleData> sampleDataList = new ArrayList<>();

        // 1000 with mismatch (collections 100-500)
        for (int i = 0; i < 1000; i++) {
            int collectionId = ((i % 5) + 1) * 100; // 100, 200, 300, 400, 500
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                collectionId, "GET", "https://server.akto.io/api/mismatch/" + i, 3
            ));
        }

        // 1500 without mismatch (collections 600-1000)
        for (int i = 0; i < 1500; i++) {
            int collectionId = ((i % 5) + 6) * 100; // 600, 700, 800, 900, 1000
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithNoMismatchSamples(
                collectionId, "GET", "https://server.akto.io/api/normal/" + i, 3
            ));
        }

        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify collections 100-500 have tags
        for (int i = 1; i <= 5; i++) {
            int collectionId = i * 100;
            List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(collectionId);
            assertNotNull("Collection " + collectionId + " should have tags-mismatch tag",
                TagMismatchDataMother.findTagByKeyName(tags, "tags-mismatch"));
        }

        // Verify collections 600-1000 don't have tags
        for (int i = 6; i <= 10; i++) {
            int collectionId = i * 100;
            List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(collectionId);
            if (tags != null) {
                assertNull("Collection " + collectionId + " should NOT have tags-mismatch tag",
                    TagMismatchDataMother.findTagByKeyName(tags, "tags-mismatch"));
            }
        }
    }

    // Test 7: Updates Existing Tag
    @Test
    public void testHandleMismatchedSamples_UpdatesExistingTag() throws Exception {
        // Setup: Create ApiCollection with old tag
        ApiCollection collection = TagMismatchDataMother.createApiCollectionWithOldMismatchTag(100, "Collection 1");
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        int oldTimestamp = collection.getTagsList().get(0).getLastUpdatedTs();

        // Create SampleData with mismatch
        SampleData sampleData = TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
            100, "GET", "https://server.akto.io/api/test", 3
        );
        TagMismatchDataMother.insertSampleDataBatch(Arrays.asList(sampleData));

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify tag updated
        List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(100);
        assertNotNull("Tags should exist", tags);

        // Count tags-mismatch tags
        int count = 0;
        CollectionTags mismatchTag = null;
        for (CollectionTags tag : tags) {
            if ("tags-mismatch".equals(tag.getKeyName())) {
                count++;
                mismatchTag = tag;
            }
        }

        assertEquals("Should have exactly ONE tags-mismatch tag", 1, count);
        assertNotNull("tags-mismatch tag should exist", mismatchTag);
        assertTrue("Tag timestamp should be newer", mismatchTag.getLastUpdatedTs() > oldTimestamp);
        assertEquals("Tag value should be 'true'", "true", mismatchTag.getValue());
        assertEquals("Tag source should be USER", CollectionTags.TagSource.USER, mismatchTag.getSource());
    }

    // Test 8: Preserves Other Tags
    @Test
    public void testHandleMismatchedSamples_PreservesOtherTags() throws Exception {
        // Setup: Create ApiCollection with mixed tags
        ApiCollection collection = TagMismatchDataMother.createApiCollectionWithMixedTags(100, "Collection 1");
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Create SampleData with mismatch
        SampleData sampleData = TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
            100, "GET", "https://server.akto.io/api/test", 3
        );
        TagMismatchDataMother.insertSampleDataBatch(Arrays.asList(sampleData));

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify other tags preserved
        List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(100);
        assertNotNull("Tags should exist", tags);
        assertEquals("Should have 3 tags total", 3, tags.size());

        CollectionTags envTypeTag = TagMismatchDataMother.findTagByKeyName(tags, "envType");
        assertNotNull("envType tag should exist", envTypeTag);
        assertEquals("envType value unchanged", "STAGING", envTypeTag.getValue());

        CollectionTags customTag = TagMismatchDataMother.findTagByKeyName(tags, "customTag");
        assertNotNull("customTag should exist", customTag);
        assertEquals("customTag value unchanged", "foo", customTag.getValue());

        CollectionTags mismatchTag = TagMismatchDataMother.findTagByKeyName(tags, "tags-mismatch");
        assertNotNull("tags-mismatch tag should exist", mismatchTag);
        assertEquals("tags-mismatch value correct", "true", mismatchTag.getValue());
    }

    // Test 9: Bulk Update Multiple Collections
    @Test
    public void testHandleMismatchedSamples_BulkUpdateMultipleCollections() throws Exception {
        // Setup: Create 10 ApiCollections
        List<ApiCollection> collections = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            collections.add(TagMismatchDataMother.createApiCollection(i * 100, "Collection " + i, "server.akto.io"));
        }
        TagMismatchDataMother.insertApiCollections(collections);

        // Create 10 SampleData (one per collection, all with mismatch)
        List<SampleData> sampleDataList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                i * 100, "GET", "https://server.akto.io/api/test" + i, 3
            ));
        }
        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify all 10 collections updated
        for (int i = 1; i <= 10; i++) {
            int collectionId = i * 100;
            List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(collectionId);
            assertNotNull("Collection " + collectionId + " should have tags-mismatch tag",
                TagMismatchDataMother.findTagByKeyName(tags, "tags-mismatch"));
        }
    }

    // Test 10: Same Collection Deduplication
    @Test
    public void testHandleMismatchedSamples_SameCollectionDeduplication() throws Exception {
        // Setup: Create 1 ApiCollection
        ApiCollection collection = TagMismatchDataMother.createApiCollection(100, "Collection 1", "server.akto.io");
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Create 20 SampleData all for collection 100 (all with mismatch)
        List<SampleData> sampleDataList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sampleDataList.add(TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                100, "GET", "https://server.akto.io/api/test" + i, 3
            ));
        }
        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify exactly ONE tags-mismatch tag
        List<CollectionTags> tags = TagMismatchDataMother.getApiCollectionTags(100);
        assertNotNull("Tags should exist", tags);

        int count = 0;
        for (CollectionTags tag : tags) {
            if ("tags-mismatch".equals(tag.getKeyName())) {
                count++;
            }
        }

        assertEquals("Should have exactly ONE tags-mismatch tag (not 20)", 1, count);
    }

    // Test 11: Both DestIp Patterns
    @Test
    public void testIsTagsMismatch_BothDestIpPatterns() throws Exception {
        // Setup: Create 2 ApiCollections
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(100, "Collection 1", "server1.akto.io"),
            TagMismatchDataMother.createApiCollection(200, "Collection 2", "server2.akto.io")
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // SampleData 1: samples with destIp="127.0.0.1:15001"
        int baseTime = Context.now();
        List<String> samples1 = Arrays.asList(
            TagMismatchDataMother.createSampleJsonWithMismatch_127("GET", "https://server.akto.io/api/test1", baseTime),
            TagMismatchDataMother.createSampleJsonWithMismatch_127("GET", "https://server.akto.io/api/test2", baseTime + 1)
        );
        SampleData sampleData1 = TagMismatchDataMother.createSampleData(100, "GET", "https://server.akto.io/api/test1", -1, samples1);

        // SampleData 2: samples with destIp="0.0.0.0:0"
        List<String> samples2 = Arrays.asList(
            TagMismatchDataMother.createSampleJsonWithMismatch_0000("GET", "https://server.akto.io/api/test3", baseTime),
            TagMismatchDataMother.createSampleJsonWithMismatch_0000("GET", "https://server.akto.io/api/test4", baseTime + 1)
        );
        SampleData sampleData2 = TagMismatchDataMother.createSampleData(200, "GET", "https://server.akto.io/api/test3", -1, samples2);

        TagMismatchDataMother.insertSampleDataBatch(Arrays.asList(sampleData1, sampleData2));

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify both patterns detected
        List<CollectionTags> tags100 = TagMismatchDataMother.getApiCollectionTags(100);
        assertNotNull("Collection 100 should have tag (127.0.0.1:15001 pattern)",
            TagMismatchDataMother.findTagByKeyName(tags100, "tags-mismatch"));

        List<CollectionTags> tags200 = TagMismatchDataMother.getApiCollectionTags(200);
        assertNotNull("Collection 200 should have tag (0.0.0.0:0 pattern)",
            TagMismatchDataMother.findTagByKeyName(tags200, "tags-mismatch"));
    }

    // ========== Unit Tests for ignoreSameHostNameCollections ==========

    /**
     * Test 12: Valid service name extraction - should ignore collection
     * Hostname: "local.ebe-creditcard-service-netcore.svc:5000-ebe-creditcard-service-netcore"
     * Expected: Service name "ebe-creditcard-service-netcore" is extracted and found in hostname -> ignored
     */
    @Test
    public void testIgnoreSameHostNameCollections_ValidServiceName() throws Exception {
        // Setup: Create collection with matching service name pattern
        ApiCollection collection = TagMismatchDataMother.createApiCollection(
            100,
            "Credit Card Service",
            "local.ebe-creditcard-service-netcore.svc:5000-ebe-creditcard-service-netcore"
        );
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Reload collections into static map
        TagMismatchCron.loadApiCollections();

        // Create set with collection ID
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(100));

        // Invoke method under test
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify collection was removed (ignored)
        assertTrue("Collection should be ignored (removed from set)", collectionIds.isEmpty());
    }

    /**
     * Test 13: Multiple collections with matching service names
     * All should be ignored
     */
    @Test
    public void testIgnoreSameHostNameCollections_MultipleMatchingCollections() throws Exception {
        // Setup: Create 3 collections with matching patterns
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(
                100, "Service 1", "local.payment-service.svc:8080-payment-service"
            ),
            TagMismatchDataMother.createApiCollection(
                200, "Service 2", "prod.auth-service.cluster:9090-auth-service"
            ),
            TagMismatchDataMother.createApiCollection(
                300, "Service 3", "test.user-management.internal:3000-user-management"
            )
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // Reload collections into static map
        TagMismatchCron.loadApiCollections();

        // Create set with all collection IDs
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(100, 200, 300));

        // Invoke method under test
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify all collections were removed
        assertTrue("All collections should be ignored", collectionIds.isEmpty());
    }

    /**
     * Test 14: Hostname without dash in last segment - should NOT ignore
     * Hostname: "api.example.com"
     */
    @Test
    public void testIgnoreSameHostNameCollections_NoDashInLastSegment() throws Exception {
        // Setup: Create collection without dash in last segment
        ApiCollection collection = TagMismatchDataMother.createApiCollection(
            100, "Example Service", "api.example.com"
        );
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Reload collections
        TagMismatchCron.loadApiCollections();

        // Create set with collection ID
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(100));

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify collection was NOT removed
        assertTrue("Collection should NOT be ignored (no dash in last segment)", collectionIds.contains(100));
        assertEquals("Set should still have 1 element", 1, collectionIds.size());
    }

    /**
     * Test 15: Service name not found in hostname - should NOT ignore
     * Hostname: "api.example.com:8080-different-service"
     * Extracted service: "different-service" but hostname doesn't contain it in other parts
     */
    @Test
    public void testIgnoreSameHostNameCollections_ServiceNameNotInHostname() throws Exception {
        // Setup: Service name appears only once (in last segment after dash)
        ApiCollection collection = TagMismatchDataMother.createApiCollection(
            100, "Service", "api.example.com:8080-unique-service-name"
        );
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Reload collections
        TagMismatchCron.loadApiCollections();

        // Create set with collection ID
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(100));

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify collection was NOT removed (service name only appears once)
        assertTrue("Collection should NOT be ignored", collectionIds.contains(100));
        assertEquals("Set should still have 1 element", 1, collectionIds.size());
    }

    /**
     * Test 16: Mixed collections - some match, some don't
     * Only matching ones should be ignored
     */
    @Test
    public void testIgnoreSameHostNameCollections_MixedCollections() throws Exception {
        // Setup: 5 collections, 2 match pattern, 3 don't
        List<ApiCollection> collections = Arrays.asList(
            // MATCH: service name in hostname
            TagMismatchDataMother.createApiCollection(
                100, "Match 1", "local.payment-service.svc:8080-payment-service"
            ),
            // NO MATCH: no dash in last segment
            TagMismatchDataMother.createApiCollection(
                200, "No Match 1", "api.example.com"
            ),
            // MATCH: service name in hostname
            TagMismatchDataMother.createApiCollection(
                300, "Match 2", "prod.auth-service.cluster:9090-auth-service"
            ),
            // NO MATCH: service name not in hostname
            TagMismatchDataMother.createApiCollection(
                400, "No Match 2", "api.server.com:3000-unique-name"
            ),
            // NO MATCH: null hostname (should not be in map)
            TagMismatchDataMother.createApiCollection(
                500, "No Match 3", null
            )
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // Reload collections
        TagMismatchCron.loadApiCollections();

        // Create set with all collection IDs
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(100, 200, 300, 400, 500));

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify only 100 and 300 were removed
        assertFalse("Collection 100 should be ignored (removed)", collectionIds.contains(100));
        assertTrue("Collection 200 should NOT be ignored", collectionIds.contains(200));
        assertFalse("Collection 300 should be ignored (removed)", collectionIds.contains(300));
        assertTrue("Collection 400 should NOT be ignored", collectionIds.contains(400));
        assertTrue("Collection 500 should NOT be ignored (null hostname)", collectionIds.contains(500));
        assertEquals("Should have 3 collections remaining", 3, collectionIds.size());
    }

    /**
     * Test 17: Empty set - should handle gracefully
     */
    @Test
    public void testIgnoreSameHostNameCollections_EmptySet() throws Exception {
        // Create empty set
        Set<Integer> collectionIds = new HashSet<>();

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify still empty, no errors
        assertTrue("Set should remain empty", collectionIds.isEmpty());
    }

    /**
     * Test 18: Collection not in map - should remain in set
     */
    @Test
    public void testIgnoreSameHostNameCollections_CollectionNotInMap() throws Exception {
        // Setup: Create one collection but query for different ID
        ApiCollection collection = TagMismatchDataMother.createApiCollection(
            100, "Service", "api.service.com:8080-service"
        );
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Reload collections
        TagMismatchCron.loadApiCollections();

        // Create set with non-existent collection ID
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(999));

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify collection stays in set (not removed)
        assertTrue("Non-existent collection should remain in set", collectionIds.contains(999));
    }

    /**
     * Test 19: Edge case - dash at end of last segment
     * Hostname: "api.service.com:8080-"
     * Extracted service name would be empty, should NOT ignore
     */
    @Test
    public void testIgnoreSameHostNameCollections_DashAtEnd() throws Exception {
        // Setup: Dash at end, no service name after it
        ApiCollection collection = TagMismatchDataMother.createApiCollection(
            100, "Service", "api.service.com:8080-"
        );
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Reload collections
        TagMismatchCron.loadApiCollections();

        // Create set
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(100));

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify collection was NOT removed (empty service name)
        assertTrue("Collection should NOT be ignored (empty service name)", collectionIds.contains(100));
    }

    /**
     * Test 20: Real-world example from requirements
     * Hostname: "local.ebe-creditcard-service-netcore.svc:5000-ebe-creditcard-service-netcore"
     * This should be ignored
     */
    @Test
    public void testIgnoreSameHostNameCollections_RealWorldExample() throws Exception {
        // Setup: Exact example from requirements
        ApiCollection collection = TagMismatchDataMother.createApiCollection(
            -107935017,
            "EBE Credit Card Service",
            "central.ebe-creditcard-service-netcore.svc:5000-ebe-creditcard-service"
        );
        TagMismatchDataMother.insertApiCollections(Arrays.asList(collection));

        // Reload collections
        TagMismatchCron.loadApiCollections();

        // Create set
        Set<Integer> collectionIds = new HashSet<>(Arrays.asList(-107935017));

        // Invoke method
        TagMismatchCron.ignoreSameHostNameCollections(collectionIds);

        // Verify collection was ignored
        assertTrue("Real-world example should be ignored", collectionIds.isEmpty());
    }

    /**
     * Test 21: Integration test - collections with matching hostnames should not get tags
     */
    @Test
    public void testEvaluateTagsMismatch_WithHostnameFiltering() throws Exception {
        // Setup: Create 3 collections
        // Collection 100: Matching hostname pattern (should be ignored)
        // Collection 200: Non-matching hostname pattern (should get tag)
        // Collection 300: No hostname (should get tag)
        List<ApiCollection> collections = Arrays.asList(
            TagMismatchDataMother.createApiCollection(
                100, "Payment Service", "local.payment-service.svc:8080-payment-service"
            ),
            TagMismatchDataMother.createApiCollection(
                200, "Auth Service", "api.external.com:9000-different-name"
            ),
            TagMismatchDataMother.createApiCollection(
                300, "User Service", null
            )
        );
        TagMismatchDataMother.insertApiCollections(collections);

        // Create SampleData with mismatches for all 3 collections
        List<SampleData> sampleDataList = Arrays.asList(
            TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                100, "GET", "https://server.akto.io/api/payment", 3
            ),
            TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                200, "GET", "https://server.akto.io/api/auth", 3
            ),
            TagMismatchDataMother.createSampleDataWithAllMismatchSamples(
                300, "GET", "https://server.akto.io/api/user", 3
            )
        );
        TagMismatchDataMother.insertSampleDataBatch(sampleDataList);

        // Execute
        java.lang.reflect.Method method = TagMismatchCron.class.getDeclaredMethod("evaluateTagsMismatch", int.class);
        method.setAccessible(true);
        method.invoke(tagMismatchCron, TEST_ACCOUNT_ID);

        // Verify results
        // Collection 100: Should NOT have tag (filtered out by hostname logic)
        List<CollectionTags> tags100 = TagMismatchDataMother.getApiCollectionTags(100);
        if (tags100 != null) {
            assertNull("Collection 100 should NOT have tag (hostname filter)",
                TagMismatchDataMother.findTagByKeyName(tags100, "tags-mismatch"));
        }

        // Collection 200: Should have tag (hostname doesn't match pattern)
        List<CollectionTags> tags200 = TagMismatchDataMother.getApiCollectionTags(200);
        assertNotNull("Collection 200 should have tag (hostname doesn't match)",
            TagMismatchDataMother.findTagByKeyName(tags200, "tags-mismatch"));

        // Collection 300: Should have tag (no hostname in map)
        List<CollectionTags> tags300 = TagMismatchDataMother.getApiCollectionTags(300);
        assertNotNull("Collection 300 should have tag (no hostname)",
            TagMismatchDataMother.findTagByKeyName(tags300, "tags-mismatch"));
    }
}
