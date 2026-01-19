package com.akto.jobs.executors;

import com.akto.dao.context.Context;
import com.akto.dto.jobs.DatadogTrafficCollectorJobParams;
import com.akto.dto.jobs.Job;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.otel.OtelTraceImporter;
import com.akto.otel.OtelTraceImporter.ImportRequest;
import com.akto.otel.OtelTraceImporter.ImportResult;
import com.akto.otel.TraceProcessingService;

public class DatadogTrafficCollectorJobExecutor extends JobExecutor<DatadogTrafficCollectorJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(DatadogTrafficCollectorJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final DatadogTrafficCollectorJobExecutor INSTANCE = new DatadogTrafficCollectorJobExecutor();

    public DatadogTrafficCollectorJobExecutor() {
        super(DatadogTrafficCollectorJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        logger.info("Starting Datadog traffic collector job for job: {}", job.getId());
        DatadogTrafficCollectorJobParams params = paramClass.cast(job.getJobParams());

        long currentTimestamp = System.currentTimeMillis();
        long lastJobRunTimestamp = params.getLastJobRunTimestamp();

        // For initial scan, use timestamp 0
        if (lastJobRunTimestamp == 0) {
            logger.info("Initial scan detected. Fetching all available spans.");
        }

        long startTime = lastJobRunTimestamp;
        long endTime = currentTimestamp;

        try {
            OtelTraceImporter importer = new OtelTraceImporter(
                params.getDatadogApiKey(),
                params.getDatadogAppKey(),
                params.getDatadogSite()
            );

            ImportRequest request = new ImportRequest(
                startTime,
                endTime,
                params.getServiceNames(),
                params.getLimit()
            );

            ImportResult result = importer.importTraces(request, Context.accountId.get());

            logger.info("Fetched {} traces, converted {} traces",
                result.getTracesProcessed(), result.getTracesConverted());

            // Process the converted traces through the API pipeline
            if (result.getTraces() != null && !result.getTraces().isEmpty()) {
                TraceProcessingService.Holder.getInstance().processTraces(
                    result.getTraces(),
                    Context.accountId.get()
                );
                logger.info("Successfully processed {} converted traces through API pipeline",
                    result.getTraces().size());
            } else {
                logger.info("No traces to process for this interval.");
            }

            // Update lastJobRunTimestamp to current timestamp
            params.setLastJobRunTimestamp(currentTimestamp);
            updateJobParams(job, params);

            logger.info("Datadog traffic collector job completed successfully.");
        } catch (Exception e) {
            logger.error("Error in Datadog traffic collector job: {}", e.getMessage(), e);
            throw e;
        }
    }
}
