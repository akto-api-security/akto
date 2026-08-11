package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
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
import com.akto.gpt.handlers.gpt_prompts.CollectionDescriptionPromptHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.usage.UsageMetricCalculator;
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
 * Per-endpoint descriptions (skills/MCP tools/agent-LLM endpoints) are a separate concern, handled by
 * {@link EndpointDescriptionCron} on its own schedule and budget.
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

    // Explicit allowlist - this cron no longer sweeps every account, only these. Add more IDs here as
    // needed.
    private static final List<Integer> ALLOWED_ACCOUNT_IDS = Collections.singletonList(1779231193);

    // Skill/MCP-server collections can have hundreds of distinct skills or tools - sampled wide enough
    // to report the true count, but only a slice of names is ever put in a prompt.
    private static final int MAX_LIBRARY_SAMPLE_QUERY = 1000;
    private static final int MAX_LIBRARY_NAMES_IN_PROMPT = 40;
    static final Pattern SKILL_NAME_PATTERN = Pattern.compile("/skills/([^/?]+)");
    private static final Pattern MCP_TOOL_NAME_PATTERN = Pattern.compile("tools/call/([^/?]+)");

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

            loggerMaker.infoAndAddToDb("Collection description cron starting, budget=" + GLOBAL_RUN_LIMIT);

            AtomicInteger remaining = new AtomicInteger(GLOBAL_RUN_LIMIT);
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

            try {
                for (int accountId : ALLOWED_ACCOUNT_IDS) {
                    if (remaining.get() <= 0) {
                        break;
                    }
                    processAccountCollections(accountId, remaining, pool);
                }
            } finally {
                // Always shut the pool down, even if account iteration itself failed (e.g. the initial
                // account fetch throwing) - otherwise these threads leak for good, since nothing else
                // ever references this pool again.
                pool.shutdown();
                try {
                    pool.awaitTermination(55, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                int submitted = GLOBAL_RUN_LIMIT - remaining.get();
                loggerMaker.infoAndAddToDb("Collection description cron finished: submitted="
                    + submitted + "/" + GLOBAL_RUN_LIMIT);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in collection description cron: " + e.getMessage());
        }
    }

    /** Sets Context.accountId itself - called once per account in ALLOWED_ACCOUNT_IDS. */
    private void processAccountCollections(int accountId, AtomicInteger remaining, ExecutorService pool) {
        Context.accountId.set(accountId);
        List<ApiCollection> pending = findPendingCollections(remaining.get());
        if (pending.isEmpty()) {
            return;
        }
        loggerMaker.infoAndAddToDb("Collection description cron processing accountId="
            + accountId + ", pending=" + pending.size());
        for (ApiCollection collection : pending) {
            if (remaining.get() <= 0) {
                break;
            }
            if (failCountCache.getOrDefault(collection.getId(), 0) >= MAX_FAILED_ATTEMPTS) {
                continue;
            }
            remaining.decrementAndGet();
            pool.submit(() -> generateDescription(accountId, collection));
        }
    }

    /**
     * One-time, manually-triggered backfill/reset for a single account: regenerates the description for
     * every Atlas collection in it, overwriting whatever's already there (unlike the regular hourly run,
     * which only ever fills in collections that have none). Not wired into any scheduler - call this
     * directly when needed.
     */
    public void forceRefreshAtlasAccount(int accountId) {
        Context.accountId.set(accountId);
        Bson filter = Filters.and(atlasOnlyTypeFilter(), UsageMetricCalculator.excludeDemosAndDeactivated(ApiCollection.ID));
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
            filter, 0, Integer.MAX_VALUE, Sorts.descending(ApiCollection.START_TS),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.START_TS,
                ApiCollection.HOST_NAME, ApiCollection.ACCESS_TYPE, ApiCollection.TAGS_STRING)
        );
        loggerMaker.infoAndAddToDb("Force-refreshing collection descriptions for accountId=" + accountId
            + ", collections=" + collections.size());
        for (ApiCollection collection : collections) {
            generateDescription(accountId, collection);
        }
    }

    /** Assumes Context.accountId is already set by the caller. */
    private List<ApiCollection> findPendingCollections(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        Bson noDescriptionFilter = Filters.or(
            Filters.exists(ApiCollection.DESCRIPTION, false),
            Filters.eq(ApiCollection.DESCRIPTION, "")
        );
        Bson filter = Filters.and(
            noDescriptionFilter,
            argusOrAtlasTypeFilter(),
            UsageMetricCalculator.excludeDemosAndDeactivated(ApiCollection.ID)
        );

        return ApiCollectionsDao.instance.findAll(
            filter, 0, limit, Sorts.descending(ApiCollection.START_TS),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.START_TS,
                ApiCollection.HOST_NAME, ApiCollection.ACCESS_TYPE, ApiCollection.TAGS_STRING)
        );
    }

    // Argus (agentic: mcp-server/gen-ai tags) or Atlas (endpoint security: source=ENDPOINT tag)
    // collections only - excludes plain API Security collections for now.
    static Bson argusOrAtlasTypeFilter() {
        return Filters.and(
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
    }

    // Atlas (endpoint security: source=ENDPOINT tag) only - narrower than argusOrAtlasTypeFilter, used
    // for the account-scoped force-refresh which is explicitly Atlas-only.
    static Bson atlasOnlyTypeFilter() {
        return Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
            Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENDPOINT_SOURCE_TAG),
            Filters.eq(CollectionTags.VALUE, Constants.AKTO_ENDPOINT_SOURCE_VALUE)
        ));
    }

    /**
     * Argus/Atlas collections come in 4 flavors - Skill, MCP server, AI agent, or LLM - and tags already
     * say which: "skill", "mcp-server", "ai-agent" tags, or a "gen-ai" tag valued "AI Agent"/"LLM".
     * None of these labels say "API" - an agent or MCP server isn't one, and calling it that in the
     * generated description reads wrong.
     */
    static String collectionTypeLabel(ApiCollection collection) {
        List<CollectionTags> tags = collection.getTagsList();
        if (tags == null || tags.isEmpty()) {
            return null;
        }

        if (tagValue(tags, "skill") != null) {
            return "Skill";
        }
        if (tagValue(tags, "mcp-server") != null) {
            return "MCP server";
        }
        if (tagValue(tags, "ai-agent") != null) {
            return "AI agent";
        }
        return genAiTypeLabel(tagValue(tags, "gen-ai"));
    }

    /** The gen-ai tag is only ever "AI Agent" or "LLM" - nothing else to handle. */
    private static String genAiTypeLabel(String genAiValue) {
        if ("AI Agent".equalsIgnoreCase(genAiValue)) {
            return "AI agent";
        }
        if ("LLM".equalsIgnoreCase(genAiValue)) {
            return "LLM";
        }
        return null;
    }

    static String tagValue(List<CollectionTags> tags, String keyName) {
        for (CollectionTags tag : tags) {
            if (keyName.equals(tag.getKeyName())) {
                return tag.getValue();
            }
        }
        return null;
    }

    private static String skillTagValue(ApiCollection collection) {
        List<CollectionTags> tags = collection.getTagsList();
        return tags == null ? null : tagValue(tags, "skill");
    }

    private static final class NamedItemLibrary {
        final int distinctCount;
        final List<String> sampleNames;

        NamedItemLibrary(int distinctCount, List<String> sampleNames) {
            this.distinctCount = distinctCount;
            this.sampleNames = sampleNames;
        }
    }

    /**
     * Names (skill/tool) are short, so a much wider sample than MAX_ENDPOINTS_FOR_CONTEXT can be
     * afforded here just to count and name them - the 15-endpoint context used elsewhere made a
     * 500+ skill/tool collection's description read as if it were about only the couple of items that
     * happened to be sampled.
     */
    private NamedItemLibrary sampleNamedItemLibrary(int collectionId, Pattern namePattern) {
        List<ApiInfo> wideSample = ApiInfoDao.instance.findAll(
            Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId),
            0, MAX_LIBRARY_SAMPLE_QUERY, Sorts.descending(ApiInfo.LAST_SEEN),
            Projections.include(ApiInfo.ID_URL)
        );

        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ApiInfo apiInfo : wideSample) {
            String name = extractName(namePattern, apiInfo.getId().getUrl());
            if (name != null) {
                names.add(name);
            }
        }

        List<String> sample = new ArrayList<>();
        for (String name : names) {
            if (sample.size() >= MAX_LIBRARY_NAMES_IN_PROMPT) {
                break;
            }
            sample.add(name);
        }
        return new NamedItemLibrary(names.size(), sample);
    }

    /**
     * Plain AI agent/LLM endpoints have no extractable name, so breadth-awareness here is just a total
     * count vs. the sampled MAX_ENDPOINTS_FOR_CONTEXT - cheap count query, no need to fetch the URLs.
     */
    private long countEndpoints(int collectionId) {
        return ApiInfoDao.instance.count(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId));
    }

    static String extractName(Pattern pattern, String url) {
        if (url == null) {
            return null;
        }
        Matcher m = pattern.matcher(url);
        return m.find() ? m.group(1) : null;
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
            boolean hasEndpoints = apiInfos != null && !apiInfos.isEmpty();

            String collectionType = collectionTypeLabel(collection);

            if (!hasEndpoints && collectionType == null) {
                // Genuinely nothing to go on - no traffic yet, and tags don't even say what kind of
                // collection this is. Not a failure, just not enough info yet - don't count it toward
                // MAX_FAILED_ATTEMPTS (see markFailed), or a collection that starts like this and later
                // gets real endpoints/tags would already have burned its retry budget by the time it
                // actually has something to describe.
                loggerMaker.debugAndAddToDb("Skipping collection " + collectionId + ": no endpoints and no recognized type yet");
                return;
            }

            // Skill/MCP-tool collections can have hundreds of distinct items named right in the URL;
            // plain AI agent/LLM endpoints have no such name, but can still have more endpoints than the
            // MAX_ENDPOINTS_FOR_CONTEXT sample shows. Either way, a narrow sample presented as if it were
            // the whole collection reads as "this is about the 1-2 things I happened to see" - wrong.
            Pattern namePattern = "Skill".equals(collectionType) ? SKILL_NAME_PATTERN
                : "MCP server".equals(collectionType) ? MCP_TOOL_NAME_PATTERN
                : null;

            List<String> endpointsForPrompt = hasEndpoints ? endpointStrings(apiInfos) : null;
            int itemLibrarySize = 0;
            String itemWord = null;

            if (hasEndpoints && namePattern != null) {
                NamedItemLibrary library = sampleNamedItemLibrary(collectionId, namePattern);
                if (library.distinctCount > 1) {
                    itemLibrarySize = library.distinctCount;
                    itemWord = "Skill".equals(collectionType) ? "skill" : "tool";
                    endpointsForPrompt = library.sampleNames;
                }
            } else if (hasEndpoints && ("AI agent".equals(collectionType) || "LLM".equals(collectionType))) {
                long totalEndpoints = countEndpoints(collectionId);
                if (totalEndpoints > apiInfos.size()) {
                    itemLibrarySize = (int) totalEndpoints;
                    itemWord = "endpoint";
                }
            }
            boolean isLibrary = itemLibrarySize > 1;

            BasicDBObject queryData = new BasicDBObject();
            queryData.put(CollectionDescriptionPromptHandler.COLLECTION_NAME, collection.getName());
            queryData.put(CollectionDescriptionPromptHandler.HOST_NAME, collection.getHostName());
            queryData.put(CollectionDescriptionPromptHandler.ACCESS_TYPE, collection.getAccessType());
            queryData.put(CollectionDescriptionPromptHandler.COLLECTION_TYPE, collectionType);
            // A single-skill collection's "skill" tag names the point of the description; a library with
            // many items has no one name that represents the whole thing, so it's left unset there.
            queryData.put(CollectionDescriptionPromptHandler.SKILL_NAME, isLibrary ? null : skillTagValue(collection));
            queryData.put(CollectionDescriptionPromptHandler.TAGS, tagStrings(collection));
            queryData.put(CollectionDescriptionPromptHandler.ENDPOINTS, endpointsForPrompt);
            queryData.put(CollectionDescriptionPromptHandler.ITEM_LIBRARY_SIZE, itemLibrarySize);
            queryData.put(CollectionDescriptionPromptHandler.ITEM_WORD, itemWord);
            queryData.put(CollectionDescriptionPromptHandler.SAMPLE_SNIPPETS,
                hasEndpoints ? sampleSnippets(collectionId, apiInfos) : null);
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
            // Best-effort provenance trail so a bad batch can be found/audited later - this cron never
            // overwrites a description that was already set, so this only ever fires for new ones.
            loggerMaker.infoAndAddToDb("Set description for collectionId=" + collectionId
                + ", accountId=" + accountId + ": " + description);
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
