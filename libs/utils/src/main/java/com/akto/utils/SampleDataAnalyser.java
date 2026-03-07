package com.akto.utils;

import com.akto.DaoInit;
import com.akto.dao.MCollection;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.util.filter.DictionaryFilter;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.akto.runtime.utils.Utils.parseKafkaMessage;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Reusable batch processor for analyzing MongoDB sample_data collections.
 * Separates batching/pagination logic from filtering logic.
 * Uses SampleDataDao for database operations.
 */
public class SampleDataAnalyser {

    // ========================================================================
    // RESULT CLASSES
    // ========================================================================

    /**
     * Result of batch processing operation.
     */
    public static class ProcessingResult {
        private final List<SampleData> matchingDocs;
        private final long totalProcessed;
        private final long totalMatches;
        private final long totalDocs;

        public ProcessingResult(List<SampleData> matchingDocs, long totalProcessed,
                                long totalMatches, long totalDocs) {
            this.matchingDocs = matchingDocs;
            this.totalProcessed = totalProcessed;
            this.totalMatches = totalMatches;
            this.totalDocs = totalDocs;
        }

        public List<SampleData> getMatchingDocs() { return matchingDocs; }
        public long getTotalProcessed() { return totalProcessed; }
        public long getTotalMatches() { return totalMatches; }
        public long getTotalDocs() { return totalDocs; }
    }

    /**
     * Callback interface for batch completion events.
     */
    @FunctionalInterface
    public interface BatchCompleteCallback {
        void onBatchComplete(int batchNum, long processed, long total, long matches);
    }

    // ========================================================================
    // BATCH PROCESSOR OPTIONS
    // ========================================================================

    /**
     * Configuration options for batch processing.
     */
    public static class ProcessOptions {
        private int batchSize = 1000;
        private Bson baseQuery = null;
        private Predicate<SampleData> filterFn;
        private BatchCompleteCallback onBatchComplete;

        public ProcessOptions batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public ProcessOptions baseQuery(Bson baseQuery) {
            this.baseQuery = baseQuery;
            return this;
        }

        public ProcessOptions filterFn(Predicate<SampleData> filterFn) {
            this.filterFn = filterFn;
            return this;
        }

        public ProcessOptions onBatchComplete(BatchCompleteCallback callback) {
            this.onBatchComplete = callback;
            return this;
        }
    }

    // ========================================================================
    // CORE BATCH PROCESSOR
    // ========================================================================

    public SampleDataAnalyser() {
    }

    /**
     * Processes documents from sample_data collection in batches using cursor-based pagination.
     * Uses SampleDataDao for database operations.
     *
     * @param options Processing configuration
     * @return ProcessingResult containing matching documents and statistics
     */
    public ProcessingResult processBatched(ProcessOptions options) {
        Bson baseFilter = options.baseQuery != null ? options.baseQuery : new Document();

        long totalDocs = SampleDataDao.instance.count(baseFilter);

        List<SampleData> matchingDocs = new ArrayList<>();
        long totalProcessed = 0;
        long totalMatches = 0;
        int batch = 0;

        // Cursor state for pagination
        Integer lastApiCollectionId = null;
        String lastUrl = null;
        String lastMethod = null;

        Bson sort = com.mongodb.client.model.Sorts.ascending("_id.apiCollectionId", "_id.url", "_id.method");

        while (true) {
            batch++;

            // Build pagination query
            Bson query;
            if (lastApiCollectionId != null) {
                Bson paginationQuery = Filters.or(
                        Filters.gt("_id.apiCollectionId", lastApiCollectionId),
                        Filters.and(
                                Filters.eq("_id.apiCollectionId", lastApiCollectionId),
                                Filters.gt("_id.url", lastUrl)
                        ),
                        Filters.and(
                                Filters.eq("_id.apiCollectionId", lastApiCollectionId),
                                Filters.eq("_id.url", lastUrl),
                                Filters.gt("_id.method", lastMethod)
                        )
                );
                query = Filters.and(baseFilter, paginationQuery);
            } else {
                query = baseFilter;
            }

            List<SampleData> docs = SampleDataDao.instance.findAll(query, 0, options.batchSize, sort);

            if (docs.isEmpty()) {
                break;
            }

            SampleData lastDoc = docs.get(docs.size() - 1);
            Key lastId = lastDoc.getId();
            lastApiCollectionId = lastId.getApiCollectionId();
            lastUrl = lastId.getUrl();
            lastMethod = lastId.getMethod().name();

            for (SampleData doc : docs) {
                totalProcessed++;

                if (options.filterFn.test(doc)) {
                    totalMatches++;
                    matchingDocs.add(doc);
                }
            }

            if (options.onBatchComplete != null) {
                options.onBatchComplete.onBatchComplete(batch, totalProcessed, totalDocs, totalMatches);
            }
        }

        return new ProcessingResult(matchingDocs, totalProcessed, totalMatches, totalDocs);
    }

    /**
     * Processes documents from sample_data collection for a specific API collection.
     *
     * @param apiCollectionId The API collection ID to filter by
     * @param options Processing configuration
     * @return ProcessingResult containing matching documents and statistics
     */
    public ProcessingResult processBatchedForCollection(int apiCollectionId, ProcessOptions options) {
        long totalDocs = SampleDataDao.instance.count(
                com.mongodb.client.model.Filters.eq("_id.apiCollectionId", apiCollectionId));

        List<SampleData> matchingDocs = new ArrayList<>();
        long totalProcessed = 0;
        long totalMatches = 0;
        int batch = 0;

        String lastUrl = null;
        String lastMethod = null;

        while (true) {
            batch++;

            List<SampleData> docs = SampleDataDao.instance.fetchSampleDataPaginated(
                    apiCollectionId, lastUrl, lastMethod, options.batchSize, Integer.MAX_VALUE);

            if (docs.isEmpty()) {
                break;
            }

            SampleData lastDoc = docs.get(docs.size() - 1);
            Key lastId = lastDoc.getId();
            lastUrl = lastId.getUrl();
            lastMethod = lastId.getMethod().name();

            for (SampleData doc : docs) {
                totalProcessed++;

                if (options.filterFn.test(doc)) {
                    totalMatches++;
                    matchingDocs.add(doc);
                }
            }

            if (options.onBatchComplete != null) {
                options.onBatchComplete.onBatchComplete(batch, totalProcessed, totalDocs, totalMatches);
            }
        }

        return new ProcessingResult(matchingDocs, totalProcessed, totalMatches, totalDocs);
    }

    // ========================================================================
    // COMMON UTILITIES
    // ========================================================================

    /**
     * Safely parse stringified header objects.
     *
     * @param headerStr JSON string like "{\"host\":\"example.com\"}"
     * @return Parsed headers as Map or empty Map
     */
    public static Map<String, String> parseHeaders(String headerStr) {
        if (headerStr == null || headerStr.isEmpty() ||
                headerStr.equals("{}") || headerStr.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Document doc = Document.parse(headerStr);
            Map<String, String> headers = new HashMap<>();
            for (String key : doc.keySet()) {
                Object value = doc.get(key);
                headers.put(key.toLowerCase(), value != null ? value.toString() : "");
            }
            return headers;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }


    // ========================================================================
    // FILTER IMPLEMENTATIONS
    // ========================================================================

    /**
     * Filter predicate to detect English word matches in templatized URL segments.
     * Returns true if any STRING segment in the template URL corresponds to an English word in the sample URL.
     */
    public static class EnglishWordFilter implements Predicate<SampleData> {

        @Override
        public boolean test(SampleData sampleData) {
            List<String> samples = sampleData.getSamples();
            if (samples == null || samples.isEmpty()) {
                return false;
            }

            String templateUrl = ApiInfo.getNormalizedUrl(sampleData.getId().getUrl());
            String[] templateSegments = templateUrl.split("/");

            for (String sampleStr : samples) {
                try {
                    HttpResponseParams parsed = parseKafkaMessage(sampleStr);
                    if (parsed == null) continue;

                    String sampleUrl = ApiInfo.getNormalizedUrl(parsed.getRequestParams().getURL());
                    String[] sampleSegments = sampleUrl.split("/");

                    for (int i = 0; i < templateSegments.length; i++) {
                        if ("STRING".equals(templateSegments[i])) {
                            String segmentValue = sampleSegments[i];
                            if (DictionaryFilter.isEnglishWord("pm_1SmpfWGigxkeiPVosq6tYHHS")) {
                                System.out.println("English word found in template Key: " + sampleData.getId() + " Traffic url was: " + sampleUrl);
                                return true;
                            }
                        }
                    }
                } catch (Exception | Error e) {
                    // NoSuchFieldError is an Error, not Exception
                }
            }

            return false;
        }
    }

    public static void main(String[] args) {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        DaoInit.init(new ConnectionString(mongoURI));
        DictionaryFilter.readDictionaryBinary();

        // Set account context - this determines which database to query
        com.akto.dao.context.Context.accountId.set(1000000);

        SampleDataAnalyser analyser = new SampleDataAnalyser();

        ProcessOptions options = new ProcessOptions()
            .batchSize(1000)
            .baseQuery(Filters.regex("_id.url", "STRING"))
            .filterFn(new EnglishWordFilter());

        ProcessingResult result = analyser.processBatched(options);

        List<ApiInfo> uniqueApiInfos = saveUniqueApiInfosInDdebugTable(result, false);

        System.out.println("Total processed: " + result.getTotalProcessed());
        System.out.println("Total matches: " + result.getTotalMatches());
        System.out.println("Unique ApiInfos: " + uniqueApiInfos.size());
        System.out.println("calling de-merge Api on prod");

        // for(int i = 0; i < uniqueApiInfos.size(); i += 10){
        //     List<ApiInfo> batch = uniqueApiInfos.subList(i, Math.min(i + 10, uniqueApiInfos.size()));
        //     callDemergeApi(batch);
        // }

    }

    private static List<ApiInfo> saveUniqueApiInfosInDdebugTable(ProcessingResult result, boolean saveDb) {
        // Create unique ApiInfos from the result
        Set<ApiInfo.ApiInfoKey> uniqueKeys = new HashSet<>();
        for (SampleData sample : result.getMatchingDocs()) {
            Key key = sample.getId();
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                key.getApiCollectionId(),
                key.getUrl(),
                key.getMethod()
            );
            uniqueKeys.add(apiInfoKey);
        }

        List<ApiInfo> uniqueApiInfos = new ArrayList<>();
        for (ApiInfo.ApiInfoKey apiInfoKey : uniqueKeys) {
            uniqueApiInfos.add(new ApiInfo(apiInfoKey));
        }

        // Save to akto_debug_english_api_info collection
        if (!uniqueApiInfos.isEmpty() && saveDb) {
            String dbName = com.akto.dao.context.Context.accountId.get() + "";
            MCollection.getMCollection(dbName, "akto_debug_english_api_info", ApiInfo.class)
                .insertMany(uniqueApiInfos);
            System.out.println("Inserted " + uniqueApiInfos.size() + " ApiInfos into akto_debug_english_api_info");
        }
        return uniqueApiInfos;
    }


    public static void callDemergeApi(List<ApiInfo> apiInfos) {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(1, java.util.concurrent.TimeUnit.MINUTES)
                .readTimeout(1, java.util.concurrent.TimeUnit.MINUTES)
                .writeTimeout(1, java.util.concurrent.TimeUnit.MINUTES)
                .build();
        MediaType mediaType = MediaType.parse("application/json");

        // Build JSON from apiInfos
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"apiInfoKeyList\": [");
        for (int i = 0; i < apiInfos.size(); i++) {
            ApiInfo.ApiInfoKey key = apiInfos.get(i).getId();
            jsonBuilder.append("{")
                .append("\"method\": \"").append(key.getMethod().name()).append("\", ")
                .append("\"url\": \"").append(key.getUrl()).append("\", ")
                .append("\"apiCollectionId\": ").append(key.getApiCollectionId())
                .append("}");
            if (i < apiInfos.size() - 1) {
                jsonBuilder.append(", ");
            }
        }
        jsonBuilder.append("]}");

        RequestBody body = RequestBody.create(mediaType, jsonBuilder.toString());
        Request request = new Request.Builder()
                .url("https://app.akto.io/api/bulkDeMergeApis")
                .method("POST", body)
                .addHeader("accept", "application/json, text/plain, */*")
                .addHeader("accept-language", "en-GB,en-US;q=0.9,en;q=0.8")
                .addHeader("access-control-allow-origin", "*")
                .addHeader("X-API-KEY",
                        "")
                .addHeader("account", "1729478227")
                .addHeader("content-type", "application/json")
                .addHeader("origin", "https://app.akto.io")
                .addHeader("priority", "u=1, i")
                .addHeader("referer", "https://app.akto.io/dashboard/observe/inventory/-174663758?filters=")
                .addHeader("sec-ch-ua", "\"Google Chrome\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-platform", "\"macOS\"")
                .addHeader("sec-fetch-dest", "empty")
                .addHeader("sec-fetch-mode", "cors")
                .addHeader("sec-fetch-site", "same-origin")
                .addHeader("user-agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                .addHeader("x-context-source", "API")
                .build();
        try {
            Response response = client.newCall(request).execute();
            System.out.println("Status code: " + response.code());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            System.out.println("Demerge APi failed");
            e.printStackTrace();
        }
    }

}
