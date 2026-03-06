package com.akto.threat.backend.cron;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.dao.ActorInfoDao;
import com.akto.threat.backend.db.ActorInfoModel;
import com.akto.threat_utils.CloudflareWafUtils;
import com.akto.util.AccountTask;
import com.mongodb.client.model.Filters;
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

        // 3. Fetch unique threat actors
        Set<String> uniqueActorIds = fetchUniqueThreatActors(accountId, threatPolicies, sevenDaysAgo);

        loggerMaker.infoAndAddToDb("Found " + uniqueActorIds.size() + " unique threat actors for account " + accountId);

        // 4. Call bulkModifyActorCloudflare
        if (!uniqueActorIds.isEmpty()) {
            bulkModifyActorCloudflare(config, new ArrayList<>(uniqueActorIds));
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

        // Query ActorInfo collection: filterId IN policyIds AND lastAttackTs >= startTime
        Bson filter = Filters.and(
            Filters.in("filterId", policyIds),
            Filters.gte("lastAttackTs", (long) startTime)
        );

        List<ActorInfoModel> actors = ActorInfoDao.instance
            .getCollection(String.valueOf(accountId))
            .find(filter)
            .into(new ArrayList<>());

        for (ActorInfoModel actor : actors) {
            if (actor.getActorId() != null) {
                uniqueActorIds.add(actor.getActorId());
            }
        }

        return uniqueActorIds;
    }

    /**
     * Blocks actors by adding them to Cloudflare WAF lists
     */
    private void bulkModifyActorCloudflare(Config.CloudflareWafConfig config, List<String> actorIds) {
        try {
            boolean success = CloudflareWafUtils.blockActorIps(config, actorIds);
            if (success) {
                loggerMaker.infoAndAddToDb("Successfully blocked " + actorIds.size() + " actors in Cloudflare WAF");
            } else {
                loggerMaker.errorAndAddToDb("Failed to block " + actorIds.size() + " actors in Cloudflare WAF");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error blocking actors in Cloudflare WAF: " + e.getMessage());
        }
    }
}
