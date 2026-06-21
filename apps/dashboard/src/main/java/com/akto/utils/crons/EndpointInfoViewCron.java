package com.akto.utils.crons;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AccountTask;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
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
    private static final int ACTIVE_THRESHOLD = 10 * 60;
    private static final int INACTIVE_THRESHOLD = 24 * 60 * 60;
    private static final int ACTIVITY_LOOKBACK = 3 * 24 * 60 * 60;
    private static final int STI_FULL_REBUILD_INTERVAL = 24 * 60 * 60;

    private static final String TEMP_COLL = EndpointInfoViewTempDao.instance.getCollName();
    private static final String LIVE_COLL = EndpointInfoViewDao.instance.getCollName();

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
        }, 0, 10, TimeUnit.MINUTES);
    }

    public static void refreshView() {
        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if (settings == null || !settings.isEnableEndpointInfoView()) return;

        int configuredThreshold = settings.getViewRefreshThresholdSeconds();
        int threshold = configuredThreshold > 0 ? configuredThreshold
                : (isAccountActiveRecently() ? ACTIVE_THRESHOLD : INACTIVE_THRESHOLD);
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

        List<Integer> hostCollectionIds = buildHostCollectionIds(excludedIds);

        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, accountId));
        int stiLastFullRebuildTs = account != null ? account.getStiLastFullRebuildTs() : 0;
        int stiLastIncrementalTs = account != null ? account.getStiLastIncrementalTs() : 0;

        boolean needsFullRebuild = stiLastFullRebuildTs == 0
                || (Context.now() - stiLastFullRebuildTs > STI_FULL_REBUILD_INTERVAL);

        int viewCount;
        if (needsFullRebuild) {
            long rebuildStart = System.currentTimeMillis();
            viewCount = fullRebuild(excludedIds, outOfScopeIds, sensitiveTypes, hostCollectionIds);
            stiLastIncrementalTs = getMaxStiTimestamp(excludedIds);
            logger.warnAndAddToDb("EndpointInfoView fullRebuild took "
                    + (System.currentTimeMillis() - rebuildStart) + "ms, count=" + viewCount
                    + ", account=" + accountId);
        } else {
            long stiStart = System.currentTimeMillis();
            incrementalStiMerge(stiLastIncrementalTs, excludedIds, sensitiveTypes);
            int maxTs = getMaxStiTimestampSince(stiLastIncrementalTs, excludedIds);
            if (maxTs > stiLastIncrementalTs) {
                stiLastIncrementalTs = maxTs;
            }
            logger.warnAndAddToDb("EndpointInfoView incrementalStiMerge took "
                    + (System.currentTimeMillis() - stiStart) + "ms, account=" + accountId);

            long viewStart = System.currentTimeMillis();
            viewCount = refreshApiInfoData(excludedIds, outOfScopeIds, hostCollectionIds);
            logger.warnAndAddToDb("EndpointInfoView refreshApiInfoData took "
                    + (System.currentTimeMillis() - viewStart) + "ms, count=" + viewCount
                    + ", account=" + accountId);
        }

        // Persist STI state
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(Account.STI_LAST_INCREMENTAL_TS, stiLastIncrementalTs));
        if (needsFullRebuild) {
            updates.add(Updates.set(Account.STI_LAST_FULL_REBUILD_TS, Context.now()));
        }
        AccountsDao.instance.updateOne(Filters.eq(Constants.ID, accountId), Updates.combine(updates));

        long totalTime = System.currentTimeMillis() - startMs;
        logger.warnAndAddToDb("EndpointInfoView refreshed: " + viewCount + " endpoints, account=" + accountId
                + ", total=" + totalTime + "ms");
    }

    /**
     * Full rebuild (every 24h):
     * Phase 1: STIs -> $group -> set flags -> $out temp
     * Phase 2: Create unique index on temp {apiCollectionId, url, method}
     * Phase 3: api_info -> extract top-level fields -> $merge into temp (sets api_info fields + derived fields)
     * Phase 4: Rename temp -> live
     */
    private static int fullRebuild(Set<Integer> excludedIds, List<Integer> outOfScopeIds, List<String> sensitiveTypes, List<Integer> hostCollectionIds) {
        EndpointInfoViewTempDao.instance.getMCollection().drop();

        // Phase 1: STIs -> group -> set flags -> $out temp
        long phase1Start = System.currentTimeMillis();
        runStiGroupPipeline(excludedIds, outOfScopeIds, sensitiveTypes, hostCollectionIds);
        logger.warnAndAddToDb("EndpointInfoView fullRebuild phase1 (STI group) took "
                + (System.currentTimeMillis() - phase1Start) + "ms");

        // Phase 2: Create unique index for $merge matching
        long phase2Start = System.currentTimeMillis();
        createMergeKeyIndex();
        logger.warnAndAddToDb("EndpointInfoView fullRebuild phase2 (create index) took "
                + (System.currentTimeMillis() - phase2Start) + "ms");

        // Phase 3: api_info -> $merge into temp
        long phase3Start = System.currentTimeMillis();
        runApiInfoMerge(excludedIds, TEMP_COLL);
        logger.warnAndAddToDb("EndpointInfoView fullRebuild phase3 (api_info merge) took "
                + (System.currentTimeMillis() - phase3Start) + "ms");

        // Phase 4: Rename temp -> live
        return swapTempToLive();
    }

    /**
     * Phase 1: STIs -> $group by endpoint -> set flags via precomputed lists -> $out temp
     */
    private static void runStiGroupPipeline(Set<Integer> excludedIds, List<Integer> outOfScopeIds, List<String> sensitiveTypes, List<Integer> hostCollectionIds) {
        Document compoundId = buildCompoundId();
        Document sensitiveCondition = buildSensitiveCondition(sensitiveTypes);
        Document hasHostHeaderCondition = buildHasHostHeaderCondition();

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludedIds)),
                Aggregates.group(compoundId,
                        Accumulators.addToSet("sensitiveSubTypes", sensitiveCondition),
                        Accumulators.sum("paramCount", 1),
                        Accumulators.max("hasHostHeader", hasHostHeaderCondition),
                        Accumulators.min("discoveredTimestamp", "$timestamp"),
                        Accumulators.max("lastSeen", "$timestamp")),
                // Extract top-level fields and convert hasHostHeader to boolean
                Aggregates.addFields(
                        new Field<>("apiCollectionId", "$_id.apiCollectionId"),
                        new Field<>("url", "$_id.url"),
                        new Field<>("method", "$_id.method"),
                        new Field<>("hasHostHeader", new Document("$cond", Arrays.asList(
                                "$hasHostHeader", true, false)))),
                Aggregates.addFields(
                        new Field<>("isHostCollection",
                                new Document("$in", Arrays.asList("$_id.apiCollectionId", hostCollectionIds))),
                        new Field<>("isOutOfTestingScope",
                                new Document("$in", Arrays.asList("$_id.apiCollectionId", outOfScopeIds))),
                        // Set api_info defaults (will be overwritten by $merge for matched endpoints)
                        new Field<>("riskScore", 0),
                        new Field<>("severityScore", 0),
                        new Field<>("lastTested", 0),
                        new Field<>("actualAuthType", Collections.emptyList()),
                        new Field<>("actualAccessType", Collections.emptyList()),
                        new Field<>("severity", null)),
                Aggregates.out(TEMP_COLL)
        );

        SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).allowDiskUse(true).first();
    }

    /**
     * Create unique index on temp for $merge matching on {apiCollectionId, url, method}.
     */
    private static void createMergeKeyIndex() {
        MCollection.createIndexIfAbsent(
                EndpointInfoViewTempDao.instance.getDBName(), TEMP_COLL,
                Indexes.ascending("apiCollectionId", "url", "method"),
                new IndexOptions().name("merge_key_unique").unique(true));
    }

    /**
     * Phase 3: api_info -> extract top-level fields -> $merge into target collection.
     * Sets api_info fields + derived fields on matching docs. Discards api_info-only endpoints.
     */
    private static void runApiInfoMerge(Set<Integer> excludedIds, String targetColl) {
        // Build the whenMatched pipeline: set api_info fields + compute derived fields
        Document whenMatchedSet = new Document()
                .append("riskScore", ifNull("$$new.riskScore", "$riskScore"))
                .append("severityScore", ifNull("$$new.severityScore", "$severityScore"))
                .append("lastTested", ifNull("$$new.lastTested", "$lastTested"))
                .append("lastSeen", ifNull("$$new.lastSeen", "$lastSeen"))
                .append("discoveredTimestamp", ifNull("$$new.discoveredTimestamp", "$discoveredTimestamp"))
                .append("apiType", ifNull("$$new.apiType", "$apiType"))
                .append("actualAuthType", "$$new.actualAuthType")
                .append("actualAccessType", "$$new.actualAccessType")
                .append("severity", ifNull("$$new.severity", "$severity"));

        Document mergeStage = new Document("$merge", new Document()
                .append("into", targetColl)
                .append("on", Arrays.asList("apiCollectionId", "url", "method"))
                .append("whenMatched", Arrays.asList(new Document("$set", whenMatchedSet)))
                .append("whenNotMatched", "discard"));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.nin("_id.apiCollectionId", excludedIds)),
                // Extract top-level fields from compound _id
                Aggregates.addFields(
                        new Field<>("apiCollectionId", "$_id.apiCollectionId"),
                        new Field<>("url", "$_id.url"),
                        new Field<>("method", "$_id.method"),
                        // Flatten allAuthTypesFound
                        new Field<>("actualAuthType", buildActualAuthTypeExpr()),
                        new Field<>("actualAccessType", buildActualAccessTypeExpr()),
                        new Field<>("severity", buildSeverityExpr())),
                // Keep only the fields we need for the merge
                Aggregates.project(Projections.fields(
                        Projections.excludeId(),
                        Projections.include("apiCollectionId", "url", "method",
                                "riskScore", "severityScore", "lastTested", "lastSeen",
                                "discoveredTimestamp", "apiType",
                                "actualAuthType", "actualAccessType", "severity"))),
                mergeStage
        );

        ApiInfoDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).allowDiskUse(true).first();
    }

    /**
     * Incremental STI merge: processes only STIs newer than lastTs,
     * groups by endpoint, and merges STI fields directly into the live view.
     */
    private static void incrementalStiMerge(int lastTs, Set<Integer> excludedIds, List<String> sensitiveTypes) {
        Document compoundId = buildCompoundId();
        Document sensitiveCondition = buildSensitiveCondition(sensitiveTypes);
        Document hasHostHeaderCondition = buildHasHostHeaderCondition();

        Document mergeStage = new Document("$merge", new Document()
                .append("into", LIVE_COLL)
                .append("on", Arrays.asList("apiCollectionId", "url", "method"))
                .append("whenMatched", Arrays.asList(
                        new Document("$set", new Document()
                                .append("sensitiveSubTypes", new Document("$setUnion", Arrays.asList(
                                        new Document("$ifNull", Arrays.asList("$sensitiveSubTypes", Collections.emptyList())),
                                        "$$new.sensitiveSubTypes")))
                                .append("hasHostHeader", new Document("$cond", Arrays.asList(
                                        new Document("$or", Arrays.asList(
                                                new Document("$eq", Arrays.asList("$hasHostHeader", true)),
                                                new Document("$eq", Arrays.asList("$$new.hasHostHeader", true)))),
                                        true, false))))
                ))
                .append("whenNotMatched", "insert"));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                        Filters.gt("timestamp", lastTs),
                        Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludedIds))),
                Aggregates.group(compoundId,
                        Accumulators.addToSet("sensitiveSubTypes", sensitiveCondition),
                        Accumulators.sum("paramCount", 1),
                        Accumulators.max("hasHostHeader", hasHostHeaderCondition)),
                Aggregates.addFields(
                        new Field<>("apiCollectionId", "$_id.apiCollectionId"),
                        new Field<>("url", "$_id.url"),
                        new Field<>("method", "$_id.method"),
                        new Field<>("hasHostHeader", new Document("$cond", Arrays.asList(
                                "$hasHostHeader", true, false)))),
                mergeStage
        );

        SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, Document.class).allowDiskUse(true).first();
    }

    /**
     * Refresh api_info data (every 30 min on incremental cycles):
     * Copies live view to temp, creates merge index, merges api_info, swaps.
     */
    private static int refreshApiInfoData(Set<Integer> excludedIds, List<Integer> outOfScopeIds, List<Integer> hostCollectionIds) {
        EndpointInfoViewTempDao.instance.getMCollection().drop();

        // Copy live view to temp (refresh api_collections flags using precomputed lists)
        List<Bson> copyPipeline = Arrays.asList(
                Aggregates.addFields(
                        new Field<>("isHostCollection",
                                new Document("$in", Arrays.asList("$apiCollectionId", hostCollectionIds))),
                        new Field<>("isOutOfTestingScope",
                                new Document("$in", Arrays.asList("$apiCollectionId", outOfScopeIds)))),
                Aggregates.out(TEMP_COLL)
        );

        EndpointInfoViewDao.instance.getMCollection()
                .aggregate(copyPipeline, Document.class).allowDiskUse(true).first();

        // Create merge index + merge api_info
        createMergeKeyIndex();
        runApiInfoMerge(excludedIds, TEMP_COLL);

        return swapTempToLive();
    }

    // --- Shared pipeline helpers ---

    private static Document buildCompoundId() {
        return new Document()
                .append("method", "$" + SingleTypeInfo._METHOD)
                .append("url", "$" + SingleTypeInfo._URL)
                .append("apiCollectionId", "$" + SingleTypeInfo._API_COLLECTION_ID);
    }

    private static Document buildSensitiveCondition(List<String> sensitiveTypes) {
        return new Document("$cond", Arrays.asList(
                new Document("$in", Arrays.asList("$" + SingleTypeInfo.SUB_TYPE, sensitiveTypes)),
                "$" + SingleTypeInfo.SUB_TYPE,
                "$$REMOVE"));
    }

    private static Document buildHasHostHeaderCondition() {
        return new Document("$cond", Arrays.asList(
                new Document("$and", Arrays.asList(
                        new Document("$eq", Arrays.asList("$" + SingleTypeInfo._PARAM, "host")),
                        new Document("$eq", Arrays.asList("$" + SingleTypeInfo._IS_HEADER, true)),
                        new Document("$eq", Arrays.asList("$" + SingleTypeInfo._RESPONSE_CODE, -1)))),
                1, 0));
    }

    private static int swapTempToLive() {
        int viewCount = (int) EndpointInfoViewTempDao.instance.getMCollection().countDocuments();
        EndpointInfoViewTempDao.instance.createIndicesIfAbsent();
        EndpointInfoViewTempDao.instance.renameCollection(
                EndpointInfoViewDao.instance.getCollName());
        return viewCount;
    }

    private static int getMaxStiTimestamp(Set<Integer> excludedIds) {
        SingleTypeInfo doc = SingleTypeInfoDao.instance.getMCollection()
                .find(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludedIds))
                .sort(Sorts.descending("timestamp")).limit(1)
                .projection(Projections.include("timestamp")).first();
        return doc != null ? doc.getTimestamp() : 0;
    }

    private static int getMaxStiTimestampSince(int lastTs, Set<Integer> excludedIds) {
        SingleTypeInfo doc = SingleTypeInfoDao.instance.getMCollection()
                .find(Filters.and(
                        Filters.gt("timestamp", lastTs),
                        Filters.nin(SingleTypeInfo._API_COLLECTION_ID, excludedIds)))
                .sort(Sorts.descending("timestamp")).limit(1)
                .projection(Projections.include("timestamp")).first();
        return doc != null ? doc.getTimestamp() : lastTs;
    }

    private static Document ifNull(String expr, String fallback) {
        return new Document("$ifNull", Arrays.asList(expr, fallback));
    }

    private static Document buildActualAuthTypeExpr() {
        return new Document("$reduce", new Document()
                .append("input", new Document("$ifNull", Arrays.asList("$allAuthTypesFound", Collections.emptyList())))
                .append("initialValue", Collections.emptyList())
                .append("in", new Document("$setUnion", Arrays.asList("$$value", "$$this"))));
    }

    private static Document buildActualAccessTypeExpr() {
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

    private static List<Integer> buildHostCollectionIds(Set<Integer> excludedIds) {
        return ApiCollectionsDao.instance.findAll(
                Filters.ne(ApiCollection.HOST_NAME, null),
                Projections.include(Constants.ID)
        ).stream().map(ApiCollection::getId).filter(id -> !excludedIds.contains(id)).collect(Collectors.toList());
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
