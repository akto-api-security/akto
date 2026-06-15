package com.akto.threat.backend.cron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class ConfigRiskSyncCron {

    private static final int INITIAL_DELAY_SECONDS = 60;
    private static final int PERIOD_MINUTES = 15;
    private static final String MISCONFIGURED_TAG_KEY = "misconfigured-config";
    private static final String CONFIG_ENDPOINT_PREFIX = "^/claude/config/";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(ConfigRiskSyncCron.class, LogDb.THREAT_DETECTION);

    public void setUp() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            syncConfigRiskForAccount(t.getId());
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Unhandled error in config risk sync cron: " + e.getMessage());
                        }
                    }
                }, "config-risk-sync-cron");
            }
        }, INITIAL_DELAY_SECONDS, PERIOD_MINUTES * 60, TimeUnit.SECONDS);
    }

    private void syncConfigRiskForAccount(int accountId) {
        int startTimestamp = Context.now();
        loggerMaker.infoAndAddToDb("Config risk sync started for account " + accountId);

        Set<Integer> misconfiguredCollectionIds = findMisconfiguredCollectionIds(accountId);
        loggerMaker.infoAndAddToDb("Config misconfigured collection count: " + misconfiguredCollectionIds.size() + " for account " + accountId);

        if (!misconfiguredCollectionIds.isEmpty()) {
            stampMisconfiguredTag(misconfiguredCollectionIds);
        }

        loggerMaker.infoAndAddToDb("Config risk sync completed for account " + accountId
                + " in " + (Context.now() - startTimestamp) + "s");
    }

    private Set<Integer> findMisconfiguredCollectionIds(int accountId) {
        BasicDBObject groupedId = new BasicDBObject("host", "$host")
                .append("endpoint", "$latestApiEndpoint");

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("successfulExploit", true),
                Filters.eq("contextSource", "ENDPOINT"),
                Filters.regex("latestApiEndpoint", CONFIG_ENDPOINT_PREFIX)
        )));
        pipeline.add(Aggregates.group(groupedId));

        Map<String, Integer> hostToCollectionId = buildHostToCollectionIdMap();
        if (hostToCollectionId.isEmpty()) {
            loggerMaker.infoAndAddToDb("No host collections found for account " + accountId + ", skipping");
            return new HashSet<>();
        }

        Set<Integer> misconfiguredCollectionIds = new HashSet<>();
        try (MongoCursor<BasicDBObject> cursor = MaliciousEventDao.instance
                .getCollection(String.valueOf(accountId))
                .aggregate(pipeline, BasicDBObject.class)
                .cursor()) {

            while (cursor.hasNext()) {
                BasicDBObject document = cursor.next();
                BasicDBObject id = (BasicDBObject) document.get("_id");
                if (id == null) continue;

                String host = id.getString("host");
                if (host == null || host.isEmpty()) continue;

                Integer collectionId = hostToCollectionId.get(host);
                if (collectionId == null) {
                    loggerMaker.infoAndAddToDb("No collection mapped for host: " + host);
                    continue;
                }

                misconfiguredCollectionIds.add(collectionId);
            }
        }

        return misconfiguredCollectionIds;
    }

    private Map<String, Integer> buildHostToCollectionIdMap() {
        List<ApiCollection> allCollections = ApiCollectionsDao.fetchAllHosts();
        Map<String, Integer> hostToCollectionId = new HashMap<>();
        for (ApiCollection col : allCollections) {
            if (col.getHostName() != null && !col.getHostName().isEmpty()) {
                hostToCollectionId.put(col.getHostName(), col.getId());
            }
        }
        return hostToCollectionId;
    }

    private void stampMisconfiguredTag(Set<Integer> collectionIds) {
        CollectionTags misconfiguredTag = new CollectionTags(
                Context.now(), MISCONFIGURED_TAG_KEY, "true", CollectionTags.TagSource.AKTO);

        List<WriteModel<ApiCollection>> collectionUpdates = new ArrayList<>();
        for (Integer collId : collectionIds) {
            Bson collFilter = Filters.eq(ApiCollection.ID, collId);
            // Pull any existing entry first to avoid duplicates, then push the fresh one
            collectionUpdates.add(new UpdateManyModel<>(collFilter,
                    Updates.pull(ApiCollection.TAGS_STRING,
                            new BasicDBObject("keyName", MISCONFIGURED_TAG_KEY))
            ));
            collectionUpdates.add(new UpdateManyModel<>(collFilter,
                    Updates.push(ApiCollection.TAGS_STRING, misconfiguredTag)
            ));
        }

        loggerMaker.infoAndAddToDb("Stamping misconfigured tag on " + collectionIds.size() + " collections");
        ApiCollectionsDao.instance.bulkWrite(collectionUpdates, new BulkWriteOptions().ordered(true));
    }
}
