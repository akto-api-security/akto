package com.akto.threat.backend.cron;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.dao.ActorInfoDao;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat_utils.AwsWafUtils;
import com.akto.threat_utils.CloudflareWafUtils;
import com.akto.util.AccountTask;
import com.akto.util.ThreatDetectionConstants;
import com.akto.util.enums.GlobalEnums;
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
        Config.CloudflareWafConfig cloudflareConfig = fetchCloudflareConfig(accountId);
        Config.AwsWafConfig awsWafConfig = fetchAwsWafConfig(accountId);

        if (cloudflareConfig == null && awsWafConfig == null) {
            loggerMaker.debugAndAddToDb("No WAF config found for account " + accountId);
            return;
        }

        int sevenDaysAgo = Context.now() - SEVEN_DAYS_SECONDS;

        // Compute both lists before marking any actor BLOCKED, so an account with both WAFs blocks in both
        List<String> cloudflareActors = cloudflareConfig == null ? Collections.emptyList()
            : actorsToBlock(accountId, cloudflareConfig.getThreatPolicies(), sevenDaysAgo);
        List<String> awsActors = awsWafConfig == null ? Collections.emptyList()
            : actorsToBlock(accountId, awsWafConfig.getThreatPolicies(), sevenDaysAgo);

        Set<String> blockedActors = new HashSet<>();
        if (!cloudflareActors.isEmpty() && CloudflareWafUtils.blockActorIps(cloudflareConfig, cloudflareActors)) {
            blockedActors.addAll(cloudflareActors);
        }
        if (!awsActors.isEmpty() && AwsWafUtils.blockActorIps(awsWafConfig, awsActors)) {
            blockedActors.addAll(awsActors);
        }

        if (!blockedActors.isEmpty()) {
            ActorInfoDao.instance.bulkUpdateActorStatus(String.valueOf(accountId), new ArrayList<>(blockedActors), ThreatDetectionConstants.BLOCKED, Context.now());
        }

        loggerMaker.infoAndAddToDb("Completed WAF sync for account " + accountId + ", blocked " + blockedActors.size() + " actors");
    }

    private List<String> actorsToBlock(int accountId, List<String> threatPolicies, int startTime) {
        if (threatPolicies == null || threatPolicies.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(fetchUniqueThreatActors(accountId, threatPolicies, startTime));
    }

    private Config.CloudflareWafConfig fetchCloudflareConfig(int accountId) {
        Bson filter = Filters.and(
            Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accountId),
            Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        return (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filter);
    }

    private Config.AwsWafConfig fetchAwsWafConfig(int accountId) {
        Bson filter = Filters.and(
            Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accountId),
            Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.AWS_WAF.name())
        );
        return (Config.AwsWafConfig) ConfigsDao.instance.findOne(filter);
    }

    private Set<String> fetchUniqueThreatActors(int accountId, List<String> policyIds, int startTime) {
        Set<String> uniqueActorIds = new HashSet<>();

        // Step 1: Query MaliciousEventsDao for unique actors matching threat policies (API context only)
        int currentTime = Context.now();
        List<Bson> pipeline = Arrays.asList(
            new Document("$match", new Document()
                .append("contextSource", GlobalEnums.CONTEXT_SOURCE.API.name())
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

        loggerMaker.infoAndAddToDb("Found " + uniqueActorIds.size() + " unique actors from MaliciousEventsDao for account " + accountId);

        // Step 2: Single query to get all already-blocked actors (API context only)
        Set<String> blockedActorIds = new HashSet<>();
        ActorInfoDao.instance
            .getCollection(String.valueOf(accountId))
            .find(Filters.and(
                Filters.eq("contextSource", GlobalEnums.CONTEXT_SOURCE.API.name()),
                Filters.in("actorId", new ArrayList<>(uniqueActorIds)),
                Filters.eq("status", ThreatDetectionConstants.BLOCKED)
            ))
            .forEach(actor -> {
                String actorId = actor.getActorId();
                if (actorId != null) {
                    blockedActorIds.add(actorId);
                }
            });

        // Step 3: Return only non-blocked actors
        Set<String> actorsToBlock = new HashSet<>(uniqueActorIds);
        actorsToBlock.removeAll(blockedActorIds);

        loggerMaker.infoAndAddToDb("After filtering blocked actors: " + actorsToBlock.size() + " actors to block for account " + accountId);
        return actorsToBlock;
    }
}
