package com.akto.threat.backend.service;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.billing.Organization;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DailyActorsCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ModifyThreatActorStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ModifyThreatActorStatusResponse;
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
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse.ActivityData;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchTopNDataResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.GetThreatActorsResponse;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.threat.backend.db.ActorInfoModel;
import com.akto.threat.backend.dto.ParamEnumerationConfigDTO;
import com.akto.threat.backend.dto.RateLimitConfigDTO;
import com.akto.util.ThreatDetectionConstants;
import com.akto.threat.backend.db.SplunkIntegrationModel;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;

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
        Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource);
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

        // Activity fetch
        List<ListThreatActorResponse.ThreatActor> actors = new ArrayList<>();
        for (Document doc : paginated) {
            String actorId = doc.getString("_id");
            List<ActivityData> activityDataList = new ArrayList<>();

            // Build activity query with filtering
            Document activityQuery = new Document("actor", actorId);

            if (!contextFilter.isEmpty()) {
                activityQuery.putAll(contextFilter);
            }

            try (MongoCursor<MaliciousEventDto> cursor2 = maliciousEventDao.getCollection(accountId)
                    .find(activityQuery)
                    .sort(Sorts.descending("detectedAt"))
                    .limit(40)
                    .cursor()) {
                while (cursor2.hasNext()) {
                    MaliciousEventDto event = cursor2.next();
                    ActivityData.Builder activityBuilder = ActivityData.newBuilder()
                        .setUrl(event.getLatestApiEndpoint())
                        .setDetectedAt(event.getDetectedAt())
                        .setSubCategory(event.getFilterId())
                        .setSeverity(event.getSeverity())
                        .setMethod(event.getLatestApiMethod().name())
                        .setHost(event.getHost() != null ? event.getHost() : "");

                    // Only add metadata field for AGENTIC or ENDPOINT contexts
                    if (isAgenticOrEndpoint) {
                        activityBuilder.setMetadata(ThreatUtils.fetchMetadataString(event.getMetadata()));
                    }

                    activityDataList.add(activityBuilder.build());
                }
            }

            ListThreatActorResponse.ThreatActor.Builder actorBuilder = ListThreatActorResponse.ThreatActor.newBuilder()
                .setId(actorId)
                .setLatestApiEndpoint(doc.getString("latestApiEndpoint"))
                .setLatestApiMethod(doc.getString("latestApiMethod"))
                .setLatestApiIp(doc.getString("latestApiIp"))
                .setLatestApiHost(doc.getString("latestApiHost") != null ? doc.getString("latestApiHost") : "")
                .setDiscoveredAt(doc.getLong("discoveredAt"))
                .setCountry(doc.getString("country"))
                .setLatestSubcategory(doc.getString("latestSubCategory"))
                .addAllActivityData(activityDataList);

            // Only add metadata field for AGENTIC or ENDPOINT contexts
            if (isAgenticOrEndpoint) {
                actorBuilder.setLatestMetadata(ThreatUtils.fetchMetadataString(doc.getString("latestMetadata")));
            }

            actors.add(actorBuilder.build());
        }

        return ListThreatActorResponse.newBuilder().addAllActors(actors).setTotal(total).build();
    }

  public DailyActorsCountResponse getDailyActorCounts(String accountId, long startTs, long endTs, List<String> latestAttackList, String contextSource) {

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
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource);
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
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource);
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
    Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource);
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

        Bson filters = Filters.eq("ip", actorIp);
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
            ActorInfoModel actorInfoModel = ActorInfoModel.newBuilder().setIp(actorIp).
              setStatus(request.getStatus()).setUpdatedTs(request.getUpdatedTs()).build();
            this.actorInfoDao.getCollection(accountId).insertOne(actorInfoModel);
        }


        return ModifyThreatActorStatusResponse.newBuilder().build();
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
        Document contextFilter = ThreatUtils.buildSimpleContextFilter(contextSource);
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

  public GetThreatActorsResponse getThreatActors(String accountId, long startTs, long endTs) {
    List<Document> pipeline = new ArrayList<>();

    Document match = new Document("detectedAt", new Document("$gte", startTs).append("$lte", endTs));
    pipeline.add(new Document("$match", match));

    pipeline.add(new Document("$group",new Document("_id", "$actor").append("severities", new Document("$addToSet", "$severity"))));

    pipeline.add(new Document("$project",new Document("hasCritical", new Document("$in", Arrays.asList("CRITICAL", "$severities")))));

    pipeline.add(new Document("$group",new Document("_id", null).append("totalActors", new Document("$sum", 1)).append("criticalActors", new Document("$sum", new Document("$cond", Arrays.asList("$hasCritical", 1, 0))))));

    pipeline.add(new Document("$project",new Document("_id", 0).append("totalActors", 1).append("criticalActors", 1)));

    int totalActors = 0;
    int criticalActors = 0;

    try (MongoCursor<Document> cursor = maliciousEventDao.aggregateRaw(accountId, pipeline).cursor()) {
      if (cursor.hasNext()) {
        Document doc = cursor.next();
        totalActors = doc.getInteger("totalActors", 0);
        criticalActors = doc.getInteger("criticalActors", 0);
      }
    }

    return GetThreatActorsResponse.newBuilder()
        .setTotalActors(totalActors)
        .setCriticalActors(criticalActors)
        .build();
  }
}

