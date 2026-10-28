package com.akto.imperva;

import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.imperva.ImpervaApiClient.EndpointSummary;
import com.akto.imperva.model.ImpervaSchema;
import com.akto.imperva.parser.ImpervaSchemaParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.open_api.parser.ParserResult;
import com.akto.wiz.WizImportJobPageContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Pulls the full Imperva API Security inventory and converts it to Akto messages.
 * For every endpoint the detailed schema is fetched; if that fails or yields no sample,
 * a basic sample is built from the inventory listing (host, method, path).
 */
public class ImpervaApiEndpointsImporter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ImpervaApiEndpointsImporter.class, LogDb.DASHBOARD);

    private static final int BATCH_SIZE = 50;
    private static final int DETAILS_FETCH_THREADS = 5;
    private static final int DETAILS_FETCH_TIMEOUT_SECONDS = 180;
    // after this many failures in a row (e.g. Imperva blocking us) stop calling the details API and use the inventory only
    private static final int MAX_CONSECUTIVE_DETAILS_FAILURES = 100;
    private static final int MAX_FAILURES_LOGGED_TO_DB = 20;
    // spreads the inventory writes out instead of pushing everything as fast as possible
    private static final long PAUSE_BETWEEN_BATCHES_MILLIS = 1000;
    private static final String IMPERVA_PATH_WILDCARD = "*";
    private static final String AKTO_STRING_PATH_PARAM = "STRING";

    public static class ImportResult {
        private final int endpointCount;
        private final int fallbackCount;

        public ImportResult(int endpointCount, int fallbackCount) {
            this.endpointCount = endpointCount;
            this.fallbackCount = fallbackCount;
        }

        public int getEndpointCount() {
            return endpointCount;
        }

        public int getFallbackCount() {
            return fallbackCount;
        }
    }

    public static ImportResult importEndpoints(ImpervaApiClient client, BiConsumer<ParserResult, WizImportJobPageContext> processor) throws Exception {
        List<EndpointSummary> endpoints = client.fetchAllEndpoints();
        loggerMaker.infoAndAddToDb(String.format("Fetched %d endpoints from Imperva inventory", endpoints.size()));

        int fallbackCount = 0;
        int failedCount = 0;
        int detailsFailures = 0;
        int consecutiveDetailsFailures = 0;
        int totalBatches = (endpoints.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        ExecutorService detailsPool = Executors.newFixedThreadPool(DETAILS_FETCH_THREADS);

        try {
            for (int i = 0; i < endpoints.size(); i += BATCH_SIZE) {
                List<EndpointSummary> batch = endpoints.subList(i, Math.min(i + BATCH_SIZE, endpoints.size()));
                boolean isFirstBatch = i == 0;
                boolean isLastBatch = i + BATCH_SIZE >= endpoints.size();

                boolean fetchDetails = consecutiveDetailsFailures < MAX_CONSECUTIVE_DETAILS_FAILURES;
                List<Future<ImpervaSchema>> details = new ArrayList<>();
                if (fetchDetails) {
                    for (EndpointSummary endpoint : batch) {
                        details.add(detailsPool.submit(() -> client.fetchEndpointDetails(endpoint.getId())));
                    }
                }

                List<SwaggerUploadLog> uploadLogs = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    EndpointSummary endpoint = batch.get(j);
                    ImpervaSchema schema = null;
                    if (fetchDetails) {
                        try {
                            schema = details.get(j).get(DETAILS_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            consecutiveDetailsFailures = 0;
                        } catch (Exception e) {
                            detailsFailures++;
                            consecutiveDetailsFailures++;
                            String err = String.format("Failed to fetch Imperva details for endpoint %d (%s %s%s): %s",
                                endpoint.getId(), endpoint.getMethod(), endpoint.getHostName(), endpoint.getResource(), e.getMessage());
                            if (detailsFailures <= MAX_FAILURES_LOGGED_TO_DB) {
                                loggerMaker.errorAndAddToDb(err);
                            } else {
                                loggerMaker.error(err);
                            }
                            if (consecutiveDetailsFailures == MAX_CONSECUTIVE_DETAILS_FAILURES) {
                                loggerMaker.errorAndAddToDb(String.format("%d Imperva details calls failed in a row, using inventory data only for the remaining endpoints", MAX_CONSECUTIVE_DETAILS_FAILURES));
                            }
                        }
                    }

                    List<SwaggerUploadLog> logs = schema != null ? convert(schema, endpoint) : new ArrayList<>();
                    if (logs.isEmpty()) {
                        logs = convert(new ImpervaSchema(), endpoint);
                        if (logs.isEmpty()) {
                            failedCount++;
                            continue;
                        }
                        fallbackCount++;
                    }
                    uploadLogs.addAll(logs);
                }

                ParserResult parserResult = new ParserResult();
                parserResult.setUploadLogs(uploadLogs);
                parserResult.setFileErrors(new ArrayList<>());
                parserResult.setTotalCount(uploadLogs.size());
                // called for every batch, including an empty last one, so the final sync always happens
                processor.accept(parserResult, new WizImportJobPageContext(isFirstBatch, isLastBatch));

                int completedBatches = (i / BATCH_SIZE) + 1;
                if (completedBatches % 20 == 0 || isLastBatch) {
                    loggerMaker.infoAndAddToDb(String.format("Imperva import: completed batch %d/%d", completedBatches, totalBatches));
                }
                if (!isLastBatch) {
                    Thread.sleep(PAUSE_BETWEEN_BATCHES_MILLIS);
                }
            }
        } finally {
            detailsPool.shutdownNow();
        }

        loggerMaker.infoAndAddToDb(String.format("Imperva import completed. Endpoints: %d, basic samples (no detailed schema): %d, details call failures: %d, skipped: %d",
            endpoints.size(), fallbackCount, detailsFailures, failedCount));
        return new ImportResult(endpoints.size(), fallbackCount);
    }

    private static List<SwaggerUploadLog> convert(ImpervaSchema schema, EndpointSummary endpoint) {
        if (StringUtils.isEmpty(schema.getHostName())) schema.setHostName(endpoint.getHostName());
        if (StringUtils.isEmpty(schema.getMethod())) schema.setMethod(endpoint.getMethod());
        if (StringUtils.isEmpty(schema.getResource())) schema.setResource(endpoint.getResource());
        if (StringUtils.isEmpty(schema.getHostName()) || StringUtils.isEmpty(schema.getMethod()) || StringUtils.isEmpty(schema.getResource())) {
            return new ArrayList<>();
        }
        schema.setResource(replacePathWildcards(schema.getResource()));

        ParserResult parsed = ImpervaSchemaParser.convertImpervaSchemaToAkto(schema, null, true, false, true);
        return parsed.getUploadLogs() != null ? parsed.getUploadLogs() : new ArrayList<>();
    }

    /**
     * Imperva marks path parameters as "*" segments, e.g. /dps/apps/{@literal *}/users. Akto expects STRING
     * tokens so the endpoint is stored as a templated URL.
     */
    static String replacePathWildcards(String resource) {
        String[] segments = resource.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (IMPERVA_PATH_WILDCARD.equals(segments[i])) {
                segments[i] = AKTO_STRING_PATH_PARAM;
            }
        }
        return String.join("/", segments);
    }
}
