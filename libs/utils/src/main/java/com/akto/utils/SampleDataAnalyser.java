package com.akto.utils;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.util.filter.DictionaryFilter;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import static com.akto.runtime.utils.Utils.parseKafkaMessage;

import org.bson.Document;
import org.bson.conversions.Bson;

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
                            if (DictionaryFilter.isEnglishWord(segmentValue)) {
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

        System.out.println("Total processed: " + result.getTotalProcessed());
        System.out.println("Total matches: " + result.getTotalMatches());
        System.out.println("finiesxhed");
    }

}
