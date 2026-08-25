package com.akto.jobs.executors.copilotstudio;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.AgenticUsers;
import com.akto.dto.CopilotStudioIntegration;
import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.jobs.executors.copilotstudio.CopilotStudioUserResolver.GraphUser;
import com.akto.log.LoggerMaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps a durable, DB-backed copy of every connected Copilot Studio tenant's Microsoft Graph
 * user directory (agent_users collection) refreshed on a fixed schedule, and mirrors it in an
 * in-memory cache so CopilotStudioWebhookAction can resolve a conversation's AAD user id to the
 * sanitized userId string (see CopilotStudioUserResolver.buildUserId) without touching Graph or
 * Mongo on the request path.
 *
 * data-ingestion-service has no direct database access — like every other module, it goes
 * through DataActor (DbActor for direct-DB deployments, ClientActor for hybrid/SaaS, where it
 * calls the database-abstractor service). See DataActor's CLAUDE.md for the 4-file pattern
 * behind fetchCopilotStudioIntegrations/fetchAllAgentUsers/upsertAgentUserExternalIdentity.
 *
 * Singleton, started once from data-ingestion-service's InitializerListener; modeled on
 * McpCollectionResolver's shape (AtomicBoolean-guarded start(), daemon ScheduledExecutorService,
 * infoAndAddToDb/errorAndAddToDb logging so runs are diagnosable from the logs collection even
 * without console access to the running process).
 */
public class CopilotStudioAgentUsersCron {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioAgentUsersCron.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final CopilotStudioAgentUsersCron INSTANCE = new CopilotStudioAgentUsersCron();

    private static final ConcurrentHashMap<String, AgenticUsers> cache = new ConcurrentHashMap<>();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private DataActor dataActor;
    private ScheduledExecutorService scheduler;

    private CopilotStudioAgentUsersCron() {
    }

    public static CopilotStudioAgentUsersCron getInstance() {
        return INSTANCE;
    }

    /** Cache lookup by AAD object id, used by CopilotStudioWebhookAction; null on a miss. */
    public static AgenticUsers getCachedUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return cache.get(userId);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        logger.infoAndAddToDb("CopilotStudioAgentUsersCron: starting");
        dataActor = DataActorFactory.fetchInstance();
        try {
            loadCacheFromDb();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "CopilotStudioAgentUsersCron: initial DB load failed: " + e.getMessage());
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "copilot-studio-agent-users-sync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::syncFromGraphSafely, 0,
            AIAgentConnectorConstants.COPILOT_STUDIO_AGENT_USERS_SYNC_INTERVAL_HOURS, TimeUnit.HOURS);
        logger.infoAndAddToDb("CopilotStudioAgentUsersCron: scheduled every "
            + AIAgentConnectorConstants.COPILOT_STUDIO_AGENT_USERS_SYNC_INTERVAL_HOURS + "h, first run immediate");
    }

    /**
     * DB-only warm-up so the cache is usable immediately on startup, before the first Graph sync
     * completes. agent_users is shared with other identity sources (SSO, inference-hooks devices)
     * whose rows never set userId — ConcurrentHashMap rejects a null key outright, so those rows
     * are skipped here rather than cached.
     */
    private void loadCacheFromDb() {
        List<AgenticUsers> existing = dataActor.fetchAllAgentUsers();
        int skipped = 0;
        for (AgenticUsers user : existing) {
            String userId = user.getUserId();
            if (userId == null || userId.isEmpty()) {
                skipped++;
                continue;
            }
            cache.put(userId, user);
        }
        logger.infoAndAddToDb("CopilotStudioAgentUsersCron: loaded " + (existing.size() - skipped)
            + " users from DB (" + skipped + " skipped, no userId)");
    }

    private void syncFromGraphSafely() {
        try {
            syncFromGraph();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "CopilotStudioAgentUsersCron: sync failed: " + e.getMessage());
        }
    }

    private void syncFromGraph() {
        List<CopilotStudioIntegration> integrations = dataActor.fetchCopilotStudioIntegrations();
        logger.infoAndAddToDb("CopilotStudioAgentUsersCron: sync tick, integrations found=" + integrations.size());
        for (CopilotStudioIntegration integration : integrations) {
            try {
                syncTenant(integration);
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "CopilotStudioAgentUsersCron: sync failed for tenantId="
                    + integration.getTenantId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Fetches this tenant's full Graph directory fresh, and bulk-upserts only users that are new
     * or changed since the last sync in one Mongo round trip (a tenant can have tens of thousands
     * of users — never write these one at a time) — leaves everything else (including users no
     * longer in Graph) untouched in both DB and cache. The cache only reflects the bulk write
     * after it succeeds, so a failed write doesn't leave the cache claiming a state the DB doesn't
     * actually have (next tick just retries the same diff).
     */
    private void syncTenant(CopilotStudioIntegration integration) throws Exception {
        List<GraphUser> freshUsers = CopilotStudioUserResolver.fetchAllUsersFresh(
            integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

        List<AgenticUsers> toUpsert = new ArrayList<>();
        for (GraphUser u : freshUsers) {
            if (u.id == null || u.id.isEmpty()) continue;

            String userName = CopilotStudioUserResolver.buildUserId(u);
            AgenticUsers cached = cache.get(u.id);
            boolean unchanged = cached != null
                && userName.equals(cached.getUserName())
                && Objects.equals(u.userPrincipalName, cached.getUserEmail());
            if (unchanged) continue;

            AgenticUsers refreshed = new AgenticUsers();
            refreshed.setUserId(u.id);
            refreshed.setUserName(userName);
            refreshed.setUserEmail(u.userPrincipalName);
            refreshed.setLastUpdatedBy("copilot-studio-sync");
            toUpsert.add(refreshed);
        }

        if (!toUpsert.isEmpty()) {
            dataActor.bulkUpsertAgentUserExternalIdentities(toUpsert);
            for (AgenticUsers u : toUpsert) {
                cache.put(u.getUserId(), u);
            }
        }
        logger.infoAndAddToDb("CopilotStudioAgentUsersCron: tenantId=" + integration.getTenantId()
            + ", fetched=" + freshUsers.size() + ", updated=" + toUpsert.size());
    }
}
