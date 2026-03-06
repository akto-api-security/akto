package com.akto.threat.backend.cron;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.dao.ActorInfoDao;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.db.ActorInfoModel;
import com.akto.threat_utils.CloudflareWafUtils;
import com.akto.util.AccountTask;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CloudflareWafSyncCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CloudflareWafSyncCron.class, LogDb.THREAT_DETECTION);
    private static final int SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60;

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpCloudflareWafSyncCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        int accountId = account.getId();
                        try {
                            loggerMaker.infoAndAddToDb("Starting Cloudflare WAF sync for account " + accountId);
                            syncCloudflareWafForAccount(accountId);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb("Error in Cloudflare WAF sync for account " + accountId + ": " + e.getMessage());
                        }
                    }
                }, "cloudflare-waf-sync-cron");
            }
        }, 0, 2, TimeUnit.DAYS);

        loggerMaker.infoAndAddToDb("Cloudflare WAF Sync Cron scheduled to run every 2 days");
    }

    private void syncCloudflareWafForAccount(int accountId) {
        // 1. Fetch CloudflareWafConfig
        Config.CloudflareWafConfig config = fetchCloudflareConfig(accountId);

        if (config == null) {
            loggerMaker.debugAndAddToDb("No Cloudflare WAF config found for account " + accountId);
            return;
        }

        List<String> threatPolicies = config.getThreatPolicies();
        if (threatPolicies == null || threatPolicies.isEmpty()) {
            loggerMaker.debugAndAddToDb("No threat policies configured for account " + accountId);
            return;
        }

        // 2. Calculate 7-day time window
        int currentTime = Context.now();
        int sevenDaysAgo = currentTime - SEVEN_DAYS_SECONDS;

        // 3. Fetch unique threat actors (excluding already blocked)
        Set<String> actorsToBlock = fetchUniqueThreatActors(accountId, threatPolicies, sevenDaysAgo);

        loggerMaker.infoAndAddToDb("Found " + actorsToBlock.size() + " actors to block for account " + accountId);

        // 4. Block actors and mark status as BLOCKED
        if (!actorsToBlock.isEmpty()) {
            bulkModifyActorCloudflare(accountId, config, new ArrayList<>(actorsToBlock));
        }

        loggerMaker.infoAndAddToDb("Completed Cloudflare WAF sync for account " + accountId);
    }

    private Config.CloudflareWafConfig fetchCloudflareConfig(int accountId) {
        Bson filter = Filters.and(
            Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accountId),
            Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        return (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filter);
    }

    private Set<String> fetchUniqueThreatActors(int accountId, List<String> policyIds, int startTime) {
        Set<String> uniqueActorIds = new HashSet<>();

        // Step 1: Query MaliciousEventsDao for unique actors matching threat policies
        int currentTime = Context.now();
        List<Bson> pipeline = Arrays.asList(
            new Document("$match", new Document()
                .append("filterId", new Document("$in", policyIds))
                .append("detectedAt", new Document("$gte", startTime).append("$lte", currentTime))
            ),
            new Document("$group", new Document("_id", "$actor")),
            new Document("$match", new Document("_id", new Document("$ne", null)))
        );

        MaliciousEventDao.instance
            .getCollection(String.valueOf(accountId))
            .aggregate(pipeline, Document.class)
            .forEach(doc -> {
                String actor = doc.getString("_id");
                if (actor != null) {
                    uniqueActorIds.add(actor);
                }
            });

        loggerMaker.debugAndAddToDb("Found " + uniqueActorIds.size() + " unique actors from MaliciousEventsDao for account " + accountId);

        // Step 2: Filter out already blocked actors from ActorInfoDao
        Set<String> actorsToBlock = new HashSet<>();
        for (String actorId : uniqueActorIds) {
            ActorInfoModel actor = ActorInfoDao.instance
                .getCollection(String.valueOf(accountId))
                .find(Filters.eq("actorId", actorId))
                .first();

            // Block only if actor is not already blocked
            if (actor == null || !isBlocked(actor)) {
                actorsToBlock.add(actorId);
            }
        }

        loggerMaker.debugAndAddToDb("After filtering blocked actors: " + actorsToBlock.size() + " actors to block for account " + accountId);
        return actorsToBlock;
    }

    private boolean isBlocked(ActorInfoModel actor) {
        return actor.getStatus() != null && actor.getStatus().equals("BLOCKED");
    }

    /**
     * Blocks actors by adding them to Cloudflare WAF lists and marks them as BLOCKED in ActorInfoDao
     */
    private void bulkModifyActorCloudflare(int accountId, Config.CloudflareWafConfig config, List<String> actorIds) {
        try {
            boolean success = CloudflareWafUtils.blockActorIps(config, actorIds);
            if (success) {
                loggerMaker.infoAndAddToDb("Successfully blocked " + actorIds.size() + " actors in Cloudflare WAF");

                // Mark actors as BLOCKED in ActorInfoDao to prevent re-blocking on next run
                ActorInfoDao.instance.bulkUpdateActorStatus(String.valueOf(accountId), actorIds, "BLOCKED", Context.now());
                loggerMaker.debugAndAddToDb("Marked " + actorIds.size() + " actors as BLOCKED for account " + accountId);
            } else {
                loggerMaker.errorAndAddToDb("Failed to block " + actorIds.size() + " actors in Cloudflare WAF");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error blocking actors in Cloudflare WAF: " + e.getMessage());
        }
    }
}
