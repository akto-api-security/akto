package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

/**
 * One-time backfill: creates the mcp_audit_info row (type=browser-llm, resourceName=site) for browser LLM sites
 * discovered before cyborg / mini-runtime started writing them on discovery, so the Audit Data LLMs tab lists them.
 * Runs once per account, tracked by BackwardCompatibility.browserLlmAuditBackfill (same pattern as fillQueryParams).
 * Idempotent: rows are only inserted / have lastDetected moved forward; Allow / Block decisions are never touched.
 */
public class BrowserLlmAuditBackfillCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BrowserLlmAuditBackfillCron.class, LogDb.DASHBOARD);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void setUpBrowserLlmAuditBackfillCronScheduler() {
        scheduler.schedule(this::run, 0, TimeUnit.SECONDS);
        scheduler.shutdown();
    }

    private void run() {
        try {
            AccountTask.instance.executeTask(this::processAccount, "browser-llm-audit-backfill-cron");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in browser LLM audit backfill: " + e.getMessage());
        }
    }

    void processAccount(Account account) {
        int accountId = account.getId();
        try {
            BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
            if (backwardCompatibility == null || backwardCompatibility.getBrowserLlmAuditBackfill() != 0) return;

            // Latest collection start time per site; hostName is <user>.<browser>.<site>.
            Map<String, Integer> lastSeenBySite = new HashMap<>();
            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.and(
                            Filters.eq(ApiCollection.TAGS_STRING + "." + CollectionTags.KEY_NAME, Constants.AKTO_BROWSER_LLM_TAG),
                            Filters.ne(ApiCollection._DEACTIVATED, true)),
                    Projections.include(ApiCollection.HOST_NAME, ApiCollection.START_TS));
            for (ApiCollection c : collections) {
                String site = browserLlmSite(c.getHostName());
                if (site == null) continue;
                lastSeenBySite.merge(site, c.getStartTs(), Math::max);
            }
            if (lastSeenBySite.isEmpty()) {
                markDone(backwardCompatibility);
                return;
            }

            // Same key as DbLayer.insertMCPAuditDataLog (type + resourceName), so it lands on the discovery row.
            List<WriteModel<McpAuditInfo>> writes = new ArrayList<>();
            for (Map.Entry<String, Integer> e : lastSeenBySite.entrySet()) {
                writes.add(new UpdateOneModel<>(
                        Filters.and(
                                Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_BROWSER_LLM_TAG),
                                Filters.eq(McpAuditInfo.RESOURCE_NAME, e.getKey())),
                        Updates.combine(
                                Updates.max(McpAuditInfo.LAST_DETECTED, e.getValue()),
                                Updates.setOnInsert(McpAuditInfo.CONTEXT_SOURCE, CONTEXT_SOURCE.ENDPOINT.name())),
                        new UpdateOptions().upsert(true)));
            }
            McpAuditInfoDao.instance.getMCollection().bulkWrite(writes, new BulkWriteOptions().ordered(false));
            markDone(backwardCompatibility);
            loggerMaker.infoAndAddToDb("Browser LLM audit backfill synced accountId=" + accountId + ", sites=" + writes.size());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in browser LLM audit backfill for accountId=" + accountId + ": " + e.getMessage());
        }
    }

    // Same grouping as the Agentic assets LLMs tab (AgenticObserveAction.extractServiceNameForGrouping), so both tabs list the same sites.
    private static final Set<String> SERVICE_NAME_SUFFIX_TLDS = new HashSet<>(Arrays.asList(
            "io", "com", "net", "org", "cloud", "dev", "app", "ai", "co"));

    private static String browserLlmSite(String hostName) {
        if (StringUtils.isBlank(hostName) || hostName.contains(AgenticObserveUtil.NOT_ATTACHED_VALUE)) return null;
        String host = hostName.toLowerCase(Locale.ROOT);
        String[] parts = host.split("\\.");
        if (parts.length < 3 || SERVICE_NAME_SUFFIX_TLDS.contains(parts[2])) return host;
        return String.join(".", Arrays.copyOfRange(parts, 2, parts.length));
    }

    // Marked only after a successful run, so a failed account retries on the next startup.
    private void markDone(BackwardCompatibility backwardCompatibility) {
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.BROWSER_LLM_AUDIT_BACKFILL, Context.now()));
    }
}
