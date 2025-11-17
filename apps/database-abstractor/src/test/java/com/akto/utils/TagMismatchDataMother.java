package com.akto.utils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data Mother class for TagMismatchCron tests.
 * Centralizes test data creation logic.
 */
public class TagMismatchDataMother {

    private static final String SAMPLE_TEMPLATE = "{\"destIp\":\"%s\",\"method\":\"%s\",\"requestPayload\":\"{}\",\"responsePayload\":\"<!DOCTYPE html> <html> <head> </head> <body> <h1>Hello, API user!</h1> <p>Test response</p> </body> </html>\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":1111111111,\"path\":\"%s\",\"requestHeaders\":\"{\\\"authorization\\\":\\\"JWT eyJhbGciOiJSUzI1NiJ9\\\",\\\"host\\\":\\\"vulnerableapi.com\\\",\\\"content-type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{}\",\"time\":\"%d\",\"statusCode\":\"200\",\"status\":\"OK\",\"akto_account_id\":\"1758179941\",\"direction\":\"%s\",\"is_pending\":\"false\"}";

    // Sample JSON Builders

    public static String createSampleJson(String destIp, String direction, String method, String url, int time) {

        return String.format(SAMPLE_TEMPLATE, destIp, method, url, time, direction);
    }

    public static String createSampleJsonWithMismatch_127(String method, String url, int time) {
        return createSampleJson("127.0.0.1:15001", "1", method, url, time);
    }

    public static String createSampleJsonWithMismatch_0000(String method, String url, int time) {
        return createSampleJson("0.0.0.0:0", "1", method, url, time);
    }

    public static String createSampleJsonWithoutMismatch(String method, String url, int time) {
        return createSampleJson("5.6.7.7", "1", method, url, time);
    }

    public static String createSampleJsonWithPartialMismatch(String method, String url, int time, boolean hasDestIp) {
        if (hasDestIp) {
            return createSampleJson("127.0.0.1:15001", "1", method, url, time);
        } else {
            return createSampleJson("3.4.5.6", "1", method, url, time);
        }
    }

    // SampleData Document Builders

    public static SampleData createSampleData(int apiCollectionId, String method, String url, int responseCode, List<String> sampleJsons) {
        SampleData sampleData = new SampleData();

        Key key = new Key();
        key.setApiCollectionId(apiCollectionId);
        key.setMethod(com.akto.dto.type.URLMethods.Method.valueOf(method));
        key.setUrl(url);
        key.setResponseCode(responseCode);
        key.setBucketStartEpoch(0);
        key.setBucketEndEpoch(0);

        sampleData.setId(key);
        sampleData.setSamples(sampleJsons);
        sampleData.setCollectionIds(Arrays.asList(apiCollectionId));

        return sampleData;
    }

    public static SampleData createSampleDataWithAllMismatchSamples(int apiCollectionId, String method, String url, int sampleCount) {
        List<String> samples = new ArrayList<>();
        int baseTime = Context.now();

        for (int i = 0; i < sampleCount; i++) {
            // Alternate between two destIp patterns
            if (i % 2 == 0) {
                samples.add(createSampleJsonWithMismatch_127(method, url, baseTime + i));
            } else {
                samples.add(createSampleJsonWithMismatch_0000(method, url, baseTime + i));
            }
        }

        return createSampleData(apiCollectionId, method, url, -1, samples);
    }

    public static SampleData createSampleDataWithNoMismatchSamples(int apiCollectionId, String method, String url, int sampleCount) {
        List<String> samples = new ArrayList<>();
        int baseTime = Context.now();

        for (int i = 0; i < sampleCount; i++) {
            samples.add(createSampleJsonWithoutMismatch(method, url, baseTime + i));
        }

        return createSampleData(apiCollectionId, method, url, -1, samples);
    }

    public static SampleData createSampleDataWithMixedSamples(int apiCollectionId, String method, String url, int mismatchCount, int normalCount) {
        List<String> samples = new ArrayList<>();
        int baseTime = Context.now();

        // Add mismatch samples first
        for (int i = 0; i < mismatchCount; i++) {
            samples.add(createSampleJsonWithMismatch_127(method, url, baseTime + i));
        }

        // Add normal samples
        for (int i = 0; i < normalCount; i++) {
            samples.add(createSampleJsonWithoutMismatch(method, url, baseTime + mismatchCount + i));
        }

        return createSampleData(apiCollectionId, method, url, -1, samples);
    }

    public static SampleData createSampleDataWithPartialMismatch(int apiCollectionId, String method, String url) {
        int baseTime = Context.now();
        List<String> samples = Arrays.asList(
            createSampleJsonWithPartialMismatch(method, url, baseTime, true),  // has destIp, no direction
            createSampleJsonWithPartialMismatch(method, url, baseTime + 1, false)  // no destIp, has direction
        );

        return createSampleData(apiCollectionId, method, url, -1, samples);
    }

    // Batch Data Builders

    public static List<SampleData> createBatchOfSampleData(int count, boolean allWithMismatch, int... apiCollectionIds) {
        List<SampleData> batch = new ArrayList<>();
        String[] methods = {"GET", "POST", "PUT", "DELETE"};
        String[] urls = {
            "https://vulnerable-server.akto.io/api/college/exams/cs",
            "https://vulnerable-server.akto.io/api/college/students",
            "https://vulnerable-server.akto.io/api/college/courses",
            "https://vulnerable-server.akto.io/api/college/grades"
        };

        for (int i = 0; i < count; i++) {
            int collectionId = apiCollectionIds[i % apiCollectionIds.length];
            String method = methods[i % methods.length];
            String url = urls[i % urls.length];

            SampleData sampleData;
            if (allWithMismatch) {
                sampleData = createSampleDataWithAllMismatchSamples(collectionId, method, url, 3);
            } else {
                sampleData = createSampleDataWithNoMismatchSamples(collectionId, method, url, 3);
            }

            batch.add(sampleData);
        }

        return batch;
    }

    public static List<SampleData> createBatchWithMixedCollections(int totalCount, int mismatchCount, int collectionCount) {
        List<SampleData> batch = new ArrayList<>();
        int[] collectionIds = new int[collectionCount];
        for (int i = 0; i < collectionCount; i++) {
            collectionIds[i] = 100 + (i * 100);
        }

        // Create mismatch samples
        for (int i = 0; i < mismatchCount; i++) {
            batch.add(createSampleDataWithAllMismatchSamples(
                collectionIds[i % collectionCount],
                "GET",
                "https://vulnerable-server.akto.io/api/test/" + i,
                3
            ));
        }

        // Create normal samples
        for (int i = 0; i < (totalCount - mismatchCount); i++) {
            batch.add(createSampleDataWithNoMismatchSamples(
                collectionIds[i % collectionCount],
                "GET",
                "https://vulnerable-server.akto.io/api/normal/" + i,
                3
            ));
        }

        return batch;
    }

    // ApiCollection Builders

    public static ApiCollection createApiCollection(int id, String name, String hostName) {
        ApiCollection collection = new ApiCollection();
        collection.setId(id);
        collection.setName(name);
        collection.setHostName(hostName);
        collection.setStartTs(Context.now());
        collection.setTagsList(new ArrayList<>());
        return collection;
    }

    public static ApiCollection createApiCollectionWithTags(int id, String name, List<CollectionTags> tags) {
        ApiCollection collection = createApiCollection(id, name, "vulnerable-server.akto.io");
        collection.setTagsList(tags);
        return collection;
    }

    public static ApiCollection createApiCollectionWithOldMismatchTag(int id, String name) {
        CollectionTags oldTag = new CollectionTags(
            Context.now() - 10000,
            "tags-mismatch",
            "true",
            CollectionTags.TagSource.USER
        );
        return createApiCollectionWithTags(id, name, Arrays.asList(oldTag));
    }

    public static ApiCollection createApiCollectionWithMixedTags(int id, String name) {
        List<CollectionTags> tags = Arrays.asList(
            new CollectionTags(Context.now(), "envType", "STAGING", CollectionTags.TagSource.KUBERNETES),
            new CollectionTags(Context.now(), "customTag", "foo", CollectionTags.TagSource.USER),
            new CollectionTags(Context.now() - 10000, "tags-mismatch", "true", CollectionTags.TagSource.USER)
        );
        return createApiCollectionWithTags(id, name, tags);
    }

    // Helper Methods

    public static void insertSampleDataBatch(List<SampleData> sampleDataList) {
        if (!sampleDataList.isEmpty()) {
            SampleDataDao.instance.getMCollection().insertMany(sampleDataList);
        }
    }

    public static void insertApiCollections(List<ApiCollection> collections) {
        if (!collections.isEmpty()) {
            ApiCollectionsDao.instance.getMCollection().insertMany(collections);
        }
    }

    public static List<CollectionTags> getApiCollectionTags(int apiCollectionId) {
        ApiCollection collection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
        return collection != null ? collection.getTagsList() : null;
    }

    public static CollectionTags findTagByKeyName(List<CollectionTags> tags, String keyName) {
        if (tags == null) return null;

        for (CollectionTags tag : tags) {
            if (keyName.equals(tag.getKeyName())) {
                return tag;
            }
        }
        return null;
    }
}
