package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.account_job_executor.ingestion.IngestionHttpClient;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.jobs.executors.ibmwatsonx.WatsonxApiException;
import com.akto.jobs.executors.ibmwatsonx.WatsonxAuth;
import com.akto.jobs.executors.ibmwatsonx.WatsonxClient;
import com.akto.jobs.executors.ibmwatsonx.WatsonxPoller;
import com.akto.jobs.executors.ibmwatsonx.WatsonxRecords;
import com.akto.jobs.executors.ibmwatsonx.WatsonxTransport;
import com.akto.log.LoggerMaker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_DATA_INGESTION_SERVICE_URL;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_JWT_TOKEN;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_API_KEY;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_WATSONX_INSTANCE_URL;

/**
 * IBM watsonx Orchestrate connector (async): every run discovers the instance's agents and
 * control plane, reads new conversation traces and sends both to Akto. The work is done by
 * {@link WatsonxPoller}; this class connects it to the job framework (config, cursor, heartbeat,
 * ingestion) and maps IBM errors to job outcomes.
 */
public class IbmWatsonxExecutor extends AccountJobExecutor {

    public static final IbmWatsonxExecutor INSTANCE = new IbmWatsonxExecutor();

    private static final LoggerMaker logger = new LoggerMaker(IbmWatsonxExecutor.class);
    /** Stop after the current page once this much of the run is used (runs are every 5 minutes). */
    private static final long RUN_BUDGET_MS = 4 * 60 * 1000L;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS = 60_000;
    /** Optional config key: the source tag sent to Akto (default IBM_WATSONX). */
    static final String CONFIG_SOURCE_TAG = "WATSONX_SOURCE_TAG";

    private IbmWatsonxExecutor() {
    }

    @Override
    protected void runJob(AccountJob job) throws Exception {
        Map<String, Object> config = job.getConfig();
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Job config is empty for IBM watsonx job " + job.getId());
        }
        String instanceUrl = WatsonxClient.normalizeInstanceUrl(string(config.get(CONFIG_WATSONX_INSTANCE_URL)));
        String apiKey = string(config.get(CONFIG_WATSONX_API_KEY));
        String ingestionUrl = string(config.get(CONFIG_DATA_INGESTION_SERVICE_URL));
        if (instanceUrl == null || apiKey == null || ingestionUrl == null) {
            throw new IllegalArgumentException("IBM watsonx job " + job.getId() + " is missing the instance URL, API key or ingestion URL");
        }

        WatsonxTransport transport = new WatsonxTransport.OkHttp();
        WatsonxClient client = new WatsonxClient(instanceUrl, new WatsonxAuth(instanceUrl, apiKey, transport, System::currentTimeMillis),
                transport);
        WatsonxRecords.Context context = new WatsonxRecords.Context(string(config.get(CONFIG_SOURCE_TAG)), job.getAccountId(),
                job.getJobType(), job.getSubType());

        try (IngestionHttpClient ingestion = new IngestionHttpClient(ingestionUrl, string(config.get(CONFIG_JWT_TOKEN)),
                CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS, String.valueOf(job.getId()), job.getAccountId())) {
            int[] batch = {0};
            WatsonxPoller poller = new WatsonxPoller(
                    client,
                    context,
                    records -> ingestion.sendBatch(records, ++batch[0], () -> updateJobHeartbeat(job)),
                    updates -> saveToJobConfig(job, updates),
                    () -> updateJobHeartbeat(job),
                    System::currentTimeMillis,
                    RUN_BUDGET_MS);
            WatsonxPoller.Summary summary = poller.run(config);
            logger.info("IBM watsonx job {} done: {}", job.getId(), summary);
        } catch (WatsonxApiException e) {
            if (e.isAuthFailure()) {
                // Not retryable: the key must be updated (re-saving the connector keeps the cursor).
                throw new IllegalStateException(e.getMessage(), e);
            }
            throw new RetryableJobException(e.getMessage(), e);
        } catch (IngestionHttpClient.IngestionHttpException e) {
            if (e.isRetryable()) {
                throw new RetryableJobException(e.getMessage(), e);
            }
            throw e;
        } catch (IOException e) {
            // Network problems: progress so far is saved; try again on the next run.
            throw new RetryableJobException(e.getMessage() != null ? e.getMessage() : "Transient failure", e);
        }
    }

    private static void saveToJobConfig(AccountJob job, Map<String, Object> updates) {
        Map<String, Object> prefixed = new HashMap<>();
        for (Map.Entry<String, Object> update : updates.entrySet()) {
            prefixed.put("config." + update.getKey(), update.getValue());
            job.getConfig().put(update.getKey(), update.getValue());
        }
        CyborgApiClient.updateJob(job.getId(), prefixed);
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
