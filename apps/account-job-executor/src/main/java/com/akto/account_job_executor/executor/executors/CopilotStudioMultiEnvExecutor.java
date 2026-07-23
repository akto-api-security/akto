package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dao.context.Context;
import com.akto.dto.CopilotStudioIntegration;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.executors.copilotstudio.CopilotStudioMultiEnvApiClient;
import com.akto.jobs.executors.copilotstudio.CopilotStudioMultiEnvApiClient.EnvironmentInfo;
import com.akto.log.LoggerMaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Executor for the Copilot Studio (Multi Environment) connector.
 * On every recurring run: re-fetches the environment list from Microsoft, creates the Dataverse
 * application user only for environments that don't already have one, then hands off to the
 * same copilot-shield binary AIAgentConnectorExecutor uses for COPILOT_STUDIO — once per environment.
 *
 * This is a singleton executor - use CopilotStudioMultiEnvExecutor.INSTANCE to access it.
 */
public class CopilotStudioMultiEnvExecutor extends AccountJobExecutor {

    public static final CopilotStudioMultiEnvExecutor INSTANCE = new CopilotStudioMultiEnvExecutor();

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioMultiEnvExecutor.class);
    private static final CopilotStudioMultiEnvApiClient apiClient = new CopilotStudioMultiEnvApiClient();

    private CopilotStudioMultiEnvExecutor() {
    }

    @Override
    protected void runJob(AccountJob job) throws Exception {
        Map<String, Object> config = job.getConfig();
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        Object integrationIdObj = config.get(CONFIG_COPILOT_STUDIO_MULTI_ENV_INTEGRATION_ID);
        if (integrationIdObj == null) {
            throw new IllegalArgumentException("Missing " + CONFIG_COPILOT_STUDIO_MULTI_ENV_INTEGRATION_ID + " for job: " + job.getId());
        }
        String integrationId = integrationIdObj.toString();

        CopilotStudioIntegration integration = CyborgApiClient.findCopilotStudioIntegrationById(integrationId);
        if (integration == null) {
            throw new IllegalArgumentException("CopilotStudioIntegration not found: " + integrationId);
        }

        String appOnlyToken = apiClient.getClientCredentialsToken(
            integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

        List<EnvironmentInfo> discovered = apiClient.listEnvironments(appOnlyToken);
        mergeDiscoveredEnvironments(integration, discovered);

        updateJobHeartbeat(job);

        int now = Context.now();
        int failures = 0;
        StringBuilder errorSummary = new StringBuilder();

        for (CopilotStudioIntegration.Environment env : integration.getEnvironments()) {
            try {
                if (!env.isAppUserCreated()) {
                    apiClient.createApplicationUser(appOnlyToken, env.getEnvironmentUrl(), integration.getClientId());
                    env.setAppUserCreated(true);
                }

                Map<String, Object> envConfig = new HashMap<>();
                envConfig.put(CONFIG_DATAVERSE_ENVIRONMENT_URL, env.getEnvironmentUrl());
                envConfig.put(CONFIG_DATAVERSE_TENANT_ID, integration.getTenantId());
                envConfig.put(CONFIG_DATAVERSE_CLIENT_ID, integration.getClientId());
                envConfig.put(CONFIG_DATAVERSE_CLIENT_SECRET, integration.getClientSecret());
                envConfig.put(CONFIG_DATA_INGESTION_SERVICE_URL, integration.getDataIngestionUrl());

                BinaryConnectorRunner.run(job, envConfig, BINARY_NAME_COPILOT_STUDIO);

                env.setLastIngestedAt(now);
                env.setLastError(null);
            } catch (Exception e) {
                failures++;
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                env.setLastError(reason);
                errorSummary.append(env.getEnvironmentId()).append(": ").append(reason).append("; ");
                logger.error("CopilotStudioMultiEnv: environment failed: environmentId={}, error={}",
                    env.getEnvironmentId(), reason);
            }

            updateJobHeartbeat(job);
        }

        persistEnvironments(integrationId, integration.getEnvironments());

        if (failures > 0) {
            throw new Exception(failures + " of " + integration.getEnvironments().size()
                + " environment(s) failed: " + errorSummary);
        }

        logger.info("CopilotStudioMultiEnv job completed successfully: jobId={}, environments={}",
            job.getId(), integration.getEnvironments().size());
    }

    /** Adds newly-discovered environments; leaves already-known ones (and their appUserCreated/lastIngestedAt state) untouched. */
    private static void mergeDiscoveredEnvironments(CopilotStudioIntegration integration, List<EnvironmentInfo> discovered) {
        Map<String, CopilotStudioIntegration.Environment> known = new HashMap<>();
        for (CopilotStudioIntegration.Environment env : integration.getEnvironments()) {
            known.put(env.getEnvironmentId(), env);
        }

        for (EnvironmentInfo info : discovered) {
            if (!known.containsKey(info.id)) {
                CopilotStudioIntegration.Environment newEnv =
                    new CopilotStudioIntegration.Environment(info.id, info.url, info.name);
                integration.getEnvironments().add(newEnv);
                known.put(info.id, newEnv);
                logger.info("CopilotStudioMultiEnv: discovered new environment: environmentId={}", info.id);
            }
        }
    }

    private static void persistEnvironments(String integrationId, List<CopilotStudioIntegration.Environment> environments) {
        List<Map<String, Object>> environmentMaps = new ArrayList<>();
        for (CopilotStudioIntegration.Environment env : environments) {
            Map<String, Object> map = new HashMap<>();
            map.put("environmentId", env.getEnvironmentId());
            map.put("environmentUrl", env.getEnvironmentUrl());
            map.put("environmentName", env.getEnvironmentName());
            map.put("appUserCreated", env.isAppUserCreated());
            map.put("lastIngestedAt", env.getLastIngestedAt());
            map.put("lastError", env.getLastError());
            environmentMaps.add(map);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("environments", environmentMaps);
        updates.put("updatedAt", Context.now());

        CyborgApiClient.updateCopilotStudioIntegration(integrationId, updates);
    }
}
