package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightDataLoader;
import com.akto.service.posture.ArgusPostureService;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArgusPostureAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusPostureAction.class, LogDb.DASHBOARD);

    private final ArgusPostureService argusPostureService = new ArgusPostureService();
    private final InsightDataLoader insightDataLoader = new InsightDataLoader();

    @Getter @Setter private int startTimestamp;
    @Getter @Setter private int endTimestamp;
    @Getter @Setter private String environment;

    @Getter private BasicDBObject response = new BasicDBObject();

    public String fetchArgusPostureSummary() {
        try {
            Map<String, Integer> environmentCounts = countAssetsByEnvironment();

            List<ApiCollection> scoped = ApiCollectionsDao.instance.findAll(
                    Filters.and(
                            Filters.ne(ApiCollection._DEACTIVATED, true),
                            ArgusPostureService.filterForEnvironment(environment)),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME,
                            ApiCollection.NAME, ApiCollection.TAGS_STRING));

            List<GuardrailPolicies> policies = insightDataLoader.loadPolicies();
            Map<Integer, List<String>> sensitiveByCollection =
                    insightDataLoader.loadSensitiveByCollection(scoped);

            this.response = argusPostureService.buildSummary(scoped, policies, sensitiveByCollection, environmentCounts);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture summary: " + e.getMessage());
            addActionError("Failed to build Argus posture summary");
            return ERROR.toUpperCase();
        }
    }

    private Map<String, Integer> countAssetsByEnvironment() {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.ne(ApiCollection._DEACTIVATED, true)),
                Aggregates.project(Projections.computed("envTag",
                        new BasicDBObject("$filter", new BasicDBObject("input", "$" + ApiCollection.TAGS_STRING)
                                .append("cond", new BasicDBObject("$eq", Arrays.asList(
                                        "$$this." + CollectionTags.KEY_NAME, Constants.AKTO_ENV_TYPE_TAG)))))),
                Aggregates.group(new BasicDBObject("$arrayElemAt", Arrays.asList("$envTag.value", 0)),
                        Accumulators.sum(MCollection._COUNT, 1)));

        Map<String, Integer> counts = new LinkedHashMap<>();
        try (MongoCursor<BasicDBObject> cursor =
                     ApiCollectionsDao.instance.aggregateWithRbac(pipeline).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject row = cursor.next();
                String bucket = ArgusPostureService.envBucket(row.getString(Constants.ID));
                counts.put(bucket, counts.getOrDefault(bucket, 0) + row.getInt(MCollection._COUNT, 0));
            }
        }
        return counts;
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
