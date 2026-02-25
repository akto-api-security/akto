package com.akto.threat.backend.service;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DailyActorsCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ModifyThreatActorStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ModifyThreatActorStatusResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BulkModifyThreatActorStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BulkModifyThreatActorStatusResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ParamEnumerationConfig;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.RatelimitConfig;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.Actor;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ActorId;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.SplunkIntegrationRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.SplunkIntegrationRespone;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActivityTimelineResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchThreatsForActorRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchThreatsForActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchTopNDataResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.threat.backend.db.ActorInfoModel;
import com.akto.threat.backend.dto.ParamEnumerationConfigDTO;
import com.akto.threat.backend.dto.RateLimitConfigDTO;
import com.akto.util.ThreatDetectionConstants;
import com.akto.threat.backend.db.SplunkIntegrationModel;
import com.mongodb.client.*;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;

public class ThreatActorService {

  private final MongoClient mongoClient;
  private final MaliciousEventDao maliciousEventDao;
  private final com.akto.threat.backend.dao.ThreatConfigurationDao threatConfigurationDao = com.akto.threat.backend.dao.ThreatConfigurationDao.instance;
  private final com.akto.threat.backend.dao.SplunkIntegrationDao splunkIntegrationDao = com.akto.threat.backend.dao.SplunkIntegrationDao.instance;
  private final com.akto.threat.backend.dao.ActorInfoDao actorInfoDao = com.akto.threat.backend.dao.ActorInfoDao.instance;
  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatActorService.class, LoggerMaker.LogDb.THREAT_DETECTION);

  private static final boolean USE_ACTOR_INFO_TABLE = Boolean.parseBoolean(
      System.getenv().getOrDefault("USE_ACTOR_INFO_TABLE", "false")
  );

  public ThreatActorService(MongoClient mongoClient, MaliciousEventDao maliciousEventDao) {
    this.mongoClient = mongoClient;
    this.maliciousEventDao = maliciousEventDao;
  }

  public ThreatConfiguration fetchThreatConfiguration(String accountId) {
    ThreatConfiguration.Builder builder = ThreatConfiguration.newBuilder();
    MongoCollection<Document> coll = this.threatConfigurationDao.getCollection(accountId);
    Document doc = coll.find().first();
    if (doc != null) {
        // Handle actor configuration
        Object actorIdObj = doc.get("actor");
        if (actorIdObj instanceof List) {
            List<?> actorIdList = (List<?>) actorIdObj;
            Actor.Builder actorBuilder = Actor.newBuilder();
            for (Object idObj : actorIdList) {
                if (idObj instanceof Document) {
                    Document actorIdDoc = (Document) idObj;
                    ActorId.Builder actorIdBuilder = ActorId.newBuilder();
                    if (actorIdDoc.getString("type") != null) actorIdBuilder.setType(actorIdDoc.getString("type"));
                    if (actorIdDoc.getString("key") != null) actorIdBuilder.setKey(actorIdDoc.getString("key"));
                    if (actorIdDoc.getString("kind") != null) actorIdBuilder.setKind(actorIdDoc.getString("kind"));
                    if (actorIdDoc.getString("pattern") != null) actorIdBuilder.setPattern(actorIdDoc.getString("pattern"));
                    actorBuilder.addActorId(actorIdBuilder);
                }
            }
            builder.setActor(actorBuilder);
        }
        
        // Handle ratelimit configuration using DTO
        RatelimitConfig rateLimitConfig = RateLimitConfigDTO.parseFromDocument(doc);
        if (rateLimitConfig != null) {
            builder.setRatelimitConfig(rateLimitConfig);
        }
        
        // Handle archivalDays
        Object archivalDaysObj = doc.get("archivalDays");
        if (archivalDaysObj instanceof Number) {
            int archivalDays = ((Number) archivalDaysObj).intValue();
            builder.setArchivalDays(archivalDays);
        }

        // Handle archivalEnabled
        Object archivalEnabledObj = doc.get("archivalEnabled");
        if (archivalEnabledObj instanceof Boolean) {
            boolean archivalEnabled = (Boolean) archivalEnabledObj;
            builder.setArchivalEnabled(archivalEnabled);
        }

        // Handle paramEnumerationConfig using DTO
        ParamEnumerationConfig paramEnumConfig = ParamEnumerationConfigDTO.parseFromDocument(doc);
        if (paramEnumConfig != null) {
            builder.setParamEnumerationConfig(paramEnumConfig);
        }
    }
    return builder.build();
}

  public ThreatConfiguration modifyThreatConfiguration(String accountId, ThreatConfiguration updatedConfig) {
    ThreatConfiguration.Builder builder = ThreatConfiguration.newBuilder();
    MongoCollection<Document> coll = this.threatConfigurationDao.getCollection(accountId);

    Document newDoc = new Document();

    // Prepare a list of actorId documents
    if (updatedConfig.hasActor()) {
        List<Document> actorIdDocs = new ArrayList<>();
        Actor actor = updatedConfig.getActor();
        for (ActorId actorId : actor.getActorIdList()) {
            Document actorIdDoc = new Document();
            if (!actorId.getType().isEmpty()) actorIdDoc.append("type", actorId.getType());
            if (!actorId.getKey().isEmpty()) actorIdDoc.append("key", actorId.getKey());
            if (!actorId.getKind().isEmpty()) actorIdDoc.append("kind", actorId.getKind());
            if (!actorId.getPattern().isEmpty()) actorIdDoc.append("pattern", actorId.getPattern());
            actorIdDocs.add(actorIdDoc);
        }
        newDoc.append("actor", actorIdDocs);
    }

    // Prepare rate limit config documents using DTO
    if (updatedConfig.hasRatelimitConfig()) {
        Document ratelimitConfigDoc = RateLimitConfigDTO.toDocument(updatedConfig.getRatelimitConfig());
        if (ratelimitConfigDoc != null) {
            newDoc.append("ratelimitConfig", ratelimitConfigDoc.get("ratelimitConfig"));
        }
    }

    // Handle archivalDays - only update if explicitly set (> 0)
    if (updatedConfig.getArchivalDays() > 0) {
        newDoc.append("archivalDays", updatedConfig.getArchivalDays());
    }

    // Handle paramEnumerationConfig using DTO
    if (updatedConfig.hasParamEnumerationConfig()) {
        Document paramEnumConfigDoc = ParamEnumerationConfigDTO.toDocument(updatedConfig.getParamEnumerationConfig());
        if (paramEnumConfigDoc != null) {
            newDoc.append("paramEnumerationConfig", paramEnumConfigDoc.get("paramEnumerationConfig"));
        }
    }

    // Note: archivalEnabled is now handled by separate endpoint /toggle_archival_enabled
    // This prevents other config updates from accidentally resetting it

    Document existingDoc = coll.find().first();

    if (existingDoc != null) {
        Document updateDoc = new Document("$set", newDoc);
        coll.updateOne(new Document("_id", existingDoc.getObjectId("_id")), updateDoc);
    } else {
        coll.insertOne(newDoc);
    }

    // Set the actor, ratelimitConfig, paramEnumerationConfig in the returned proto
    if (updatedConfig.hasActor()) {
        builder.setActor(updatedConfig.getActor());
    }
    if (updatedConfig.hasRatelimitConfig()) {
        builder.setRatelimitConfig(updatedConfig.getRatelimitConfig());
    }
    if (updatedConfig.hasParamEnumerationConfig()) {
        builder.setParamEnumerationConfig(updatedConfig.getParamEnumerationConfig());
    }
    // Read archivalDays from the saved document to return the current value
    Document savedDoc = coll.find().first();
    if (savedDoc != null) {
        Object archivalDaysObj = savedDoc.get("archivalDays");
        if (archivalDaysObj instanceof Number) {
            int archivalDays = ((Number) archivalDaysObj).intValue();
            builder.setArchivalDays(archivalDays);
        }
        // archivalEnabled is handled by separate endpoint, but include in response for frontend
        Object archivalEnabledObj = savedDoc.get("archivalEnabled");
        if (archivalEnabledObj instanceof Boolean) {
            boolean archivalEnabled = (Boolean) archivalEnabledObj;
            builder.setArchivalEnabled(archivalEnabled);
        }
    }
    return builder.build();
}

  public com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ToggleArchivalEnabledResponse toggleArchivalEnabled(
      String accountId,
      com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ToggleArchivalEnabledRequest request) {

    MongoCollection<Document> coll = this.threatConfigurationDao.getCollection(accountId);
    boolean enabled = request.getEnabled();

    Document existingDoc = coll.find().first();
    Document updateDoc = new Document("$set", new Document("archivalEnabled", enabled));

    if (existingDoc != null) {
        coll.updateOne(new Document("_id", existingDoc.getObjectId("_id")), updateDoc);
    } else {
        // Create new document with just archivalEnabled
        Document newDoc = new Document("archivalEnabled", enabled);
        coll.insertOne(newDoc);
    }

    return com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ToggleArchivalEnabledResponse.newBuilder()
        .setEnabled(enabled)
        .build();
  }

    public void deleteAllMaliciousEvents(String accountId) {
        loggerMaker.infoAndAddToDb("Deleting all malicious events for accountId: " + accountId);
        maliciousEventDao.getCollection(accountId).drop();
        ThreatUtils.createIndexIfAbsent(accountId, maliciousEventDao);
        loggerMaker.infoAndAddToDb("Deleted all malicious events for accountId: " + accountId);
    }


    public ListThreatActorResponse listThreatActors(String accountId, ListThreatActorsRequest request, String contextSource) {
        if (USE_ACTOR_INFO_TABLE) {
            return listThreatActorsFromActorInfo(accountId, request, contextSource);
        } else {
            return listThreatActorsFromAggregation(accountId, request, contextSource);
        }
    }

    /**
     * ObjectId-based cursor pagination.
     * - First page: skip=0, returns first 'limit' results
     * - Subsequent pages: skip=last_seen_ObjectId (as uint32 timestamp), filter by _id > ObjectId(timestamp, 0, 0)
     */
    private ListThreatActorResponse listThreatActorsFromActorInfo(
            String accountId,
            ListThreatActorsRequest request,
            String contextSource) {

        long startTime = System.currentTimeMillis();

        int limit = request.getLimit();
        Map<String, Integer> sort = request.getSortMap();
        ListThreatActorsRequest.Filter filter = request.getFilter();

        // Sort by _id first (primary) for efficient cursor pagination, discoveredAt is embedded in _id timestamp
        // MongoDB ObjectId has timestamp in first 4 bytes, so sorting by _id gives us time-based ordering
        int sortOrder = sort.getOrDefault("discoveredAt", -1);
        Bson sortBson = sortOrder == 1 ? Sorts.ascending("_id") : Sorts.descending("_id");

        // Build base query filter
        Document match = buildActorInfoQueryFilter(filter, request, contextSource);

        // Always count total BEFORE applying cursor filter (to get consistent total across pages)
        long total = countActors(accountId, match);

        // Cursor-based pagination: use _id > cursor for efficient navigation
        String cursor = request.hasCursor() ? request.getCursor() : null;
        int skipValue = request.hasSkip() ? request.getSkip() : 0;
        boolean useCursor = (cursor != null && !cursor.isEmpty());
        boolean useSkip = (!useCursor && skipValue > 0);

        if (useCursor) {
            // Apply cursor filter AFTER counting to maintain consistent total
            try {
                org.bson.types.ObjectId lastSeenId = new org.bson.types.ObjectId(cursor);
                // For descending sort: _id < cursor, for ascending: _id > cursor
                String operator = sortOrder == 1 ? "$gt" : "$lt";
                match.append("_id", new Document(operator, lastSeenId));
            } catch (IllegalArgumentException e) {
                loggerMaker.errorAndAddToDb("Invalid cursor ObjectId: " + cursor, LoggerMaker.LogDb.THREAT_DETECTION);
                throw new RuntimeException("Invalid cursor format: " + cursor, e);
            }
        }

        List<ListThreatActorResponse.ThreatActor> actors = fetchPaginatedActors(
            accountId, match, sortBson, useSkip ? skipValue : 0, limit
        );

        long duration = System.currentTimeMillis() - startTime;
        loggerMaker.infoAndAddToDb(
            String.format("listThreatActorsFromActorInfo completed in %dms (cursor=%d, total=%d, returned=%d)",
                duration, skipValue, total, actors.size()),
            LoggerMaker.LogDb.THREAT_DETECTION
        );

        return ListThreatActorResponse.newBuilder()
            .addAllActors(actors)
            .setTotal(total)
            .build();
    }

    /**
     * Builds the MongoDB query filter for actor_info collection.
     * Applies filters in order: timestamp (first for index efficiency), then other filters.
     *
     * Filter order matters for MongoDB query performance:
     * 1. Timestamp range filter (FIRST - helps narrow dataset immediately)
     * 2. Equality filters (country, filterId, host)
     * 3. IP filter (usually less selective)
     * 4. Context filter (additional filtering)
     */
    private Document buildActorInfoQueryFilter(
            ListThreatActorsRequest.Filter filter,
            ListThreatActorsRequest request,
            String contextSource) {

        Document match = new Document();

        // 1. Timestamp Range Filter - ALWAYS FIRST for optimal index usage
        addTimestampFilter(match, filter, request);

        // 2. Attack Type Filter (filterId)
        if (!filter.getLatestAttackList().isEmpty()) {
            match.append("filterId", new Document("$in", filter.getLatestAttackList()));
        }

        // 3. Country Filter
        if (!filter.getCountryList().isEmpty()) {
            match.append("country", new Document("$in", filter.getCountryList()));
        }

        // 4. Host Filter
        if (!filter.getHostsList().isEmpty()) {
            match.append("host", new Document("$in", filter.getHostsList()));
        }

        // 5. IP Filter - deduplicate actors and latestIps lists
        addIpFilter(match, filter);

        // 6. Context Filter
        Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
        if (!contextFilter.isEmpty()) {
            match.putAll(contextFilter);
            loggerMaker.infoAndAddToDb(
                "Applied context filter: " + contextSource,
                LoggerMaker.LogDb.THREAT_DETECTION
            );
        }

        return match;
    }

    /**
     * Adds actor ID filter to query, merging and deduplicating actors and latestIps lists.
     */
    private void addIpFilter(Document match, ListThreatActorsRequest.Filter filter) {
        // Use HashSet for automatic deduplication
        java.util.Set<String> uniqueIps = new java.util.HashSet<>();

        if (!filter.getActorsList().isEmpty()) {
            uniqueIps.addAll(filter.getActorsList());
        }
        if (!filter.getLatestIpsList().isEmpty()) {
            uniqueIps.addAll(filter.getLatestIpsList());
        }

        if (!uniqueIps.isEmpty()) {
            match.append("actorId", new Document("$in", new ArrayList<>(uniqueIps)));
        }
    }

    /**
     * Adds timestamp range filter using request-level timestamps.
     * Note: filter.detected_at_time_range is never used by UI/dashboard.
     */
    private void addTimestampFilter(
            Document match,
            ListThreatActorsRequest.Filter filter,
            ListThreatActorsRequest request) {

        // UI always sends timestamps at request level (not filter level)
        Long startTs = request.getStartTs() != 0 ? (long) request.getStartTs() : null;
        Long endTs = request.getEndTs() != 0 ? (long) request.getEndTs() : null;

        if (startTs != null || endTs != null) {
            Document tsFilter = new Document();
            if (startTs != null) {
                tsFilter.append("$gte", startTs);
            }
            if (endTs != null) {
                tsFilter.append("$lte", endTs);
            }
            match.append("lastAttackTs", tsFilter);
        }
    }


    /**
     * Counts matching actors with proper error handling.
     * Throws exception on failure to ensure data consistency.
     */
    private long countActors(String accountId, Document match) {
        try {
            return actorInfoDao.getCollection(accountId).countDocuments(match);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                "Error counting actor_info documents: " + e.getMessage(),
                LoggerMaker.LogDb.THREAT_DETECTION
            );
            // Throw exception to fail fast - don't return incorrect count
            throw new RuntimeException("Failed to count threat actors from actor_info", e);
        }
    }

    /**
     * Fetches paginated actor results.
     * NEW FLOW (cursor): skip=0, filtering via _id in match Document
     * OLD FLOW (skip): skip>0, uses .skip() for backward compatibility
     */
    private List<ListThreatActorResponse.ThreatActor> fetchPaginatedActors(
            String accountId,
            Document match,
            Bson sortBson,
            int skip,
            int limit) {

        long queryStartTime = System.currentTimeMillis();
        List<ListThreatActorResponse.ThreatActor> actors = new ArrayList<>();

        try (MongoCursor<ActorInfoModel> cursor = actorInfoDao.getCollection(accountId)
                .find(match)
                .sort(sortBson)
                .skip(skip)  // 0 for cursor-based, >0 for old skip-based
                .limit(limit)
                .cursor()) {

            while (cursor.hasNext()) {
                ActorInfoModel actorInfo = cursor.next();
                actors.add(buildThreatActorResponse(actorInfo));
            }

            long queryTime = System.currentTimeMillis() - queryStartTime;
            loggerMaker.infoAndAddToDb(
                String.format("fetchPaginatedActors completed - Fetched: %d docs | Query+Iteration: %dms | skip: %d, limit: %d",
                    actors.size(), queryTime, skip, limit),
                LoggerMaker.LogDb.THREAT_DETECTION
            );

            return actors;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                "Error fetching actor_info: " + e.getMessage() +
                " | Filter: " + match.toJson() +
                " | skip: " + skip +
                " | Partial results: " + actors.size(),
                LoggerMaker.LogDb.THREAT_DETECTION
            );

            throw new RuntimeException("Failed to fetch threat actors from actor_info", e);
        }
    }

    /**
     * Builds ThreatActor response from ActorInfoModel with proper null handling.
     */
    private ListThreatActorResponse.ThreatActor buildThreatActorResponse(ActorInfoModel actorInfo) {
        String actorId = actorInfo.getActorId() != null ? actorInfo.getActorId() : "";
        String objectId = actorInfo.getId() != null ? actorInfo.getId().toHexString() : "";
        return ListThreatActorResponse.ThreatActor.newBuilder()
            .setId(actorId)
            .setObjectId(objectId)
            .setLatestApiEndpoint(actorInfo.getUrl() != null ? actorInfo.getUrl() : "")
            .setLatestApiMethod(actorInfo.getMethod() != null ? actorInfo.getMethod() : "")
            .setLatestApiIp(actorId)
            .setLatestApiHost(actorInfo.getHost() != null ? actorInfo.getHost() : "")
            .setDiscoveredAt(actorInfo.getDiscoveredAt())
            .setCountry(actorInfo.getCountry() != null ? actorInfo.getCountry() : "")
            .setLatestSubcategory(actorInfo.getFilterId() != null ? actorInfo.getFilterId() : "")
            .setLatestMetadata(actorInfo.getLatestMetadata() != null ? actorInfo.getLatestMetadata() : "")
            .build();
    }

    /**
     * OLD METHOD - Uses aggregation on malicious_events (slower but stable)
     */
    private ListThreatActorResponse listThreatActorsFromAggregation(String accountId, ListThreatActorsRequest request, String contextSource) {
        int skip = request.hasSkip() ? request.getSkip() : 0;
        int limit = request.getLimit();
        Map<String, Integer> sort = request.getSortMap();

        boolean isAgenticOrEndpoint = ThreatUtils.isAgenticOrEndpointContext(contextSource);

        ListThreatActorsRequest.Filter filter = request.getFilter();
        Document match = new Document();

        // Apply filters
        if (!filter.getActorsList().isEmpty()) match.append("actor", new Document("$in", filter.getActorsList()));
        if (!filter.getLatestIpsList().isEmpty()) match.append("latestApiIp", new Document("$in", filter.getLatestIpsList()));
        if (!filter.getLatestAttackList().isEmpty()) match.append("filterId", new Document("$in", filter.getLatestAttackList()));
        if (!filter.getCountryList().isEmpty()) match.append("country", new Document("$in", filter.getCountryList()));
        if (filter.hasDetectedAtTimeRange()) {
            match.append("detectedAt", new Document("$gte", filter.getDetectedAtTimeRange().getStart()).append("$lte", filter.getDetectedAtTimeRange().getEnd()));
        }
        if (request.getStartTs() != 0 && request.getEndTs() != 0) {
            match.append("detectedAt", new Document("$gte", request.getStartTs()).append("$lte", request.getEndTs()));
        }

        // Apply simple context filter (only for ENDPOINT and AGENTIC)
        Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
        if (!contextFilter.isEmpty()) {
            match.putAll(contextFilter);
        }

        List<Document> pipeline = new ArrayList<>();
        if (!match.isEmpty()) pipeline.add(new Document("$match", match));

        // Sort first for $first to work
        pipeline.add(new Document("$sort", new Document("detectedAt", -1)));

        Document groupDoc = new Document("_id", "$actor")
            .append("latestApiEndpoint", new Document("$first", "$latestApiEndpoint"))
            .append("latestApiMethod", new Document("$first", "$latestApiMethod"))
            .append("latestApiIp", new Document("$first", "$latestApiIp"))
            .append("latestApiHost", new Document("$first", "$host"))
            .append("country", new Document("$first", "$country"))
            .append("discoveredAt", new Document("$first", "$detectedAt"))
            .append("latestSubCategory", new Document("$first", "$filterId"));

        // Only add metadata field for AGENTIC or ENDPOINT contexts
        if (isAgenticOrEndpoint) {
            groupDoc.append("latestMetadata", new Document("$first", "$metadata"));
        }

        pipeline.add(new Document("$group", groupDoc));

        if (!filter.getHostsList().isEmpty()) {
            pipeline.add(new Document("$match", new Document("latestApiHost", new Document("$in", filter.getHostsList()))));
        }

        // Facet: count and paginated result
        List<Document> facetStages = Arrays.asList(
            new Document("$sort", new Document("discoveredAt", sort.getOrDefault("discoveredAt", -1))),
            new Document("$skip", skip),
            new Document("$limit", limit)
        );

        pipeline.add(new Document("$facet", new Document()
            .append("paginated", facetStages)
            .append("count", Arrays.asList(new Document("$count", "total")))
        ));

        Document result = maliciousEventDao.aggregateRaw(accountId, pipeline).first();
        List<Document> paginated = result.getList("paginated", Document.class, Collections.emptyList());
        List<Document> countList = result.getList("count", Document.class, Collections.emptyList());
        long total = countList.isEmpty() ? 0 : countList.get(0).getInteger("total");

        List<ListThreatActorResponse.ThreatActor> actors = new ArrayList<>();
        for (Document doc : paginated) {
            String actorId = doc.getString("_id");

            ListThreatActorResponse.ThreatActor.Builder actorBuilder = ListThreatActorResponse.ThreatActor.newBuilder()
                .setId(actorId)
                .setLatestApiEndpoint(doc.getString("latestApiEndpoint"))
                .setLatestApiMethod(doc.getString("latestApiMethod"))
                .setLatestApiIp(doc.getString("latestApiIp"))
                .setLatestApiHost(doc.getString("latestApiHost") != null ? doc.getString("latestApiHost") : "")
                .setDiscoveredAt(doc.getLong("discoveredAt"))
                .setCountry(doc.getString("country"))
                .setLatestSubcategory(doc.getString("latestSubCategory"));

            // Only add metadata field for AGENTIC or ENDPOINT contexts
            if (isAgenticOrEndpoint) {
                actorBuilder.setLatestMetadata(ThreatUtils.fetchMetadataString(doc.getString("latestMetadata")));
            }

            actors.add(actorBuilder.build());
        }

        return ListThreatActorResponse.newBuilder().addAllActors(actors).setTotal(total).build();
    }

  public DailyActorsCountResponse getDailyActorCounts(String accountId, long startTs, long endTs, List<String> latestAttackList, String contextSource) {

    // Use optimized actor_info table if feature flag is enabled
    if (USE_ACTOR_INFO_TABLE) {
      return getDailyActorCountsFromActorInfo(accountId, startTs, endTs, latestAttackList, contextSource);
    }

    List<DailyActorsCountResponse.ActorsCount> actors = new ArrayList<>();
        List<Document> pipeline = new ArrayList<>();


      Document matchConditions = new Document();

    if(latestAttackList != null && !latestAttackList.isEmpty()) {
        matchConditions.append("filterId", new Document("$in", latestAttackList));
    }

      matchConditions.append("detectedAt", new Document("$lte", endTs));
        if (startTs > 0) {
            matchConditions.get("detectedAt", Document.class).append("$gte", startTs);
        }

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
    if (!contextFilter.isEmpty()) {
        matchConditions.putAll(contextFilter);
    }

        pipeline.add(new Document("$match", matchConditions));
    
        pipeline.add(new Document("$project", 
        new Document("actor", 1)
        .append("severity", 1)
        .append("severityPriority", 
            new Document("$switch", 
                new Document("branches", Arrays.asList(
                    new Document("case", new Document("$eq", Arrays.asList("$severity", "CRITICAL"))).append("then", 4),
                    new Document("case", new Document("$eq", Arrays.asList("$severity", "HIGH"))).append("then", 3),
                    new Document("case", new Document("$eq", Arrays.asList("$severity", "MEDIUM"))).append("then", 2),
                    new Document("case", new Document("$eq", Arrays.asList("$severity", "LOW"))).append("then", 1)))
                .append("default", 0)))
        .append("dayStart", 
            new Document("$dateTrunc", 
                new Document("date", new Document("$toDate", new Document("$multiply", Arrays.asList("$detectedAt", 1000))))
                    .append("unit", "day")))));

        pipeline.add(new Document("$group", 
            new Document("_id", 
                new Document("dayStart", "$dayStart")
                    .append("actor", "$actor"))
            .append("severity", new Document("$max", "$severityPriority"))));

        pipeline.add(new Document("$project", 
            new Document("dayStart", "$_id.dayStart")
                .append("severity", 
                    new Document("$switch", 
                        new Document("branches", Arrays.asList(
                            new Document("case", new Document("$eq", Arrays.asList("$severity", 4))).append("then", "CRITICAL"),
                            new Document("case", new Document("$eq", Arrays.asList("$severity", 3))).append("then", "HIGH"),
                            new Document("case", new Document("$eq", Arrays.asList("$severity", 2))).append("then", "MEDIUM"),
                            new Document("case", new Document("$eq", Arrays.asList("$severity", 1))).append("then", "LOW")))
                        .append("default", "UNKNOWN")))));

        pipeline.add(new Document("$group", 
            new Document("_id", "$dayStart")
                .append("totalActors", new Document("$sum", 1))
                .append("severityActors", 
                    new Document("$sum", 
                        new Document("$cond", 
                            Arrays.asList(
                                new Document("$eq", Arrays.asList("$severity", "CRITICAL")),
                                1,
                                0))))));

        try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                // Convert dayStart from Date (ms) back to seconds
                long dayStartEpochSeconds = doc.getDate("_id").getTime() / 1000;
                int totalActors = doc.getInteger("totalActors");
                int highSeverityActors = doc.getInteger("severityActors");
                System.out.println(totalActors);
                System.out.println(highSeverityActors);
                actors.add(
                    DailyActorsCountResponse.ActorsCount.newBuilder()
                        .setTs((int) dayStartEpochSeconds)
                        .setTotalActors(doc.getInteger("totalActors"))
                        .setCriticalActors(doc.getInteger("severityActors"))
                        .build());
            }
        }

        // Calculate summary counts using MaliciousEventDao
        // Total analysed - count all documents matching the filter
        long totalAnalysed = maliciousEventDao.getCollection(accountId).countDocuments(matchConditions);

        // Total attacks - count documents with successfulExploit = true
        long totalAttacks = maliciousEventDao.getCollection(accountId).countDocuments(new Document(matchConditions).append("successfulExploit", true));

        int criticalActorsCount = 0;
        for (DailyActorsCountResponse.ActorsCount ac : actors) {
            criticalActorsCount += ac.getCriticalActors();
        }
        // Status aggregation for totalActive, totalIgnored, totalUnderReview
        List<Document> statusPipeline = new ArrayList<>();
        if (!matchConditions.isEmpty()) {
            statusPipeline.add(new Document("$match", matchConditions));
        }
        // Group by status and actor to get distinct actors per status
        statusPipeline.add(new Document("$group",
            new Document("_id", "$status").append("count", new Document("$sum", 1))));

        int totalActive = 0;
        int totalIgnored = 0;
        int totalUnderReview = 0;
        try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, statusPipeline).cursor()) {
            while (cursor.hasNext()) {
                Document d = cursor.next();
                String status = d.getString("_id");
                int c = d.getInteger("count", 0);
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    totalActive = c;
                } else if (ThreatDetectionConstants.IGNORED.equalsIgnoreCase(status)) {
                    totalIgnored = c;
                } else if ("UNDER_REVIEW".equalsIgnoreCase(status)) {
                    totalUnderReview = c;
                }
            }
        }

        return DailyActorsCountResponse.newBuilder()
            .addAllActorsCounts(actors)
            .setTotalAnalysed((int) totalAnalysed)
            .setTotalAttacks((int) totalAttacks)
            .setCriticalActorsCount(criticalActorsCount)
            .setTotalActive(totalActive)
            .setTotalIgnored(totalIgnored)
            .setTotalUnderReview(totalUnderReview)
            .build();
  }

  /**
   * NEW METHOD - Get actor counts from actor_info table (much more efficient)
   * Returns Critical Actors (isCritical=true) and Active Actors (status=ACTIVE)
   */
  private DailyActorsCountResponse getDailyActorCountsFromActorInfo(
      String accountId, long startTs, long endTs, List<String> latestAttackList, String contextSource) {

    long methodStartTime = System.currentTimeMillis();

    Document match = new Document();

    // Filter by time range using lastAttackTs
    if (startTs > 0 || endTs > 0) {
      Document tsFilter = new Document();
      if (startTs > 0) {
        tsFilter.append("$gte", startTs);
      }
      if (endTs > 0) {
        tsFilter.append("$lte", endTs);
      }
      match.append("lastAttackTs", tsFilter);
    }

    // Filter by filterId if provided
    if (latestAttackList != null && !latestAttackList.isEmpty()) {
      match.append("filterId", new Document("$in", latestAttackList));
    }

    // Apply context filter
    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }

    // Count Critical Actors (isCritical = true)
    long criticalQueryStart = System.currentTimeMillis();
    Document criticalMatch = new Document(match);
    criticalMatch.append("isCritical", true);
    // Use aggregation for index-only count (no document fetch)
    List<Bson> criticalPipeline = Arrays.asList(
        Aggregates.match(criticalMatch),
        Aggregates.count("total")
    );
    MongoCollection<Document> rawCollection = actorInfoDao.getCollection(accountId)
        .withDocumentClass(Document.class);
    Document criticalCountResult = rawCollection
        .aggregate(criticalPipeline)
        .hint(new Document("lastAttackTs", 1).append("contextSource", 1).append("isCritical", 1))
        .first();
    long criticalActorsCount = criticalCountResult != null ? criticalCountResult.getInteger("total", 0) : 0;
    long criticalQueryTime = System.currentTimeMillis() - criticalQueryStart;

    // Count Active Actors (status = ACTIVE)
    long activeQueryStart = System.currentTimeMillis();
    Document activeMatch = new Document(match);
    activeMatch.append("status", "ACTIVE");
    // Use aggregation for index-only count (no document fetch)
    List<Bson> activePipeline = Arrays.asList(
        Aggregates.match(activeMatch),
        Aggregates.count("total")
    );
    Document activeCountResult = rawCollection
        .aggregate(activePipeline)
        .hint(new Document("lastAttackTs", 1).append("contextSource", 1).append("status", 1))
        .first();
    long activeActorsCount = activeCountResult != null ? activeCountResult.getInteger("total", 0) : 0;
    long activeQueryTime = System.currentTimeMillis() - activeQueryStart;

    long totalMethodTime = System.currentTimeMillis() - methodStartTime;

    loggerMaker.infoAndAddToDb(
        String.format("getDailyActorCountsFromActorInfo completed - Critical: %d (%dms), Active: %d (%dms) | Total: %dms | Filter: %s",
            criticalActorsCount, criticalQueryTime, activeActorsCount, activeQueryTime, totalMethodTime, match.toJson()),
        LoggerMaker.LogDb.THREAT_DETECTION
    );

    // UI expects actorsCounts array - return single element with aggregated totals
    DailyActorsCountResponse.ActorsCount actorsCount = DailyActorsCountResponse.ActorsCount.newBuilder()
        .setTs((int) endTs)  // Use endTs as timestamp
        .setTotalActors((int) activeActorsCount)
        .setCriticalActors((int) criticalActorsCount)
        .build();

    return DailyActorsCountResponse.newBuilder()
        .addActorsCounts(actorsCount)  // Add single element to array
        .setTotalActive((int) activeActorsCount)
        .setCriticalActorsCount((int) criticalActorsCount)
        .setTotalAnalysed(0)  // Not calculated from actor_info
        .setTotalAttacks(0)   // Not calculated from actor_info
        .setTotalIgnored(0)
        .setTotalUnderReview(0)
        .build();
  }

  public ThreatActivityTimelineResponse getThreatActivityTimeline(String accountId, long startTs, long endTs, List<String> latestAttackList, String contextSource) {

        List<ThreatActivityTimelineResponse.ActivityTimeline> timeline = new ArrayList<>();
        // long sevenDaysInSeconds = TimeUnit.DAYS.toSeconds(7);
        // if (startTs < endTs - sevenDaysInSeconds) {
        //     startTs = endTs - sevenDaysInSeconds;
        // }

      Document match = new Document();

    if(latestAttackList != null && !latestAttackList.isEmpty()) {
        match.append("filterId", new Document("$in", latestAttackList));
    }

      // Stage 1: Match documents within the startTs and endTs range
      match.append("detectedAt", new Document("$gte", startTs).append("$lte", endTs));

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
    if (!contextFilter.isEmpty()) {
        match.putAll(contextFilter);
    }

      List<Document> pipeline = Arrays.asList(
        new Document("$match", match),

        // Stage 2: Project required fields and normalize timestamp to daily granularity
        new Document("$project", new Document("dayStart",
            new Document("$dateTrunc", new Document("date", 
                new Document("$toDate", new Document("$multiply", Arrays.asList("$detectedAt", 1000L))))
                    .append("unit", "day")))
            .append("subCategory", "$subCategory")),

        // Stage 3: Group by dayStart and subCategory, count occurrences
        new Document("$group", new Document("_id", 
            new Document("dayStart", "$dayStart").append("subCategory", "$subCategory"))
            .append("count", new Document("$sum", 1))),

        // Stage 4: Reshape the output to group counts by day
        new Document("$group", new Document("_id", "$_id.dayStart")
            .append("subCategoryCounts", 
                new Document("$push", new Document("subCategory", "$_id.subCategory").append("count", "$count"))))
        );

        try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                System.out.print(doc);
                
                String subCategory;
                int count;
                long ts = doc.getDate("_id").getTime() / 1000;

                List<ThreatActivityTimelineResponse.SubCategoryData> subCategoryData = new ArrayList<>();
                try {
                    List<Document> countDocs = (List<Document>) doc.get("subCategoryCounts");
                    for (Document val: countDocs) {
                        subCategory = val.getString("subCategory");
                        count = val.getInteger("count");
                        subCategoryData.add(
                            ThreatActivityTimelineResponse.SubCategoryData.newBuilder()
                            .setActivityCount(count)
                            .setSubCategory(subCategory)
                            .build()
                        );
                    }
                } catch (Exception e) {
                    continue;
                }
                if (subCategoryData.size() > 0) {
                    timeline.add(
                        ThreatActivityTimelineResponse.ActivityTimeline.newBuilder()
                        .setTs((int) ts)
                        .addAllSubCategoryWiseData(subCategoryData)
                        .build()
                    );
                }                
            }
        }

        return ThreatActivityTimelineResponse.newBuilder().addAllThreatActivityTimeline(timeline).build();
  }

  private List<FetchMaliciousEventsResponse.MaliciousPayloadsResponse> fetchMaliciousPayloadsResponse(FindIterable<MaliciousEventDto> respList){
    if (respList == null) {
      return Collections.emptyList();
    }
    List<FetchMaliciousEventsResponse.MaliciousPayloadsResponse> maliciousPayloadsResponse = new ArrayList<>();
    for (MaliciousEventDto event: respList) {
        maliciousPayloadsResponse.add(
            FetchMaliciousEventsResponse.MaliciousPayloadsResponse.newBuilder().
            setOrig(HttpResponseParams.getSampleStringFromProtoString(event.getLatestApiOrig())).
            setMetadata(ThreatUtils.fetchMetadataString(event.getMetadata() != null ? event.getMetadata() : "")).
            setTs(event.getDetectedAt()).build());
    }
    return maliciousPayloadsResponse;
  } 

  public FetchMaliciousEventsResponse fetchAggregateMaliciousRequests(
      String accountId, FetchMaliciousEventsRequest request) {

    List<FetchMaliciousEventsResponse.MaliciousPayloadsResponse> maliciousPayloadsResponse = new ArrayList<>();
    String refId = request.getRefId();
    Bson filters = Filters.eq("refId", refId);
    FindIterable<MaliciousEventDto> respList;

    if (request.getEventType().equalsIgnoreCase(MaliciousEventDto.EventType.AGGREGATED.name())) {
        Bson matchConditions = Filters.and(
            Filters.eq("actor", request.getActor()),
            Filters.eq("filterId", request.getFilterId())
        );
        matchConditions = Filters.or(
            matchConditions,
            filters
        );
        respList = maliciousEventDao.getCollection(accountId).find(matchConditions).sort(Sorts.descending("detectedAt")).limit(10);
        maliciousPayloadsResponse.addAll(this.fetchMaliciousPayloadsResponse(respList));
        // TODO: Handle case where aggregate was satisfied only once.
    } else {
        respList = maliciousEventDao.getCollection(accountId).find(filters);
        maliciousPayloadsResponse = this.fetchMaliciousPayloadsResponse(respList);

    }
    return FetchMaliciousEventsResponse.newBuilder().addAllMaliciousPayloadsResponse(maliciousPayloadsResponse).build();
  }

  public ThreatActorByCountryResponse getThreatActorByCountry(
      String accountId, ThreatActorByCountryRequest request, String contextSource) {

    // Use optimized actor_info table if feature flag is enabled
    if (USE_ACTOR_INFO_TABLE) {
      return getThreatActorByCountryFromActorInfo(accountId, request, contextSource);
    } else {
      return getThreatActorByCountryFromMaliciousEvents(accountId, request, contextSource);
    }
  }

  /**
   * NEW METHOD - Uses actor_info table (much more efficient)
   */
  private ThreatActorByCountryResponse getThreatActorByCountryFromActorInfo(
      String accountId, ThreatActorByCountryRequest request, String contextSource) {

    long methodStartTime = System.currentTimeMillis();

    Document match = new Document();

    // Filter by time range using lastAttackTs (most recent attack timestamp)
    if (request.getStartTs() != 0 || request.getEndTs() != 0) {
      Document tsFilter = new Document();
      if (request.getStartTs() != 0) {
        tsFilter.append("$gte", request.getStartTs());
      }
      if (request.getEndTs() != 0) {
        tsFilter.append("$lte", request.getEndTs());
      }
      match.append("lastAttackTs", tsFilter);
    }

    // Filter by filterId (latest attack subcategory)
    if (!request.getLatestAttackList().isEmpty()) {
      match.append("filterId", new Document("$in", request.getLatestAttackList()));
    }

    // Apply context filter (for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
    if (!contextFilter.isEmpty()) {
      match.putAll(contextFilter);
    }

    // Build aggregation pipeline - single query to group by country and count
    List<Document> pipeline = new ArrayList<>();
    pipeline.add(new Document("$match", match));

    // Group by country and count actors (each doc is one actor, so just sum 1)
    pipeline.add(new Document("$group",
        new Document("_id", "$country")
            .append("count", new Document("$sum", 1))));

    // Execute aggregation and build response
    long aggregationStart = System.currentTimeMillis();
    List<ThreatActorByCountryResponse.CountryCount> actorsByCountryCount = new ArrayList<>();

    MongoCollection<Document> rawCollection = actorInfoDao.getCollection(accountId)
        .withDocumentClass(Document.class);

    // Force MongoDB to use the optimal compound index (prevents choosing pagination index)
    com.mongodb.client.AggregateIterable<Document> aggregateResult = rawCollection.aggregate(pipeline)
        .hint(new Document("lastAttackTs", 1).append("contextSource", 1).append("country", 1))
        .allowDiskUse(true);

    for (Document doc : aggregateResult) {
      String country = doc.getString("_id");
      if (country != null && !country.isEmpty()) {
        actorsByCountryCount.add(
            ThreatActorByCountryResponse.CountryCount.newBuilder()
                .setCode(country)
                .setCount(doc.getInteger("count", 0))
                .build());
      }
    }

    long aggregationTime = System.currentTimeMillis() - aggregationStart;
    long totalMethodTime = System.currentTimeMillis() - methodStartTime;

    loggerMaker.infoAndAddToDb(
        String.format("getThreatActorByCountryFromActorInfo completed - Countries: %d | Aggregation: %dms, Total: %dms | Filter: %s",
            actorsByCountryCount.size(), aggregationTime, totalMethodTime, match.toJson()),
        LoggerMaker.LogDb.THREAT_DETECTION
    );

    return ThreatActorByCountryResponse.newBuilder().addAllCountries(actorsByCountryCount).build();
  }

  /**
   * OLD METHOD - Uses malicious_events aggregation (slower but stable)
   */
  private ThreatActorByCountryResponse getThreatActorByCountryFromMaliciousEvents(
      String accountId, ThreatActorByCountryRequest request, String contextSource) {

      List<Document> pipeline = new ArrayList<>();

      Document match = new Document();

      if (!request.getLatestAttackList().isEmpty()) {
          match.append("filterId", new Document("$in", request.getLatestAttackList()));
      }

    // 1. Match on time range
    if (request.getStartTs() != 0 || request.getEndTs() != 0) {

          match.append("detectedAt",
              new Document("$gte", request.getStartTs())
                  .append("$lte", request.getEndTs()));
    }

    // Apply simple context filter (only for ENDPOINT and AGENTIC)
    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
    if (!contextFilter.isEmpty()) {
        match.putAll(contextFilter);
    }

  pipeline.add(new Document("$match", match));

    // 2. Project only necessary fields
    pipeline.add(new Document("$project", new Document("country", 1).append("actor", 1)));

    // 3. Group by country and collect distinct actors
    pipeline.add(new Document("$group",
        new Document("_id", "$country")
            .append("distinctActorsCount", new Document("$addToSet", "$actor"))));

    // 4. Project the size of the distinct actors set
    pipeline.add(new Document("$project",
        new Document("distinctActorsCount", new Document("$size", "$distinctActorsCount"))));

    List<ThreatActorByCountryResponse.CountryCount> actorsByCountryCount = new ArrayList<>();

    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).batchSize(1000).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        actorsByCountryCount.add(
            ThreatActorByCountryResponse.CountryCount.newBuilder()
                .setCode(doc.getString("_id"))
                .setCount(doc.getInteger("distinctActorsCount", 0))
                .build());
      }
    }

    return ThreatActorByCountryResponse.newBuilder().addAllCountries(actorsByCountryCount).build();
  }

  public SplunkIntegrationRespone addSplunkIntegration(
      String accountId, SplunkIntegrationRequest req) {

        int accId = Integer.parseInt(accountId);
        MongoCollection<SplunkIntegrationModel> coll = this.splunkIntegrationDao.getCollection(accountId);

        Bson filters = Filters.eq("accountId", accId);
        long cnt = coll.countDocuments(filters);
        if (cnt > 0) {
            Bson updates = Updates.combine(
                Updates.set("splunkUrl", req.getSplunkUrl()),
                Updates.set("splunkToken", req.getSplunkToken())
            );
            this.mongoClient
            .getDatabase(accountId + "")
            .getCollection(MongoDBCollection.ThreatDetection.SPLUNK_INTEGRATION_CONFIG, Document.class)
            .updateOne(filters, updates);
        } else {
            SplunkIntegrationModel splunkIntegrationModel = SplunkIntegrationModel.newBuilder().setAccountId(accId).setSplunkToken(req.getSplunkToken()).setSplunkUrl(req.getSplunkUrl()).build();
            this.splunkIntegrationDao.getCollection(accountId).insertOne(splunkIntegrationModel);
        }
        
        return SplunkIntegrationRespone.newBuilder().build();
        

    }

    public ModifyThreatActorStatusResponse modifyThreatActorStatus(
      String accountId, ModifyThreatActorStatusRequest request) {

        MongoCollection<ActorInfoModel> coll = this.actorInfoDao.getCollection(accountId);
        String actorIp = request.getIp();

        Bson filters = Filters.eq("actorId", actorIp);
        long cnt = coll.countDocuments(filters);
        if (cnt > 0) {
            Bson updates = Updates.combine(
                Updates.set("updatedTs", request.getUpdatedTs()),
                Updates.set("status", request.getStatus())
            );
            this.mongoClient
            .getDatabase(accountId + "")
            .getCollection(MongoDBCollection.ThreatDetection.ACTOR_INFO, Document.class)
            .updateOne(filters, updates);
        } else {
            ActorInfoModel actorInfoModel = ActorInfoModel.builder()
                .actorId(actorIp)
                .status(request.getStatus())
                .updatedAt(request.getUpdatedTs())
                .build();
            this.actorInfoDao.getCollection(accountId).insertOne(actorInfoModel);
        }


        return ModifyThreatActorStatusResponse.newBuilder().build();
      }

  public BulkModifyThreatActorStatusResponse bulkModifyThreatActorStatus(
      String accountId, BulkModifyThreatActorStatusRequest request) {

    if (request.getIpsList().isEmpty()) {
      return BulkModifyThreatActorStatusResponse.newBuilder().build();
    }

    MongoCollection<Document> coll = this.mongoClient
        .getDatabase(accountId)
        .getCollection(MongoDBCollection.ThreatDetection.ACTOR_INFO, Document.class);

    List<WriteModel<Document>> bulkOps = new ArrayList<>();
    for (String ip : request.getIpsList()) {
      bulkOps.add(new UpdateOneModel<>(
          Filters.eq("actorId", ip),
          Updates.combine(
              Updates.set("updatedTs", request.getUpdatedTs()),
              Updates.set("status", request.getStatus()),
              Updates.setOnInsert("actorId", ip)
          ),
          new UpdateOptions().upsert(true)
      ));
    }

    coll.bulkWrite(bulkOps);
    return BulkModifyThreatActorStatusResponse.newBuilder().build();
  }

  public FetchThreatsForActorResponse fetchThreatsForActor(
      String accountId, FetchThreatsForActorRequest request, String contextSource) {

    String actor = request.getActor();
    int limit = request.getLimit() > 0 ? request.getLimit() : 20;

    boolean isAgenticOrEndpoint = ThreatUtils.isAgenticOrEndpointContext(contextSource);

    Document activityQuery = new Document("actor", actor);

    Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
    if (!contextFilter.isEmpty()) {
      activityQuery.putAll(contextFilter);
    }

    List<FetchThreatsForActorResponse.ActivityData> activities = new ArrayList<>();

    try (MongoCursor<MaliciousEventDto> cursor = maliciousEventDao.getCollection(accountId)
        .find(activityQuery)
        .sort(Sorts.descending("detectedAt"))
        .limit(limit)
        .cursor()) {
      while (cursor.hasNext()) {
        MaliciousEventDto event = cursor.next();
        FetchThreatsForActorResponse.ActivityData.Builder activityBuilder = FetchThreatsForActorResponse.ActivityData.newBuilder()
            .setUrl(event.getLatestApiEndpoint())
            .setDetectedAt(event.getDetectedAt())
            .setSubCategory(event.getFilterId())
            .setSeverity(event.getSeverity())
            .setMethod(event.getLatestApiMethod().name())
            .setHost(event.getHost() != null ? event.getHost() : "");

        if (isAgenticOrEndpoint) {
          activityBuilder.setMetadata(ThreatUtils.fetchMetadataString(event.getMetadata()));
        }

        activities.add(activityBuilder.build());
      }
    }

    return FetchThreatsForActorResponse.newBuilder().addAllActivities(activities).build();
  }

  public FetchTopNDataResponse fetchTopNData(
      String accountId, long startTs, long endTs, List<String> latestAttackList, int limit, String contextSource) {

    List<Document> pipeline = new ArrayList<>();

        // Match stage (only apply time range filter; ignore latestAttackList)
        Document match = new Document();
        
        if (latestAttackList != null && !latestAttackList.isEmpty()) {
            match.append("filterId", new Document("$in", latestAttackList));
        }

        if (startTs > 0 || endTs > 0) {
            Document tsRange = new Document();
            if (startTs > 0) tsRange.append("$gte", startTs);
            if (endTs > 0) tsRange.append("$lte", endTs);
            match.append("detectedAt", tsRange);
        }

        // Apply simple context filter (only for ENDPOINT and AGENTIC)
        Document contextFilter = ThreatUtils.buildSimpleContextFilterNew(contextSource);
        if (!contextFilter.isEmpty()) {
            match.putAll(contextFilter);
        }

        if (!match.isEmpty()) {
            pipeline.add(new Document("$match", match));
        }

    // Group by endpoint, method, and severity to get separate rows per severity level
    // First, add a stage to normalize severity (handle null, case-insensitive)
    pipeline.add(new Document("$addFields",
        new Document("normalizedSeverity",
            new Document("$ifNull", Arrays.asList(
                new Document("$toUpper", "$severity"),
                "UNKNOWN"
            ))
        )
    ));
    
    // Group by endpoint, method, and severity to get count per severity
    pipeline.add(new Document("$group",
        new Document("_id", new Document("endpoint", "$latestApiEndpoint")
            .append("method", "$latestApiMethod")
            .append("severity", "$normalizedSeverity"))
            .append("attacks", new Document("$sum", 1))));

    // Project to flatten the structure
    pipeline.add(new Document("$project",
        new Document("endpoint", "$_id.endpoint")
            .append("method", "$_id.method")
            .append("attacks", 1)
            .append("severity", "$_id.severity")));

    // Sort by attacks descending
    pipeline.add(new Document("$sort", new Document("attacks", -1)));

    // Limit results
    pipeline.add(new Document("$limit", limit > 0 ? limit : 5));

    List<FetchTopNDataResponse.TopApiData> topApis = new ArrayList<>();

    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        topApis.add(
            FetchTopNDataResponse.TopApiData.newBuilder()
                .setEndpoint(doc.getString("endpoint"))
                .setMethod(doc.getString("method"))
                .setAttacks(doc.getInteger("attacks"))
                .setSeverity(doc.getString("severity"))
                .build());
      }
    }

    // Build pipeline for top hosts based on 'host' field
    List<Document> hostPipeline = new ArrayList<>();
    if (!match.isEmpty()) {
      hostPipeline.add(new Document("$match", match));
    }
    // Only consider documents where host exists and is not empty
    hostPipeline.add(new Document("$match", new Document("host", new Document("$ne", null))));
    hostPipeline.add(new Document("$match", new Document("host", new Document("$ne", ""))));

    hostPipeline.add(new Document("$group",
        new Document("_id", "$host")
            .append("attacks", new Document("$sum", 1))));

    hostPipeline.add(new Document("$project",
        new Document("host", "$_id")
            .append("attacks", 1)));

    hostPipeline.add(new Document("$sort", new Document("attacks", -1)));
    hostPipeline.add(new Document("$limit", limit > 0 ? limit : 5));

    List<FetchTopNDataResponse.TopHostData> topHosts = new ArrayList<>();
    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, hostPipeline).cursor()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        topHosts.add(
            FetchTopNDataResponse.TopHostData.newBuilder()
                .setHost(doc.getString("host"))
                .setAttacks(doc.getInteger("attacks", 0))
                .build());
      }
    }

    return FetchTopNDataResponse.newBuilder()
        .addAllTopApis(topApis)
        .addAllTopHosts(topHosts)
        .build();
  }
}

