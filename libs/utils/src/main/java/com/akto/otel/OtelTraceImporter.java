package com.akto.otel;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.List;

/**
 * Service for importing OpenTelemetry traces from Datadog
 * Orchestrates the fetching and conversion process
 */
public class OtelTraceImporter {

    private static final LoggerMaker logger = new LoggerMaker(OtelTraceImporter.class, LogDb.DASHBOARD);

    private final DatadogOtelClient datadogClient;
    private final OtelSpanConverter spanConverter;

    public OtelTraceImporter(String datadogApiKey, String datadogAppKey, String datadogSite) {
        this.datadogClient = new DatadogOtelClient(datadogApiKey, datadogAppKey, datadogSite);
        this.spanConverter = new OtelSpanConverter();
    }

    /**
     * Imports traces from Datadog and converts them to Akto format
     *
     * @param request Import request parameters
     * @param accountId Account ID for the traces
     * @return Import result containing converted traces and statistics
     * @throws Exception if import fails
     */
    public ImportResult importTraces(ImportRequest request, int accountId) throws Exception {

        String rawSpansJson = datadogClient.fetchSpans(
            request.getStartTimeSeconds(),
            request.getEndTimeSeconds(),
            request.getServiceNames(),
            request.getLimit()
        );

        OtelSpanConverter.ConversionResult conversionResult = spanConverter.convert(rawSpansJson, accountId);
        // here call processResponseParams to process the converted traces

        logger.infoAndAddToDb(String.format(
            "Import complete: processed=%d, converted=%d",
            conversionResult.getTotalProcessed(),
            conversionResult.getSuccessfullyConverted()
        ));

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
        private final long startTimeSeconds;
        private final long endTimeSeconds;
        private final List<String> serviceNames;
        private final int limit;

        public ImportRequest(long startTimeSeconds, long endTimeSeconds, List<String> serviceNames, int limit) {
            this.startTimeSeconds = startTimeSeconds;
            this.endTimeSeconds = endTimeSeconds;
            this.serviceNames = serviceNames;
            this.limit = limit > 0 ? limit : 100;
        }

        public long getStartTimeSeconds() {
            return startTimeSeconds;
        }

        public long getEndTimeSeconds() {
            return endTimeSeconds;
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
