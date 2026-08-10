package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.gpt.handlers.gpt_prompts.EndpointDescriptionBatchPromptHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import static com.akto.task.Cluster.callDibs;

/**
 * Backfills per-endpoint descriptions (skills, MCP tools, plain AI agent/LLM endpoints) - a separate
 * concern from {@link CollectionDescriptionCron} with its own schedule, budget, and dibs lock, so one
 * doesn't starve the other. Always eligible for TEST_ATLAS_ACCOUNT_ID; tag-gated for every other account.
 */
public class EndpointDescriptionCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointDescriptionCron.class, LogDb.DASHBOARD);

    private static final int MAX_SAMPLE_CHARS = 600;
    private static final Pattern MCP_TOOL_NAME_PATTERN = Pattern.compile("tools/call/([^/?]+)");

    private static final int TEST_ATLAS_ACCOUNT_ID = 1779231193;
    private static final String ENDPOINT_DESCRIPTION_ENABLED_TAG = "endpoint-description-enabled";
    private static final int ENDPOINT_NAME_BATCH_SIZE = 25;
    private static final int AGENT_LLM_ENDPOINT_BATCH_SIZE = 10;
    private static final int MAX_ENDPOINTS_PER_COLLECTION_PER_RUN = 200;
    private static final int MAX_ENDPOINT_BATCHES_PER_RUN = 40;
    private static final int MAX_ENDPOINT_DESCRIPTION_CHARS = 150;

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpEndpointDescriptionCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, 0, 1, TimeUnit.HOURS);
    }

    private void run() {
        try {
            Context.accountId.set(1_000_000);
            if (!callDibs(Cluster.ENDPOINT_DESCRIPTION_CRON, 3300, 60)) {
                loggerMaker.debugAndAddToDb("Endpoint description cron dibs not acquired, thus skipping cron");
                return;
            }

            loggerMaker.infoAndAddToDb("Endpoint description cron starting, budget=" + MAX_ENDPOINT_BATCHES_PER_RUN);

            AtomicInteger batchesRemaining = new AtomicInteger(MAX_ENDPOINT_BATCHES_PER_RUN);
            ExecutorService pool = Executors.newFixedThreadPool(3);

            try {
                AccountTask.instance.executeTask(account -> {
                    int accountId = account.getId();
                    List<ApiCollection> eligible = findEndpointEligibleCollections(accountId, batchesRemaining.get());
                    for (ApiCollection collection : eligible) {
                        if (batchesRemaining.get() <= 0) {
                            break;
                        }
                        pool.submit(() -> generateEndpointDescriptions(
                            accountId, collection, batchesRemaining, MAX_ENDPOINTS_PER_COLLECTION_PER_RUN, false));
                    }
                }, "endpoint-description-cron");
            } finally {
                pool.shutdown();
                try {
                    pool.awaitTermination(55, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                int submitted = MAX_ENDPOINT_BATCHES_PER_RUN - batchesRemaining.get();
                loggerMaker.infoAndAddToDb("Endpoint description cron finished: batches submitted="
                    + submitted + "/" + MAX_ENDPOINT_BATCHES_PER_RUN);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in endpoint description cron: " + e.getMessage());
        }
    }

    /**
     * One-time, manually-triggered backfill/reset for a single account: regenerates the description for
     * every endpoint in every Atlas collection, overwriting whatever's already there. Not wired into any
     * scheduler - call this directly when needed. Unbounded budget, since this is a deliberate one-off
     * covering a single known account rather than the shared hourly sweep across every account.
     */
    public void forceRefreshAtlasAccount(int accountId) {
        Context.accountId.set(accountId);
        Bson filter = Filters.and(
            CollectionDescriptionCron.atlasOnlyTypeFilter(),
            UsageMetricCalculator.excludeDemosAndDeactivated(ApiCollection.ID)
        );
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
            filter, 0, Integer.MAX_VALUE, Sorts.descending(ApiCollection.START_TS),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING)
        );
        loggerMaker.infoAndAddToDb("Force-refreshing endpoint descriptions for accountId=" + accountId
            + ", collections=" + collections.size());

        AtomicInteger unboundedBatches = new AtomicInteger(Integer.MAX_VALUE);
        for (ApiCollection collection : collections) {
            generateEndpointDescriptions(accountId, collection, unboundedBatches, Integer.MAX_VALUE, true);
        }
    }

    /**
     * Collections eligible for per-endpoint description backfill: always on for TEST_ATLAS_ACCOUNT_ID,
     * tag-gated for every other account. Independent of whether the collection itself already has a
     * description - that's CollectionDescriptionCron's field, this doesn't touch it.
     */
    private List<ApiCollection> findEndpointEligibleCollections(int accountId, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        Bson filter = Filters.and(
            CollectionDescriptionCron.argusOrAtlasTypeFilter(),
            UsageMetricCalculator.excludeDemosAndDeactivated(ApiCollection.ID)
        );
        if (accountId != TEST_ATLAS_ACCOUNT_ID) {
            filter = Filters.and(filter, Filters.elemMatch(
                ApiCollection.TAGS_STRING, Filters.eq(CollectionTags.KEY_NAME, ENDPOINT_DESCRIPTION_ENABLED_TAG)
            ));
        }

        return ApiCollectionsDao.instance.findAll(
            filter, 0, limit, Sorts.descending(ApiCollection.START_TS),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING)
        );
    }

    /**
     * Skills and MCP tools are named in the URL itself (/skills/{name}, .../tools/call/{name}) -
     * hundreds can share one collection, so those are batched by name alone (cheap - no sample needed to
     * infer a name's purpose). Plain AI agent/LLM endpoints have no such name, so each needs its own
     * method+url+sample; those collections have few endpoints in practice, so no special
     * batching-for-scale is needed there.
     * Runs on a pool thread, so Context.accountId must be set here - it doesn't cross threads.
     */
    private void generateEndpointDescriptions(int accountId, ApiCollection collection, AtomicInteger batchesRemaining,
            int candidateLimit, boolean forceOverwrite) {
        int collectionId = collection.getId();
        try {
            Context.accountId.set(accountId);
            String collectionType = CollectionDescriptionCron.collectionTypeLabel(collection);
            Pattern namePattern = "Skill".equals(collectionType) ? CollectionDescriptionCron.SKILL_NAME_PATTERN
                : "MCP server".equals(collectionType) ? MCP_TOOL_NAME_PATTERN
                : null;
            String itemKind = "Skill".equals(collectionType) ? "skill"
                : "MCP server".equals(collectionType) ? "tool"
                : "AI agent".equals(collectionType) || "LLM".equals(collectionType) ? "endpoint"
                : null;
            if (itemKind == null) {
                return;
            }

            Bson idFilter = Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId);
            Bson candidateFilter = forceOverwrite ? idFilter : Filters.and(idFilter, noDescriptionFilter());
            List<ApiInfo> candidates = ApiInfoDao.instance.findAll(
                candidateFilter, 0, candidateLimit, Sorts.descending(ApiInfo.LAST_SEEN),
                // Must include ID_API_COLLECTION_ID even though it's already known (collectionId) -
                // updateEndpointDescription rebuilds the filter from apiInfo.getId(), and without this
                // projected, that composite key's apiCollectionId silently defaults to 0, corrupting the
                // write target entirely (confirmed via live test: it corrupted unrelated documents that
                // happened to share the same relative URL under apiCollectionId=0).
                Projections.include(ApiInfo.ID_URL, ApiInfo.ID_METHOD, ApiInfo.ID_API_COLLECTION_ID)
            );
            if (candidates.isEmpty()) {
                return;
            }

            String collectionContext = buildCollectionContext(collection, collectionType);
            if (namePattern != null) {
                processNameBatchedEndpoints(candidates, namePattern, itemKind, collectionContext, batchesRemaining, forceOverwrite);
            } else {
                processFullContextEndpoints(collectionId, candidates, itemKind, collectionContext, batchesRemaining, forceOverwrite);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error generating endpoint descriptions for collection " + collectionId + ": " + e.getMessage());
        }
    }

    private static Bson noDescriptionFilter() {
        return Filters.or(
            Filters.exists(ApiInfo.DESCRIPTION, false),
            Filters.eq(ApiInfo.DESCRIPTION, "")
        );
    }

    private static String buildCollectionContext(ApiCollection collection, String collectionType) {
        List<CollectionTags> tags = collection.getTagsList();
        String platform = tags == null ? null : CollectionDescriptionCron.tagValue(tags, "mcp-client");
        if (platform == null && tags != null) {
            platform = CollectionDescriptionCron.tagValue(tags, "ai-agent");
        }
        StringBuilder sb = new StringBuilder(collectionType);
        if (platform != null) {
            sb.append(" via ").append(platform);
        }
        if (collection.getHostName() != null && !collection.getHostName().trim().isEmpty()) {
            sb.append(", host ").append(collection.getHostName());
        }
        return sb.toString();
    }

    /** Groups by extracted name (skipping endpoints whose URL doesn't match), batches names only. */
    private void processNameBatchedEndpoints(List<ApiInfo> candidates, Pattern namePattern, String itemKind,
            String collectionContext, AtomicInteger batchesRemaining, boolean forceOverwrite) {
        Map<String, List<ApiInfo>> byName = new LinkedHashMap<>();
        for (ApiInfo apiInfo : candidates) {
            String name = CollectionDescriptionCron.extractName(namePattern, apiInfo.getId().getUrl());
            if (name != null) {
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(apiInfo);
            }
        }
        if (byName.isEmpty()) {
            return;
        }

        List<String> names = new ArrayList<>(byName.keySet());
        for (int i = 0; i < names.size(); i += ENDPOINT_NAME_BATCH_SIZE) {
            if (batchesRemaining.get() <= 0) {
                break;
            }
            batchesRemaining.decrementAndGet();

            List<String> batchNames = names.subList(i, Math.min(i + ENDPOINT_NAME_BATCH_SIZE, names.size()));
            List<Map<String, Object>> items = new ArrayList<>();
            for (String name : batchNames) {
                Map<String, Object> item = new HashMap<>();
                item.put(EndpointDescriptionBatchPromptHandler.ITEM_ID, name);
                items.add(item);
            }

            Map<String, String> descriptions = callEndpointBatch(itemKind, collectionContext, items);
            if (descriptions == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : descriptions.entrySet()) {
                List<ApiInfo> matches = byName.get(entry.getKey());
                if (matches == null) {
                    continue;
                }
                for (ApiInfo apiInfo : matches) {
                    updateEndpointDescription(apiInfo, entry.getValue(), forceOverwrite);
                }
            }
        }
    }

    /**
     * No extractable name - each endpoint is its own item, method+url+sample as context. The id sent to
     * the model is a plain index (not the method+url string) - confirmed via live testing that the model
     * doesn't reliably echo a long URL back verbatim as a JSON key (it collapsed to "1" for a single-item
     * batch), where a short index round-trips fine, same as the short skill/tool names in the other mode.
     */
    private void processFullContextEndpoints(int collectionId, List<ApiInfo> candidates, String itemKind,
            String collectionContext, AtomicInteger batchesRemaining, boolean forceOverwrite) {
        for (int i = 0; i < candidates.size(); i += AGENT_LLM_ENDPOINT_BATCH_SIZE) {
            if (batchesRemaining.get() <= 0) {
                break;
            }
            batchesRemaining.decrementAndGet();

            List<ApiInfo> batch = candidates.subList(i, Math.min(i + AGENT_LLM_ENDPOINT_BATCH_SIZE, candidates.size()));
            Map<String, ApiInfo> byId = new LinkedHashMap<>();
            List<Map<String, Object>> items = new ArrayList<>();
            int index = 1;
            for (ApiInfo apiInfo : batch) {
                String id = String.valueOf(index++);
                byId.put(id, apiInfo);
                String endpointText = apiInfo.getId().getMethod().name() + " " + apiInfo.getId().getUrl();
                String sample = fetchOneSample(collectionId, apiInfo);
                Map<String, Object> item = new HashMap<>();
                item.put(EndpointDescriptionBatchPromptHandler.ITEM_ID, id);
                item.put(EndpointDescriptionBatchPromptHandler.ITEM_CONTEXT,
                    sample != null ? endpointText + " | sample: " + sample : endpointText);
                items.add(item);
            }

            Map<String, String> descriptions = callEndpointBatch(itemKind, collectionContext, items);
            if (descriptions == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : descriptions.entrySet()) {
                ApiInfo apiInfo = byId.get(entry.getKey());
                if (apiInfo != null) {
                    updateEndpointDescription(apiInfo, entry.getValue(), forceOverwrite);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> callEndpointBatch(String itemKind, String collectionContext, List<Map<String, Object>> items) {
        BasicDBObject queryData = new BasicDBObject();
        queryData.put(EndpointDescriptionBatchPromptHandler.ITEM_KIND, itemKind);
        queryData.put(EndpointDescriptionBatchPromptHandler.COLLECTION_CONTEXT, collectionContext);
        queryData.put(EndpointDescriptionBatchPromptHandler.ITEMS, items);
        queryData.put(EndpointDescriptionBatchPromptHandler.MAX_CHARS, MAX_ENDPOINT_DESCRIPTION_CHARS);

        BasicDBObject resp = new EndpointDescriptionBatchPromptHandler().handle(queryData);
        Object descriptions = resp != null ? resp.get("descriptions") : null;
        return descriptions instanceof Map ? (Map<String, String>) descriptions : null;
    }

    private String fetchOneSample(int collectionId, ApiInfo apiInfo) {
        try {
            SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApi(
                collectionId, apiInfo.getId().getUrl(), apiInfo.getId().getMethod()
            );
            if (sampleData == null || sampleData.getSamples() == null || sampleData.getSamples().isEmpty()) {
                return null;
            }
            String sample = sampleData.getSamples().get(0);
            if (sample != null && sample.length() > MAX_SAMPLE_CHARS) {
                sample = sample.substring(0, MAX_SAMPLE_CHARS);
            }
            return sample;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateEndpointDescription(ApiInfo apiInfo, String description, boolean forceOverwrite) {
        if (description.length() > MAX_ENDPOINT_DESCRIPTION_CHARS) {
            description = truncateAtWordBoundary(description, MAX_ENDPOINT_DESCRIPTION_CHARS);
        }
        Bson filter = ApiInfoDao.getFilter(apiInfo.getId());
        if (!forceOverwrite) {
            filter = Filters.and(filter, noDescriptionFilter());
        }
        ApiInfoDao.instance.updateOne(filter, Updates.set(ApiInfo.DESCRIPTION, description));
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
}
