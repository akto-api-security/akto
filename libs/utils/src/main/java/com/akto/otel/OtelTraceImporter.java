package com.akto.otel;

import java.util.List;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class OtelTraceImporter {

    private static final LoggerMaker logger = new LoggerMaker(OtelTraceImporter.class, LogDb.DASHBOARD);

    private final DatadogOtelClient datadogClient;
    private final OtelSpanConverter spanConverter;

    public OtelTraceImporter(String datadogApiKey, String datadogAppKey, String datadogSite) {
        this.datadogClient = new DatadogOtelClient(datadogApiKey, datadogAppKey, datadogSite);
        this.spanConverter = new OtelSpanConverter();
    }

    public ImportResult importTraces(ImportRequest request, int accountId) throws Exception {
        logger.info("Fetching spans from Datadog...");
        String rawSpansJson = datadogClient.fetchSpans(
            request.getStartTimeMillis(),
            request.getEndTimeMillis(),
            request.getServiceNames(),
            request.getLimit()
        );

        logger.info("Raw spans JSON length: {}", rawSpansJson != null ? rawSpansJson.length() : 0);
        logger.info("Converting spans to Akto format...");

        OtelSpanConverter.ConversionResult conversionResult = spanConverter.convert(rawSpansJson, accountId);
        // here call processResponseParams to process the converted traces

        logger.info("Import complete: processed={}, converted={}",
            conversionResult.getTotalProcessed(),
            conversionResult.getSuccessfullyConverted());

        return new ImportResult(
            conversionResult.getTraces(),
            conversionResult.getTotalProcessed(),
            conversionResult.getSuccessfullyConverted()
        );
    }

    /**
     * Request parameters for trace import
     */
    public static class ImportRequest {
        private final long startTimeMillis;
        private final long endTimeMillis;
        private final List<String> serviceNames;
        private final int limit;

        public ImportRequest(long startTimeMillis, long endTimeMillis, List<String> serviceNames, int limit) {
            this.startTimeMillis = startTimeMillis;
            this.endTimeMillis = endTimeMillis;
            this.serviceNames = serviceNames;
            this.limit = limit > 0 ? limit : 100;
        }

        public long getStartTimeMillis() {
            return startTimeMillis;
        }

        public long getEndTimeMillis() {
            return endTimeMillis;
        }

        public List<String> getServiceNames() {
            return serviceNames;
        }

        public int getLimit() {
            return limit;
        }
    }

    /**
     * Result of trace import operation
     */
    public static class ImportResult {
        private final List<HttpResponseParams> traces;
        private final int tracesProcessed;
        private final int tracesConverted;

        public ImportResult(List<HttpResponseParams> traces, int tracesProcessed, int tracesConverted) {
            this.traces = traces;
            this.tracesProcessed = tracesProcessed;
            this.tracesConverted = tracesConverted;
        }

        public List<HttpResponseParams> getTraces() {
            return traces;
        }

        public int getTracesProcessed() {
            return tracesProcessed;
        }

        public int getTracesConverted() {
            return tracesConverted;
        }
    }
}
