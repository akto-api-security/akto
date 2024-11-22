package com.akto.threat.protection.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.dto.Account;
import com.akto.threat.protection.db.CleanupAuditModel;
import com.akto.threat.protection.db.MaliciousEventModel;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class CleanupTask {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    private final MongoClient mongoClient;

    public CleanupTask(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public void init() {
        this.executor.scheduleAtFixedRate(this::cleanUp, 0, 5, TimeUnit.HOURS);
    }

    public void cleanUp() {
        AccountTask.instance.executeTask(
                new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        cleanUpForAccount(account.getId());
                    }
                },
                "cleanup-malicious-requests");
    }

    public Optional<CleanupAuditModel> getLatestEntry(String accountId) {
        return Optional.ofNullable(this.mongoClient.getDatabase(accountId + "")
                .getCollection("cleanup_malicious_requests_audit", CleanupAuditModel.class)
                .find(
                        new BasicDBObject("sort", new BasicDBObject("alertWindowEnd", -1)))
                .first());
    }

    public void cleanUpForAccount(int accountId) {
        // Remove all the requests that have passed their expiry.
        // AND those requests whose actor and filter that don't have any alerts
        // associated with
        // them.
        long now = System.currentTimeMillis() / 1000L;

        // Get the latest cleanup audit if exists
        Optional<CleanupAuditModel> audit = this.getLatestEntry(accountId + "");
        long start = audit.map(CleanupAuditModel::getAlertWindowEnd).orElse(0L);

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(
                        Filters.and(
                                Filters.gte("detectedAt", start),
                                Filters.lt("detectedAt", now))),
                Aggregates.group(
                        0,
                        Accumulators.addToSet(
                                "validFilters",
                                new BasicDBObject("filterId", "$filterId")
                                        .append("actor", "$actor"))),
                Aggregates.project(
                        Projections.fields(
                                Projections.include("validFilters"),
                                Projections.excludeId())));

        try (MongoCursor<BasicDBObject> result = this.mongoClient.getDatabase(accountId + "")
                .getCollection("smart_events")
                .aggregate(pipeline, BasicDBObject.class)
                .cursor()) {

            BasicDBObject validFilters = result.tryNext();
            if (validFilters == null) {
                return;
            }

            BasicDBList filters = (BasicDBList) validFilters.get("validFilters");
            List<BasicDBObject> filterList = new ArrayList<>();
            for (Object filter : filters) {
                BasicDBObject filterObj = (BasicDBObject) filter;
                filterList.add(
                        new BasicDBObject("filterId", filterObj.getString("filterId"))
                                .append("actor", filterObj.getString("actor")));
            }

            // Remove all the requests that have passed their expiry.
            this.mongoClient.getDatabase(accountId + "")
                    .getCollection("malicious_events", MaliciousEventModel.class)
                    .deleteMany(
                            Filters.and(
                                    Filters.lt("expiry", now),
                                    Filters.nor(
                                            filterList.stream()
                                                    .map(Filters::and)
                                                    .toArray(Bson[]::new))));

            // TODO: For any given filter, only keep last 1000 requests

            this.mongoClient.getDatabase(accountId + "")
                    .getCollection("cleanup_malicious_requests_audit", CleanupAuditModel.class)
                    .insertOne(new CleanupAuditModel(start, now));
        }
    }
}