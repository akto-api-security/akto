package com.akto.suspect_data;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.threat_detection.CleanupAuditDao;
import com.akto.dao.threat_detection.DetectedThreatAlertDao;
import com.akto.dao.threat_detection.SampleMaliciousRequestDao;
import com.akto.dto.Account;
import com.akto.dto.threat_detection.CleanupAudit;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

public class CleanUpTask {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    private CleanUpTask() {}

    public static CleanUpTask instance = new CleanUpTask();

    public void init() {
        this.executor.scheduleAtFixedRate(this::cleanUp, 0, 5, TimeUnit.HOURS);
    }

    public void cleanUp() {
        AccountTask.instance.executeTask(
                new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        cleanUpForAccount();
                    }
                },
                "cleanup-malicious-requests");
    }

    public void cleanUpForAccount() {
        // Remove all the requests that have passed their expiry.
        // AND those requests whose actor and filter that don't have any alerts associated with
        // them.
        long now = System.currentTimeMillis() / 1000L;

        // Get the latest cleanup audit if exists
        Optional<CleanupAudit> audit = CleanupAuditDao.instance.getLatestEntry();
        long start = audit.map(CleanupAudit::getAlertWindowEnd).orElse(0L);

        List<Bson> pipeline =
                Arrays.asList(
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

        try (MongoCursor<BasicDBObject> result =
                DetectedThreatAlertDao.instance
                        .getMCollection()
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
            SampleMaliciousRequestDao.instance
                    .getMCollection()
                    .deleteMany(
                            Filters.and(
                                    Filters.lt("expiry", now),
                                    Filters.nor(
                                            filterList.stream()
                                                    .map(Filters::and)
                                                    .toArray(Bson[]::new))));

            // TODO: For any given filter, only keep last 1000 requests

            CleanupAuditDao.instance.insertOne(new CleanupAudit(start, now));
        }
    }
}
