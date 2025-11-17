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
import java.util.List;
import java.util.Random;

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
}
