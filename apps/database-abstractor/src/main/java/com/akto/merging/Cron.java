package com.akto.merging;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.merging.MergeAuditLogDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.merging.MergeAuditLog;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Cron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Cron.class, LoggerMaker.LogDb.CYBORG);
    private static final int PRIORITY_ACCOUNT_ID = 1736798101;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public void cron(boolean isHybridSaas) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (isHybridSaas) {
                    // Run optimized account first to avoid being blocked by other accounts
                    loggerMaker.warnAndAddToDb("Priority merging: starting for account " + PRIORITY_ACCOUNT_ID, LoggerMaker.LogDb.CYBORG);
                    Context.accountId.set(PRIORITY_ACCOUNT_ID);
                    try {
                        triggerMerging(PRIORITY_ACCOUNT_ID);
                        loggerMaker.warnAndAddToDb("Priority merging: completed for account " + PRIORITY_ACCOUNT_ID, LoggerMaker.LogDb.CYBORG);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error in priority merging for account " + PRIORITY_ACCOUNT_ID + ": " + e.getMessage(), LoggerMaker.LogDb.CYBORG);
                    }
                    AccountTask.instance.executeTaskHybridAccounts(new Consumer<Account>() {
                        @Override
                        public void accept(Account t) {
                            triggerMerging(t.getId());
                        }
                    }, "mergingCron");
                } else {
                    loggerMaker.warnAndAddToDb("Priority merging else block: starting for account " + PRIORITY_ACCOUNT_ID, LoggerMaker.LogDb.CYBORG);
                    Context.accountId.set(PRIORITY_ACCOUNT_ID);
                    try {
                        triggerMerging(PRIORITY_ACCOUNT_ID);
                        loggerMaker.warnAndAddToDb("Priority merging: completed for account " + PRIORITY_ACCOUNT_ID, LoggerMaker.LogDb.CYBORG);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error in priority merging for account " + PRIORITY_ACCOUNT_ID + ": " + e.getMessage(), LoggerMaker.LogDb.CYBORG);
                    }
                    AccountTask.instance.executeTask(new Consumer<Account>() {
                        @Override
                        public void accept(Account t) {
                            triggerMerging(t.getId());
                        }
                    }, "mergingCron");
                }                
            }
        }, 0, 10, TimeUnit.MINUTES);

        String enableDeletion = System.getenv("ENABLE_MERGE_AUDIT_DELETION");
        if ("true".equalsIgnoreCase(enableDeletion)) {
            loggerMaker.warnAndAddToDb("Merge audit deletion cron enabled", LoggerMaker.LogDb.CYBORG);
            scheduler.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    Context.accountId.set(PRIORITY_ACCOUNT_ID);
                    MergeAuditDeletion.runDeletion(PRIORITY_ACCOUNT_ID);
                }
            }, 5, 30, TimeUnit.MINUTES);
        }

    }

    public void triggerMerging(int accountId) {
        if (!Lock.acquireLock(accountId)) {
            loggerMaker.infoAndAddToDb("Unable to acquire lock, merging process ignored for account " + accountId);
            return;
        }
        loggerMaker.infoAndAddToDb("Acquired lock, starting merging process for account " + accountId);
        List<Integer> apiCollectionIds = DbLayer.fetchApiCollectionIds();
        AccountSettings accountSettings = DbLayer.fetchAccountSettings(accountId);
        Boolean doBodyMatch = accountSettings != null && accountSettings.getBodyMatchEnabled();
        try {
            for (int apiCollectionId : apiCollectionIds) {
                int start = Context.now();
                loggerMaker.infoAndAddToDb("Started merging API collection " + apiCollectionId +
                        " accountId " + accountId);

                try {
                    MergingLogic.mergeUrlsAndSave(apiCollectionId, !doBodyMatch, accountSettings.isAllowMergingOnVersions());
                    loggerMaker.infoAndAddToDb("Finished merging API collection " +
                            apiCollectionId + " accountId " + accountId + " in " + (Context.now() - start)
                            + " seconds");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error merging Api collection" + apiCollectionId +
                            " accountId " + accountId + e.getMessage(), LoggerMaker.LogDb.CYBORG);
                }  
            }
            DependencyFlow dependencyFlow = new DependencyFlow();
            dependencyFlow.run(null);
            dependencyFlow.syncWithDb();
        } catch (Exception e) {
            String err = e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : e.getMessage();
            loggerMaker.errorAndAddToDb("error in mergeUrlsAndSave: " + " accountId " + accountId
                    + err, LoggerMaker.LogDb.CYBORG);
            e.printStackTrace();
        }
        Lock.releaseLock(accountId);
    }

    /**
     * Reads merge_audit_logs and prints verification stats:
     * 1. Total unique static URLs that would be deleted
     * 2. Deletion count per collection
     * 3. Current unique endpoints (ApiInfo) vs post-merge count
     * 4. Total STIs that would be deleted
     * 5. Any static URL present in multiple audit docs (should be mergeable into only 1 template)
     */
    public static void verifyAuditLogs() {
        System.out.println("=== Merge Audit Verification ===\n");

        // Load all audit logs
        MongoCursor<MergeAuditLog> cursor = MergeAuditLogDao.instance.getMCollection().find().cursor();
        List<MergeAuditLog> allLogs = new ArrayList<>();
        while (cursor.hasNext()) {
            allLogs.add(cursor.next());
        }
        cursor.close();
        System.out.println("Total audit log docs: " + allLogs.size());

        // Track per-collection deletions and detect duplicates
        Map<Integer, Set<String>> perCollectionDeletes = new HashMap<>();
        // staticUrl → list of templateUrls it matched (for duplicate detection)
        Map<String, List<String>> staticUrlToTemplates = new HashMap<>();

        for (MergeAuditLog log : allLogs) {
            int colId = log.getApiCollectionId();
            Set<String> deletes = perCollectionDeletes.computeIfAbsent(colId, k -> new HashSet<>());
            String templateInfo = log.getMergeType() + " | " + log.getMethod() + " " + log.getTemplateUrl();

            for (String staticUrl : log.getMatchedStaticUrls()) {
                deletes.add(staticUrl);
                staticUrlToTemplates.computeIfAbsent(staticUrl, k -> new ArrayList<>()).add(templateInfo);
            }
        }

        // 1. Total unique URLs to delete
        int totalDeletes = 0;
        for (Set<String> s : perCollectionDeletes.values()) totalDeletes += s.size();
        System.out.println("\n--- 1. Total unique static URLs to delete: " + totalDeletes);

        // 2. Per-collection deletion count
        System.out.println("\n--- 2. Deletion count per collection:");
        for (Map.Entry<Integer, Set<String>> entry : perCollectionDeletes.entrySet()) {
            System.out.println("  Collection " + entry.getKey() + ": " + entry.getValue().size() + " URLs");
        }

        // 3. Current ApiInfo count vs post-merge
        System.out.println("\n--- 3. ApiInfo count (current vs post-merge):");
        for (int colId : perCollectionDeletes.keySet()) {
            long currentCount = ApiInfoDao.instance.count(
                    Filters.eq("_id.apiCollectionId", colId));
            long postMerge = currentCount - perCollectionDeletes.get(colId).size();
            System.out.println("  Collection " + colId + ": " + currentCount + " → " + postMerge
                    + " (deleting " + perCollectionDeletes.get(colId).size() + " endpoints)");
        }

        // 4. Total STIs that would be deleted
        System.out.println("\n--- 4. STI deletion count per collection:");
        long totalStiDeletes = 0;
        for (Map.Entry<Integer, Set<String>> entry : perCollectionDeletes.entrySet()) {
            int colId = entry.getKey();
            long stiCount = 0;
            for (String staticUrl : entry.getValue()) {
                // staticUrl is "METHOD /path" format
                String[] parts = staticUrl.split(" ", 2);
                if (parts.length < 2) continue;
                stiCount += SingleTypeInfoDao.instance.count(
                        Filters.and(
                                Filters.eq("apiCollectionId", colId),
                                Filters.eq("method", parts[0]),
                                Filters.eq("url", parts[1])
                        ));
            }
            totalStiDeletes += stiCount;
            System.out.println("  Collection " + colId + ": " + stiCount + " STIs");
        }
        System.out.println("  Total STIs to delete: " + totalStiDeletes);

        // 5. Static URLs present in multiple audit docs
        System.out.println("\n--- 5. Static URLs matched by multiple templates:");
        int dupeCount = 0;
        for (Map.Entry<String, List<String>> entry : staticUrlToTemplates.entrySet()) {
            if (entry.getValue().size() > 1) {
                dupeCount++;
                System.out.println("  " + entry.getKey());
                for (String tmpl : entry.getValue()) {
                    System.out.println("    → " + tmpl);
                }
            }
        }
        if (dupeCount == 0) {
            System.out.println("  None — every static URL maps to exactly 1 template.");
        } else {
            System.out.println("  WARNING: " + dupeCount + " static URLs matched multiple templates!");
        }

        System.out.println("\n=== Verification Complete ===");
    }

}
