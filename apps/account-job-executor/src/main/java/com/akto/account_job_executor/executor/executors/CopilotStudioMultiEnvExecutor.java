package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dao.context.Context;
import com.akto.dto.CopilotStudioIntegration;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.executors.copilotstudio.CopilotStudioInventoryClient;
import com.akto.util.Constants;
import com.akto.jobs.executors.copilotstudio.CopilotStudioInventoryPublisher;
import com.akto.jobs.executors.copilotstudio.CopilotStudioMultiEnvApiClient;
import com.akto.jobs.executors.copilotstudio.CopilotStudioMultiEnvApiClient.AccessToken;
import com.akto.jobs.executors.copilotstudio.CopilotStudioUserResolver;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final CopilotStudioInventoryClient inventoryClient = new CopilotStudioInventoryClient();
    private static final CopilotStudioInventoryPublisher inventoryPublisher = new CopilotStudioInventoryPublisher();
    private static final int MAX_PARALLEL_ENVIRONMENTS = 8;
    private static final String DATABASE_ABSTRACTOR_SERVICE_TOKEN_ENV = "DATABASE_ABSTRACTOR_SERVICE_TOKEN";
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
            throw new IllegalArgumentException("Missing copilot studio integration ID for job: " + job.getId());
        }
        String integrationId = integrationIdObj.toString();
        logger.info("CopilotStudioMultiEnv: job started: jobId={}, integrationId={}", job.getId(), integrationId);

        CopilotStudioIntegration integration = CyborgApiClient.findCopilotStudioIntegrationById(integrationId);
        if (integration == null) {
            throw new IllegalArgumentException("CopilotStudioIntegration not found: " + integrationId);
        }

        AtomicReference<AccessToken> tokenRef = new AtomicReference<>(apiClient.getClientCredentialsTokenWithExpiry(
            integration.getTenantId(), integration.getClientId(), integration.getClientSecret()));

        List<CopilotStudioIntegration.Environment> discovered = new ArrayList<>();
        try {
            discovered = apiClient.listEnvironments(tokenRef.get().getToken());
        } catch (Exception e) {
            logger.error("CopilotStudioMultiEnv: failed to list environments for integration={}: {}",
                integrationId, e.getMessage());
        }
        int existingCount = integration.getEnvironments().size();
        mergeDiscoveredEnvironments(integration, discovered);
        int newCount = integration.getEnvironments().size() - existingCount;
        logger.info("CopilotStudioMultiEnv: integrationId={}, existingEnvironments={}, newEnvironments={}, totalEnvironments={}",
            integrationId, existingCount, newCount, integration.getEnvironments().size());

        updateJobHeartbeat(job);

        // Resolve Graph users once per job tick (cached to a local runtime file with a TTL,
        // see CopilotStudioUserResolver) so this never scales with environment/binary count.
        // The binary itself never calls Graph - it only receives a path to the derived map
        // file, not the content, since that content can exceed the 128KB env-var limit at scale.
        String resolvedUserMapFilePath = "";
        Map<String, String> ownerIdToUserId = null;
        try {
            resolvedUserMapFilePath = CopilotStudioUserResolver.buildUserMapFile(
                integration.getTenantId(), integration.getClientId(), integration.getClientSecret());
            // Cache-file read, not a second Graph call — buildUserMapFile above just populated it for this tenantId.
            ownerIdToUserId = CopilotStudioUserResolver.resolveUserMap(
                integration.getTenantId(), integration.getClientId(), integration.getClientSecret());
        } catch (Exception e) {
            logger.error("CopilotStudioMultiEnv: failed to build Graph user map: {}", e.getMessage());
        }
        final String userMapFilePath = resolvedUserMapFilePath;
        final Map<String, String> finalOwnerIdToUserId = ownerIdToUserId;

        int now = Context.now();
        List<CopilotStudioIntegration.Environment> environments = integration.getEnvironments();
        persistEnvironments(integrationId, integration);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        int totalEnvironments = environments.size();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        // {botName: {userID, ...}} merged from each environment's own output file; per-run only, not persisted across ticks.
        Map<String, Set<String>> botNameToKnownUserIds = new ConcurrentHashMap<>();

        // Background heartbeat: keeps the job alive independent of how long any single environment takes.
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "copilot-studio-multi-env-heartbeat-" + job.getId());
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                updateJobHeartbeat(job);
            } catch (Exception e) {
                logger.error("CopilotStudioMultiEnv: background heartbeat failed: {}", e.getMessage());
            }
        }, 4, 4, TimeUnit.SECONDS);

        int poolSize = Math.max(1, Math.min(environments.size(), MAX_PARALLEL_ENVIRONMENTS));
        ExecutorService envPool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "copilot-studio-multi-env-worker");
            t.setDaemon(true);
            return t;
        });

        List<Future<?>> futures = new ArrayList<>();
        for (CopilotStudioIntegration.Environment env : environments) {
            futures.add(envPool.submit(() ->
                processEnvironment(job, integration, env, tokenRef, now, failures, errors, completed, totalEnvironments,
                    userMapFilePath, botNameToKnownUserIds)));
        }

        envPool.shutdown();
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                logger.error("CopilotStudioMultiEnv: environment task error: {}", e.getMessage());
            }
        }

        // After transcripts, while the heartbeat still runs — inventory is additive and must never fail the transcript work.
        publishAgentInventory(integrationId, integration, job.getAccountId(), finalOwnerIdToUserId, botNameToKnownUserIds);

        heartbeatExecutor.shutdownNow();

        persistEnvironments(integrationId, integration);

        if (failures.get() > 0) {
            throw new Exception(failures.get() + " of " + environments.size()
                + " environment(s) failed: " + String.join("; ", errors));
        }

        logger.info("CopilotStudioMultiEnv job completed successfully: jobId={}, environments={}",
            job.getId(), environments.size());
    }

    private void processEnvironment(AccountJob job, CopilotStudioIntegration integration,
            CopilotStudioIntegration.Environment env, AtomicReference<AccessToken> tokenRef, int now,
            AtomicInteger failures, List<String> errors, AtomicInteger completed, int totalEnvironments,
            String userMapFilePath, Map<String, Set<String>> botNameToKnownUserIds) {
        logger.info("CopilotStudioMultiEnv: processing environment: environmentId={}, appUserCreated={}",
            env.getEnvironmentId(), env.isAppUserCreated());
        try {
            if (!env.isAppUserCreated()) {
                AccessToken token = refreshTokenIfExpired(integration, tokenRef);
                apiClient.createApplicationUser(token.getToken(), env.getEnvironmentId(), integration.getClientId());
                env.setAppUserCreated(true);
                logger.info("CopilotStudioMultiEnv: app user created: environmentId={}", env.getEnvironmentId());
            }

            // Environment id in the filename keeps this unique per parallel binary execution — no shared file to race on.
            File userAgentMapFile = new File(System.getProperty("java.io.tmpdir"),
                "copilot-studio-user-agent-map-" + integration.getTenantId() + "-" + env.getEnvironmentId() + ".json");

            Map<String, Object> envConfig = new HashMap<>();
            envConfig.put(CONFIG_DATAVERSE_ENVIRONMENT_URL, env.getEnvironmentUrl());
            envConfig.put(CONFIG_DATAVERSE_TENANT_ID, integration.getTenantId());
            envConfig.put(CONFIG_DATAVERSE_CLIENT_ID, integration.getClientId());
            envConfig.put(CONFIG_DATAVERSE_CLIENT_SECRET, integration.getClientSecret());
            envConfig.put(CONFIG_DATA_INGESTION_SERVICE_URL, integration.getDataIngestionUrl());
            envConfig.put("COPILOT_STUDIO_USER_MAP_FILE", userMapFilePath);
            envConfig.put("COPILOT_STUDIO_USER_AGENT_MAP_OUTPUT_FILE", userAgentMapFile.getAbsolutePath());

                BinaryConnectorRunner.run(job, envConfig, BINARY_NAME_COPILOT_STUDIO);

            readUserAgentMapFile(userAgentMapFile, env.getEnvironmentId(), botNameToKnownUserIds);

            env.setLastIngestedAt(now);
            env.setLastError(null);
            logger.info("CopilotStudioMultiEnv: environment ingested successfully: environmentId={}",
                env.getEnvironmentId());
        } catch (Exception e) {
            failures.incrementAndGet();
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            env.setLastError(null);
            errors.add(env.getEnvironmentId() + ": " + reason);
            logger.error("CopilotStudioMultiEnv: environment failed: environmentId={}, error={}",
                env.getEnvironmentId(), reason);
        } finally {
            int done = completed.incrementAndGet();
            logger.info("CopilotStudioMultiEnv: progress: {} of {} environments processed, {} remaining",
                done, totalEnvironments, totalEnvironments - done);
        }
    }

    /** Reads one environment's {botName: [userID,...]} file into the job-wide map (keys scoped by environmentId), then deletes it. */
    private void readUserAgentMapFile(File file, String environmentId, Map<String, Set<String>> botNameToKnownUserIds) {
        try {
            if (!file.exists()) {
                return;
            }
            Map<String, List<String>> fresh = objectMapper.readValue(file, new TypeReference<Map<String, List<String>>>() {});
            for (Map.Entry<String, List<String>> entry : fresh.entrySet()) {
                Set<String> users = botNameToKnownUserIds.computeIfAbsent(
                    CopilotStudioInventoryPublisher.botKey(environmentId, entry.getKey()), k -> Collections.synchronizedSet(new HashSet<>()));
                synchronized (users) {
                    users.addAll(entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.error("CopilotStudioMultiEnv: failed to read user-agent map file {}: {}", file, e.getMessage());
        } finally {
            file.delete();
        }
    }

    /** Pulls tenant-wide agent inventory once per job (not per environment) and publishes samples; best-effort, never blocks transcripts: https://learn.microsoft.com/en-us/power-platform/admin/power-platform-inventory#access-requirements */
    private void publishAgentInventory(String integrationId, CopilotStudioIntegration integration, int accountId,
            Map<String, String> ownerIdToUserId, Map<String, Set<String>> botNameToKnownUserIds) {
        try {
            // Existing integrations default off until the customer opts in; new ones enable this at setup.
            if (!integration.isAgentGraphEnabled()) {
                logger.info("CopilotStudioMultiEnv: agent graphs not enabled, skipping inventory. integrationId={}", integrationId);
                return;
            }

            String ingestionUrl = integration.getDataIngestionUrl();
            if (ingestionUrl == null || ingestionUrl.isEmpty()) {
                logger.warn("CopilotStudioMultiEnv: no data ingestion URL, skipping agent inventory");
                return;
            }

            String storedRefreshToken = integration.getRefreshToken();
            if (storedRefreshToken == null || storedRefreshToken.isEmpty()) {
                // Only ever null when the customer connected before this feature shipped — never cleared by us.
                logger.warn("CopilotStudioMultiEnv: no delegated refresh token on file, skipping agent "
                    + "inventory until the customer reconnects. integrationId={}", integrationId);
                return;
            }

            AccessToken inventoryToken;
            try {
                inventoryToken = apiClient.getDelegatedTokenFromRefreshToken(
                    integration.getTenantId(), integration.getClientId(), integration.getClientSecret(),
                    storedRefreshToken, Constants.SCOPE_COPILOT_STUDIO_INVENTORY);
            } catch (Exception e) {
                // Token kept, never cleared — this also catches transient failures, and only a sign-in can replace it.
                logger.warn("CopilotStudioMultiEnv: delegated refresh token rejected, skipping agent "
                    + "inventory this run. integrationId={} error={}", integrationId, e.getMessage());
                return;
            }

            // Rotated on use; written on its own so a concurrent Reconnect can't be clobbered by this run's stale copy.
            if (inventoryToken.getRefreshToken() != null
                    && !inventoryToken.getRefreshToken().equals(storedRefreshToken)) {
                integration.setRefreshToken(inventoryToken.getRefreshToken());
                CyborgApiClient.updateCopilotStudioRefreshToken(integrationId, inventoryToken.getRefreshToken());
            }

            List<JsonNode> agents = inventoryClient.fetchAgents(inventoryToken.getToken(), null);
            if (agents.isEmpty()) {
                logger.info("CopilotStudioMultiEnv: agent inventory returned no agents");
                return;
            }

            // One call covers the tenant; split by environment so each sample gets its environment's host.
            Map<String, List<JsonNode>> agentsByEnvironment = new HashMap<>();
            for (JsonNode agent : agents) {
                String environmentId = agent.path("properties").path("environmentId").asText("");
                agentsByEnvironment.computeIfAbsent(environmentId, k -> new ArrayList<>()).add(agent);
            }

            String jwtToken = System.getenv(DATABASE_ABSTRACTOR_SERVICE_TOKEN_ENV);
            int published = 0;
            for (Map.Entry<String, List<JsonNode>> entry : agentsByEnvironment.entrySet()) {
                List<Map<String, Object>> samples = inventoryPublisher.buildSamples(
                    entry.getValue(), entry.getKey(), null, accountId, ownerIdToUserId, botNameToKnownUserIds);
                published += inventoryPublisher.publish(ingestionUrl, jwtToken, samples);
            }

            logger.info("CopilotStudioMultiEnv: published inventory for {} agents across {} environments",
                published, agentsByEnvironment.size());

        } catch (CopilotStudioInventoryClient.InventoryException e) {
            if (e.isAuthorizationError()) {
                logger.warn("CopilotStudioMultiEnv: agent inventory not authorized - the user who "
                    + "connected needs Global Administrator, Power Platform Administrator, Dynamics 365 "
                    + "Administrator, Global Reader, AI Administrator or AI Reader. Skipping. " + e.getMessage());
            } else {
                logger.error("CopilotStudioMultiEnv: agent inventory query failed: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.error("CopilotStudioMultiEnv: agent inventory failed: " + e.getMessage());
        }
    }

    private AccessToken refreshTokenIfExpired(CopilotStudioIntegration integration, AtomicReference<AccessToken> tokenRef)
            throws Exception {
        AccessToken current = tokenRef.get();
        if (!current.isExpired()) {
            return current;
        }
        synchronized (tokenRef) {
            current = tokenRef.get();
            if (current.isExpired()) {
                current = apiClient.getClientCredentialsTokenWithExpiry(
                    integration.getTenantId(), integration.getClientId(), integration.getClientSecret());
                tokenRef.set(current);
            }
            return current;
        }
    }

    /** Adds newly-discovered environments; leaves already-known ones (and their appUserCreated/lastIngestedAt state) untouched. */
    private static void mergeDiscoveredEnvironments(CopilotStudioIntegration integration,
                                                     List<CopilotStudioIntegration.Environment> discovered) {
        List<CopilotStudioIntegration.Environment> environments = integration.getEnvironments();
        Map<String, CopilotStudioIntegration.Environment> existingById = new HashMap<>();
        for (CopilotStudioIntegration.Environment env : environments) {
            existingById.put(env.getEnvironmentId(), env);
        }

        for (CopilotStudioIntegration.Environment env : discovered) {
            CopilotStudioIntegration.Environment existing = existingById.get(env.getEnvironmentId());
            if (existing == null) {
                environments.add(env);
                // logger.info("CopilotStudioMultiEnv: discovered new environment: environmentId={}", env.getEnvironmentId());
            } else {
                existing.setEnvironmentName(env.getEnvironmentName());
            }
        }
    }

    /** Writes environments + updatedAt only; refreshToken has its own endpoint so the two can't clobber each other. */
    private static void persistEnvironments(String integrationId, CopilotStudioIntegration integration) {
        integration.setUpdatedAt(Context.now());
        CyborgApiClient.updateCopilotStudioIntegration(integrationId, integration);
    }
}
