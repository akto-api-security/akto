package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.gpt.handlers.gpt_prompts.CollectionDescriptionPromptHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import static com.akto.task.Cluster.callDibs;

/**
 * Backfills a short, LLM-generated {@code description} for API collections that don't have one yet.
 * Runs hourly, capped at GLOBAL_RUN_LIMIT collections per run, up to CONCURRENCY LLM calls at a time.
 * Failed attempts are capped via an in-memory counter (resets on restart - acceptable, that's rare).
 */
public class CollectionDescriptionCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CollectionDescriptionCron.class, LogDb.DASHBOARD);

    private static final int GLOBAL_RUN_LIMIT = 1000;
    private static final int MAX_DESCRIPTION_CHARS = 300;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int CONCURRENCY = 5;
    private static final int MAX_ENDPOINTS_FOR_CONTEXT = 15;
    private static final int MAX_SAMPLE_ENDPOINTS = 2;
    private static final int MAX_SAMPLE_CHARS = 600;

    // collectionId -> consecutive failed attempts.
    private static final Map<Integer, Integer> failCountCache = Collections.synchronizedMap(new HashMap<>());

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpCollectionDescriptionCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, 0, 1, TimeUnit.HOURS);
    }

    private void run() {
        try {
            Context.accountId.set(1_000_000);
            if (!callDibs(Cluster.COLLECTION_DESCRIPTION_CRON, 3300, 60)) {
                loggerMaker.debugAndAddToDb("Collection description cron dibs not acquired, thus skipping cron");
                return;
            }

            AtomicInteger remaining = new AtomicInteger(GLOBAL_RUN_LIMIT);
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

            AccountTask.instance.executeTask(account -> {
                int accountId = account.getId();
                for (ApiCollection collection : findPendingCollections(remaining.get())) {
                    if (remaining.get() <= 0) {
                        break;
                    }
                    if (failCountCache.getOrDefault(collection.getId(), 0) >= MAX_FAILED_ATTEMPTS) {
                        continue;
                    }
                    remaining.decrementAndGet();
                    pool.submit(() -> generateDescription(accountId, collection));
                }
            }, "collection-description-cron");

            pool.shutdown();
            try {
                pool.awaitTermination(55, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in collection description cron: " + e.getMessage());
        }
    }

    /** Assumes Context.accountId is already set (true inside AccountTask.executeTask's callback). */
    private List<ApiCollection> findPendingCollections(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        Bson noDescriptionFilter = Filters.or(
            Filters.exists(ApiCollection.DESCRIPTION, false),
            Filters.eq(ApiCollection.DESCRIPTION, "")
        );
        // Argus (agentic: mcp-server/gen-ai tags) or Atlas (endpoint security: source=ENDPOINT tag)
        // collections only - excludes plain API Security collections for now.
        Bson argusOrAtlasFilter = Filters.and(
            Filters.exists(ApiCollection.TAGS_STRING),
            Filters.or(
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG)),
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GEN_AI_TAG)),
                Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
                    Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENDPOINT_SOURCE_TAG),
                    Filters.eq(CollectionTags.VALUE, Constants.AKTO_ENDPOINT_SOURCE_VALUE)
                ))
            )
        );
        Bson filter = Filters.and(
            noDescriptionFilter,
            argusOrAtlasFilter,
            UsageMetricCalculator.excludeDemosAndDeactivated(ApiCollection.ID)
        );

        return ApiCollectionsDao.instance.findAll(
            filter, 0, limit, Sorts.descending(ApiCollection.START_TS),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.START_TS,
                ApiCollection.HOST_NAME, ApiCollection.ACCESS_TYPE, ApiCollection.TAGS_STRING)
        );
    }

    /** MCP/GenAI/DAST/guardrail collections need a very different description than a plain REST API. */
    private static String collectionTypeLabel(ApiCollection collection) {
        if (collection.isMcpCollection()) {
            return "MCP Server";
        }
        if (collection.isGenAICollection()) {
            return "GenAI / LLM API";
        }
        if (collection.isDastCollection()) {
            return "DAST-tested application";
        }
        if (collection.isGuardRailCollection()) {
            return "Guardrail-protected API";
        }
        return null;
    }

    private static List<String> tagStrings(ApiCollection collection) {
        List<CollectionTags> tagsList = collection.getTagsList();
        List<String> tags = new ArrayList<>();
        if (tagsList == null) {
            return tags;
        }
        for (CollectionTags tag : tagsList) {
            if (tag.getKeyName() == null) {
                continue;
            }
            tags.add(tag.getKeyName() + ": " + tag.getValue());
        }
        return tags;
    }

    /** Runs on a pool thread, so Context.accountId must be set here - it doesn't cross threads. */
    private void generateDescription(int accountId, ApiCollection collection) {
        int collectionId = collection.getId();
        try {
            Context.accountId.set(accountId);

            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId),
                0, MAX_ENDPOINTS_FOR_CONTEXT, Sorts.descending(ApiInfo.LAST_SEEN),
                Projections.include(ApiInfo.ID_URL, ApiInfo.ID_METHOD)
            );

            if (apiInfos == null || apiInfos.isEmpty()) {
                markFailed(collectionId, "No endpoints found for collection");
                return;
            }

            BasicDBObject queryData = new BasicDBObject();
            queryData.put(CollectionDescriptionPromptHandler.COLLECTION_NAME, collection.getName());
            queryData.put(CollectionDescriptionPromptHandler.HOST_NAME, collection.getHostName());
            queryData.put(CollectionDescriptionPromptHandler.ACCESS_TYPE, collection.getAccessType());
            queryData.put(CollectionDescriptionPromptHandler.COLLECTION_TYPE, collectionTypeLabel(collection));
            queryData.put(CollectionDescriptionPromptHandler.TAGS, tagStrings(collection));
            queryData.put(CollectionDescriptionPromptHandler.ENDPOINTS, endpointStrings(apiInfos));
            queryData.put(CollectionDescriptionPromptHandler.SAMPLE_SNIPPETS, sampleSnippets(collectionId, apiInfos));
            queryData.put(CollectionDescriptionPromptHandler.MAX_CHARS, MAX_DESCRIPTION_CHARS);

            BasicDBObject resp = new CollectionDescriptionPromptHandler().handle(queryData);
            String description = resp != null ? resp.getString("description") : null;

            if (description == null || description.trim().isEmpty()) {
                markFailed(collectionId, resp != null ? resp.getString("error") : "Empty response from LLM handler");
                return;
            }

            if (description.length() > MAX_DESCRIPTION_CHARS) {
                description = truncateAtWordBoundary(description, MAX_DESCRIPTION_CHARS);
            }

            ApiCollectionsDao.instance.updateOne(
                Filters.eq(ApiCollection.ID, collectionId),
                Updates.set(ApiCollection.DESCRIPTION, description)
            );
            failCountCache.remove(collectionId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error generating description for collection " + collectionId + ": " + e.getMessage());
            markFailed(collectionId, e.getMessage());
        }
    }

    /** Hard-cuts at maxChars, then backs off to the last word boundary so it doesn't end mid-word. */
    private static String truncateAtWordBoundary(String text, int maxChars) {
        String cut = text.substring(0, maxChars);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace);
        }
        return cut.trim();
    }

    private static List<String> endpointStrings(List<ApiInfo> apiInfos) {
        List<String> endpoints = new ArrayList<>();
        for (ApiInfo apiInfo : apiInfos) {
            endpoints.add(apiInfo.getId().getMethod().name() + " " + apiInfo.getId().getUrl());
        }
        return endpoints;
    }

    /** Best-effort: grabs one sample per endpoint, for the first MAX_SAMPLE_ENDPOINTS endpoints only. */
    private List<String> sampleSnippets(int collectionId, List<ApiInfo> apiInfos) {
        List<String> snippets = new ArrayList<>();
        for (int i = 0; i < apiInfos.size() && snippets.size() < MAX_SAMPLE_ENDPOINTS; i++) {
            ApiInfo apiInfo = apiInfos.get(i);
            try {
                SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApi(
                    collectionId, apiInfo.getId().getUrl(), apiInfo.getId().getMethod()
                );
                if (sampleData == null || sampleData.getSamples() == null || sampleData.getSamples().isEmpty()) {
                    continue;
                }
                String sample = sampleData.getSamples().get(0);
                if (sample != null && sample.length() > MAX_SAMPLE_CHARS) {
                    sample = sample.substring(0, MAX_SAMPLE_CHARS);
                }
                snippets.add(sample);
            } catch (Exception e) {
                loggerMaker.debugAndAddToDb("Error fetching sample data for collection " + collectionId + ": " + e.getMessage());
            }
        }
        return snippets;
    }

    private void markFailed(int collectionId, String reason) {
        loggerMaker.debugAndAddToDb("Failed to generate description for collection " + collectionId + ": " + reason);
        failCountCache.put(collectionId, failCountCache.getOrDefault(collectionId, 0) + 1);
    }
}
