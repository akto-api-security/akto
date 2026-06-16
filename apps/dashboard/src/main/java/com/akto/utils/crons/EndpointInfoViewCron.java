package com.akto.utils.crons;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AccountTask;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EndpointInfoViewCron {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final LoggerMaker logger = new LoggerMaker(EndpointInfoViewCron.class, LoggerMaker.LogDb.DASHBOARD);
    private static final int ACTIVE_THRESHOLD = 30 * 60;
    private static final int INACTIVE_THRESHOLD = 24 * 60 * 60;
    private static final int ACTIVITY_LOOKBACK = 3 * 24 * 60 * 60;
    private static final int STI_FULL_REBUILD_INTERVAL = 24 * 60 * 60;

    public void setUpEndpointInfoViewCronScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            AccountTask.instance.executeTask(new Consumer<Account>() {
                @Override
                public void accept(Account t) {
                    try {
                        refreshView();
                    } catch (Exception e) {
                        logger.errorAndAddToDb(e, "Error in EndpointInfoViewCron: " + e.getMessage());
                    }
                }
            }, "endpoint-stat-view-cron");
        }, 0, 30, TimeUnit.MINUTES);
    }

    public static void refreshView() {
        int threshold = isAccountActiveRecently() ? ACTIVE_THRESHOLD : INACTIVE_THRESHOLD;
        if (!tryAcquire(threshold)) return;

        try {
            doRefresh();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing view for account " + Context.accountId.get());
        }
    }

    private static void doRefresh() {
        long startMs = System.currentTimeMillis();
        int accountId = Context.accountId.get();

        long ts = System.currentTimeMillis();
        Set<Integer> excludedIds = buildExcludedIds();
        logger.warnAndAddToDb("EndpointInfoView buildExcludedIds took "
                + (System.currentTimeMillis() - ts) + "ms, account=" + accountId);

        ts = System.currentTimeMillis();
        List<Integer> outOfScopeIds = buildOutOfScopeIds(excludedIds);
        logger.warnAndAddToDb("EndpointInfoView buildOutOfScopeIds took "
                + (System.currentTimeMillis() - ts) + "ms, account=" + accountId);

        ts = System.currentTimeMillis();
        List<String> sensitiveTypes = buildSensitiveTypesList();
        logger.warnAndAddToDb("EndpointInfoView buildSensitiveTypesList took "
                + (System.currentTimeMillis() - ts) + "ms, account=" + accountId);

        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, accountId));
        int stiLastFullRebuildTs = account != null ? account.getStiLastFullRebuildTs() : 0;
        int stiLastIncrementalTs = account != null ? account.getStiLastIncrementalTs() : 0;

        boolean needsFullRebuild = stiLastFullRebuildTs == 0
                || (Context.now() - stiLastFullRebuildTs > STI_FULL_REBUILD_INTERVAL);

        if (needsFullRebuild) {
            long stiStart = System.currentTimeMillis();
            int maxTs = buildStiSummaryFull(excludedIds, sensitiveTypes);
            stiLastIncrementalTs = maxTs;
            logger.warnAndAddToDb("EndpointInfoView STI full rebuild took "
                    + (System.currentTimeMillis() - stiStart) + "ms, account=" + accountId);
        } else {
            long stiStart = System.currentTimeMillis();
            int maxTs = stiIncrementalSync(stiLastIncrementalTs, excludedIds, sensitiveTypes);
            if (maxTs > stiLastIncrementalTs) {
                stiLastIncrementalTs = maxTs;
            }
            logger.warnAndAddToDb("EndpointInfoView STI incremental took "
                    + (System.currentTimeMillis() - stiStart) + "ms, account=" + accountId);
        }

        // View build: single pipeline ApiInfo -> $lookup sti_unique_endpoints -> $out temp -> rename
        long viewStart = System.currentTimeMillis();
        int viewCount = buildView(excludedIds, outOfScopeIds);
        long viewTime = System.currentTimeMillis() - viewStart;
        logger.warnAndAddToDb("EndpointInfoView buildView took "
                + viewTime + "ms, count=" + viewCount + ", account=" + accountId);

        // Persist STI state
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(Account.STI_LAST_INCREMENTAL_TS, stiLastIncrementalTs));
        if (needsFullRebuild) {
            updates.add(Updates.set(Account.STI_LAST_FULL_REBUILD_TS, Context.now()));
        }
        AccountsDao.instance.updateOne(Filters.eq(Constants.ID, accountId), Updates.combine(updates));

        long totalTime = System.currentTimeMillis() - startMs;
        logger.warnAndAddToDb("EndpointInfoView refreshed: " + viewCount + " endpoints, account=" + accountId
                + ", total=" + totalTime + "ms (view=" + viewTime + "ms)");
    }

    /**
     * Full STI summary rebuild. Groups all STI docs by endpoint into sti_unique_endpoints
     * with string _id = "method|apiCollectionId|url". Returns max timestamp seen.
     */
    private static int buildStiSummaryFull(Set<Integer> excludedIds, List<String> sensitiveTypes) {
        Document concatId = new Document("$concat", Arrays.asList(
                "$" + SingleTypeInfo._METHOD, "|",
                new Document("$toString", "$" + SingleTypeInfo._API_COLLECTION_ID), "|",
                "$" + SingleTypeInfo._URL));

        Document sensitiveCondition = new Document("$cond", Arrays.asList(
                new Document("$in", Arrays.asList("$" + SingleTypeInfo.SUB_TYPE, sensitiveTypes)),
                "$" + SingleTypeInfo.SUB_TYPE,
                "$$REMOVE"));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludedIds)),
                Aggregates.group(concatId,
                        Accumulators.addToSet("sensitiveSubTypes", sensitiveCondition),
                        Accumulators.sum("paramCount", 1),
                        Accumulators.max("maxTs", "$timestamp"),
                        Accumulators.first("method", "$" + SingleTypeInfo._METHOD),
                        Accumulators.first("apiCollectionId", "$" + SingleTypeInfo._API_COLLECTION_ID),
                        Accumulators.first("url", "$" + SingleTypeInfo._URL)),
                Aggregates.out(StiUniqueEndpointsDao.instance.getCollName())
        );

        SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).allowDiskUse(true).first();

        Document maxDoc = StiUniqueEndpointsDao.instance.getMCollection()
                .find().sort(new Document("maxTs", -1)).limit(1)
                .projection(Projections.include("maxTs")).first();
        return maxDoc != null ? ((Number) maxDoc.get("maxTs")).intValue() : 0;
    }

    /**
     * Incremental STI sync. Processes only STIs newer than lastTs.
     * Uses $merge with pipeline to union sensitiveSubTypes correctly.
     */
    private static int stiIncrementalSync(int lastTs, Set<Integer> excludedIds, List<String> sensitiveTypes) {
        Document concatId = new Document("$concat", Arrays.asList(
                "$" + SingleTypeInfo._METHOD, "|",
                new Document("$toString", "$" + SingleTypeInfo._API_COLLECTION_ID), "|",
                "$" + SingleTypeInfo._URL));

        Document sensitiveCondition = new Document("$cond", Arrays.asList(
                new Document("$in", Arrays.asList("$" + SingleTypeInfo.SUB_TYPE, sensitiveTypes)),
                "$" + SingleTypeInfo.SUB_TYPE,
                "$$REMOVE"));

        // $merge with pipeline: union sensitiveSubTypes, take max timestamp
        Document mergeStage = new Document("$merge", new Document()
                .append("into", StiUniqueEndpointsDao.instance.getCollName())
                .append("on", "_id")
                .append("whenMatched", Arrays.asList(
                        new Document("$set", new Document()
                                .append("sensitiveSubTypes", new Document("$setUnion", Arrays.asList(
                                        new Document("$ifNull", Arrays.asList("$sensitiveSubTypes", Collections.emptyList())),
                                        "$$new.sensitiveSubTypes")))
                                .append("maxTs", new Document("$max", Arrays.asList("$maxTs", "$$new.maxTs"))))
                ))
                .append("whenNotMatched", "insert"));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                        Filters.gt("timestamp", lastTs),
                        Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludedIds))),
                Aggregates.group(concatId,
                        Accumulators.addToSet("sensitiveSubTypes", sensitiveCondition),
                        Accumulators.sum("paramCount", 1),
                        Accumulators.max("maxTs", "$timestamp"),
                        Accumulators.first("method", "$" + SingleTypeInfo._METHOD),
                        Accumulators.first("apiCollectionId", "$" + SingleTypeInfo._API_COLLECTION_ID),
                        Accumulators.first("url", "$" + SingleTypeInfo._URL)),
                mergeStage
        );

        SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).allowDiskUse(true).first();

        Document maxDoc = StiUniqueEndpointsDao.instance.getMCollection()
                .find(Filters.gt("maxTs", lastTs))
                .sort(new Document("maxTs", -1)).limit(1)
                .projection(Projections.include("maxTs")).first();
        return maxDoc != null ? ((Number) maxDoc.get("maxTs")).intValue() : lastTs;
    }

    /**
     * View build. Single pipeline: ApiInfo -> compute string key -> $lookup sti_unique_endpoints
     * -> filter orphans -> enrich with STI data + scope -> $out into temp collection.
     * Then create indexes on temp and atomic rename to replace the live view.
     */
    private static int buildView(Set<Integer> excludedIds, List<Integer> outOfScopeIds) {
        String stiCollName = StiUniqueEndpointsDao.instance.getCollName();
        String tempCollName = EndpointInfoViewTempDao.instance.getCollName();

        // Drop any leftover temp collection from a previous crashed run
        EndpointInfoViewTempDao.instance.getMCollection().drop();

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.nin("_id.apiCollectionId", excludedIds)),
                // Compute string key for STI lookup
                Aggregates.addFields(new Field<>("_stiKey", new Document("$concat", Arrays.asList(
                        "$_id.method", "|",
                        new Document("$toString", "$_id.apiCollectionId"), "|",
                        "$_id.url")))),
                // Lookup STI summary
                Aggregates.lookup(stiCollName, "_stiKey", "_id", "_sti"),
                // Exclude orphans
                Aggregates.match(Filters.ne("_sti", Collections.emptyList())),
                // Enrich with STI data, scope, top-level keys for filters/indexes
                Aggregates.addFields(
                        new Field<>("sensitiveSubTypes",
                                new Document("$arrayElemAt", Arrays.asList("$_sti.sensitiveSubTypes", 0))),
                        new Field<>("paramCount",
                                new Document("$arrayElemAt", Arrays.asList("$_sti.paramCount", 0))),
                        new Field<>("isOutOfTestingScope",
                                new Document("$in", Arrays.asList("$_id.apiCollectionId", outOfScopeIds))),
                        new Field<>("discoveredTimestamp",
                                new Document("$ifNull", Arrays.asList("$discoveredTimestamp", 0))),
                        new Field<>("apiCollectionId", "$_id.apiCollectionId"),
                        new Field<>("url", "$_id.url"),
                        new Field<>("method", "$_id.method"),
                        new Field<>("actualAuthType", buildActualAuthTypeExpr()),
                        new Field<>("actualAccessType", buildActualAccessTypeExpr()),
                        new Field<>("severity", buildSeverityExpr())),
                // Remove temp and unwanted fields
                Aggregates.project(Projections.exclude(
                        "_stiKey", "_sti", "collectionIds", "responseCodes",
                        "violations", "parentMcpToolNames", "lastCalculatedSubTypes",
                        "allAuthTypesFound", "apiAccessTypes")),
                Aggregates.out(tempCollName)
        );

        ApiInfoDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).allowDiskUse(true).first();

        int viewCount = (int) EndpointInfoViewTempDao.instance.getMCollection().countDocuments();

        // Create indexes on temp before swap
        EndpointInfoViewTempDao.instance.createIndicesIfAbsent();

        // Atomic swap: rename temp -> live (drops old live collection)
        EndpointInfoViewTempDao.instance.renameCollection(
                EndpointInfoViewDao.instance.getCollName());

        return viewCount;
    }

    private static Document buildActualAuthTypeExpr() {
        // Flatten allAuthTypesFound (Set<Set<String>>) into a flat array
        return new Document("$reduce", new Document()
                .append("input", new Document("$ifNull", Arrays.asList("$allAuthTypesFound", Collections.emptyList())))
                .append("initialValue", Collections.emptyList())
                .append("in", new Document("$setUnion", Arrays.asList("$$value", "$$this"))));
    }

    private static Document buildActualAccessTypeExpr() {
        // Pass through apiAccessTypes as-is (already a flat array)
        return new Document("$ifNull", Arrays.asList("$apiAccessTypes", Collections.emptyList()));
    }

    private static Document buildSeverityExpr() {
        return new Document("$switch", new Document()
                .append("branches", Arrays.asList(
                        new Document("case", new Document("$gte", Arrays.asList("$severityScore", 1000))).append("then", "CRITICAL"),
                        new Document("case", new Document("$gte", Arrays.asList("$severityScore", 100))).append("then", "HIGH"),
                        new Document("case", new Document("$gte", Arrays.asList("$severityScore", 10))).append("then", "MEDIUM"),
                        new Document("case", new Document("$gt", Arrays.asList("$severityScore", 1))).append("then", "LOW")))
                .append("default", null));
    }

    private static List<String> buildSensitiveTypesList() {
        List<String> sensitiveTypes = new ArrayList<>();
        for (Map.Entry<String, SingleTypeInfo.SubType> entry : SingleTypeInfo.subTypeMap.entrySet()) {
            if (entry.getValue().isSensitiveAlways()) {
                sensitiveTypes.add(entry.getKey());
            }
        }
        int accountId = Context.accountId.get();
        Map<String, CustomDataType> customMap = SingleTypeInfo.getCustomDataTypeMap(accountId);
        if (customMap != null) {
            for (Map.Entry<String, CustomDataType> entry : customMap.entrySet()) {
                if (entry.getValue().isSensitiveAlways() || !entry.getValue().getSensitivePosition().isEmpty()) {
                    sensitiveTypes.add(entry.getKey());
                }
            }
        }
        return sensitiveTypes;
    }

    private static Set<Integer> buildExcludedIds() {
        Set<Integer> excluded = new HashSet<>(UsageMetricCalculator.getDemosAndDeactivated());
        List<ApiCollection> apiGroups = ApiCollectionsDao.instance.findAll(
                Filters.eq(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.name()),
                Projections.include(Constants.ID));
        for (ApiCollection g : apiGroups) excluded.add(g.getId());
        return excluded;
    }

    private static List<Integer> buildOutOfScopeIds(Set<Integer> excludedIds) {
        return ApiCollectionsDao.instance.findAll(
                Filters.and(
                        Filters.nin(Constants.ID, excludedIds),
                        Filters.eq(ApiCollection.IS_OUT_OF_TESTING_SCOPE, true)),
                Projections.include(Constants.ID)
        ).stream().map(ApiCollection::getId).collect(Collectors.toList());
    }

    private static boolean tryAcquire(int threshold) {
        int now = Context.now();
        int accountId = Context.accountId.get();
        Bson filter = Filters.and(
                Filters.eq(Constants.ID, accountId),
                Filters.or(
                        Filters.lte(Account.VIEW_REFRESH_INITIATE_TS, now - threshold),
                        Filters.exists(Account.VIEW_REFRESH_INITIATE_TS, false)));
        Bson update = Updates.set(Account.VIEW_REFRESH_INITIATE_TS, now);
        try {
            Account result = AccountsDao.instance.getMCollection()
                    .findOneAndUpdate(filter, update,
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            return result != null && result.getViewRefreshInitiateTs() == now;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAccountActiveRecently() {
        int cutoff = Context.now() - ACTIVITY_LOOKBACK;
        Bson filter = Filters.and(
                Filters.exists("accounts." + Context.accountId.get()),
                Filters.gte(User.LAST_LOGIN_TS, cutoff));
        return UsersDao.instance.getMCollection().find(filter)
                .projection(Projections.include(Constants.ID)).limit(1).first() != null;
    }
}
