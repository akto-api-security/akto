package com.akto.utils.crons;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.agentic_sessions.QueryTopicCacheDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.agentic_sessions.QueryTopicCache;
import com.akto.gpt.handlers.gpt_prompts.UserQueryTopicClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class UserAnalysisCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UserAnalysisCron.class, LogDb.DASHBOARD);

    private static final int CRON_INTERVAL_MINUTES       = 10;
    private static final int SLACK_SECONDS               = 60;
    private static final int PAGE_SIZE                   = 10;
    private static final int MAX_DOCS_PER_ACCOUNT_PER_TICK = 5500;
    private static final int MIN_QUERY_LENGTH            = 20;
    private static final int BATCH_SIZE                  = 10;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpUserAnalysisCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (!SearchClientFactory.instance().isConfigured()) {
                    loggerMaker.error("UserAnalysisCron: search backend not configured, skipping tick.");
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        try {
                            loggerMaker.infoAndAddToDb("Starting user analysis cron for account " + account.getId());
                            processAccount(account.getId());
                        } catch (Exception e) {
                            loggerMaker.error("UserAnalysisCron: failure for account "
                                + account.getId() + ": " + e.getMessage());
                        }
                    }
                }, "user-analysis-cron-info");
            }
        }, 0, CRON_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void processAccount(int accountId) {
        Context.accountId.set(accountId);
        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        long nowMs = System.currentTimeMillis();
        long endTs = nowMs - SLACK_SECONDS * 1000L;
        long startTs = resolveStartTs(settings);

        UserQueryTopicClassifier classifier = new UserQueryTopicClassifier();

        // Collect unprocessed records (scrollQueryData already filters topicProcessed=true)
        List<AgentQueryRecord> records = new ArrayList<>();
        SearchClientFactory.instance().scrollQueryData(accountId, startTs, endTs, PAGE_SIZE,
            MAX_DOCS_PER_ACCOUNT_PER_TICK, records::add);

        loggerMaker.info("Fetch query records count: " + records.size());

        // Filter: drop tool-use spans and short/empty records
        List<AgentQueryRecord> llmRecords = new ArrayList<>();
        for (AgentQueryRecord rec : records) {
            if (rec.getServiceId() == null || rec.getServiceId().isEmpty()) continue;
            if (rec.getDeviceId() == null || rec.getDeviceId().isEmpty()) continue;
            if (rec.getQueryPayload() == null || rec.getQueryPayload().length() < MIN_QUERY_LENGTH) continue;
            if (!isToolUseRecord(rec)) llmRecords.add(rec);
        }
        loggerMaker.info("Filtered query records count left: " + llmRecords.size());

        if (!llmRecords.isEmpty()) {
            classifyAndPersist(llmRecords, accountId, classifier);
        }

        // If the fetch was capped, advance the cursor only to the last fetched record's
        // timestamp so the next tick resumes mid-window rather than skipping unprocessed records.
        long cursorTs = endTs;
        if (records.size() >= MAX_DOCS_PER_ACCOUNT_PER_TICK) {
            cursorTs = records.get(records.size() - 1).getTimeStampMs();
        }

        AccountSettingsDao.instance.updateOne(
            Filters.eq(Constants.ID, accountId),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_USER_ANALYSIS_CRON, (cursorTs / 1000))
        );
    }

    private void classifyAndPersist(List<AgentQueryRecord> llmRecords, int accountId,
                                    UserQueryTopicClassifier classifier) {
        // Step 1: Compute hash per record and bulk-check the topic cache
        Map<AgentQueryRecord, String> recToHash = new LinkedHashMap<>();
        Set<String> allHashes = new LinkedHashSet<>();
        for (AgentQueryRecord rec : llmRecords) {
            String hash = md5Hash(rec.getQueryPayload());
            recToHash.put(rec, hash);
            allHashes.add(hash);
        }

        Map<String, QueryTopicCache> cacheHits;
        try {
            cacheHits = QueryTopicCacheDao.instance.bulkGet(allHashes);
        } catch (Exception e) {
            loggerMaker.error("UserAnalysisCron: cache lookup failed, treating all as misses: " + e.getMessage());
            cacheHits = new HashMap<>();
        }

        // Step 2: Batch-classify cache misses
        List<AgentQueryRecord> cacheMisses = new ArrayList<>();
        for (AgentQueryRecord rec : llmRecords) {
            if (!cacheHits.containsKey(recToHash.get(rec))) cacheMisses.add(rec);
        }

        Map<String, BasicDBObject> newClassifications = new HashMap<>();
        List<QueryTopicCache> newCacheEntries = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 0; i < cacheMisses.size(); i += BATCH_SIZE) {
            List<AgentQueryRecord> batch = cacheMisses.subList(i, Math.min(i + BATCH_SIZE, cacheMisses.size()));
            List<BasicDBObject> inputs = new ArrayList<>();
            for (AgentQueryRecord rec : batch) {
                inputs.add(new BasicDBObject()
                    .append(UserQueryTopicClassifier.QUERY_PAYLOAD, rec.getQueryPayload())
                    .append(UserQueryTopicClassifier.RESPONSE_PAYLOAD,
                        rec.getResponsePayload() != null ? rec.getResponsePayload() : ""));
            }
            List<BasicDBObject> results;
            try {
                results = classifier.handleBatch(inputs);
            } catch (Exception e) {
                loggerMaker.error("UserAnalysisCron: handleBatch error for accountId " + accountId + ": " + e.getMessage());
                continue;
            }
            if (results == null) continue;

            for (int j = 0; j < batch.size() && j < results.size(); j++) {
                BasicDBObject result = results.get(j);
                if (result == null || result.containsKey("error")) continue;
                String hash = recToHash.get(batch.get(j));
                newClassifications.put(hash, result);

                newCacheEntries.add(new QueryTopicCache(
                    hash,
                    getDomain(result),
                    getSubDomain(result),
                    result.getBoolean("harmful", false),
                    result.getString("harmfulCategory", ""),
                    result.getString("harmfulReason", ""),
                    now
                ));
            }
        }

        // Step 3: Persist new cache entries
        try {
            QueryTopicCacheDao.instance.bulkPut(newCacheEntries);
        } catch (Exception e) {
            loggerMaker.error("UserAnalysisCron: cache store failed: " + e.getMessage());
        }

        // Step 4: Accumulate per-record results into per-AggregateKey structures
        List<SearchClient.TopicUpdate> topicUpdates = new ArrayList<>();
        // domain → (subDomain → count) per aggregate key — mirrors topicHierarchy in MongoDB
        Map<AggregateKey, Map<String, Map<String, Integer>>> aggTopicHierarchy = new HashMap<>();
        Map<AggregateKey, Map<String, Object>> aggHarmfulMerge = new HashMap<>();
        Map<AggregateKey, long[]>              aggTokens        = new HashMap<>();
        Map<AggregateKey, String>              aggUserNames     = new HashMap<>();
        Map<AggregateKey, String>              aggSummaries     = new HashMap<>();

        for (AgentQueryRecord rec : llmRecords) {
            String hash = recToHash.get(rec);
            BasicDBObject result;
            QueryTopicCache hit = cacheHits.get(hash);
            if (hit != null) {
                result = new BasicDBObject()
                    .append("domain",          hit.getDomain())
                    .append("subDomain",        hit.getSubDomain())
                    .append("harmful",          hit.isHarmful())
                    .append("harmfulCategory",  hit.getHarmfulCategory())
                    .append("harmfulReason",    hit.getHarmfulReason());
            } else {
                result = newClassifications.get(hash);
            }
            if (result == null) continue;

            AggregateKey key = new AggregateKey(rec.getServiceId(), rec.getDeviceId());

            // Topic write-back: domain + subDomain for this record
            String domain    = getDomain(result);
            String subDomain = getSubDomain(result);
            if (!domain.isEmpty() && rec.getDocId() != null && !rec.getDocId().isEmpty()) {
                topicUpdates.add(new SearchClient.TopicUpdate(rec.getDocId(), domain, subDomain, rec.getTimeStampMs()));
            }

            // Build topic hierarchy: domain → subDomain → count
            if (!domain.isEmpty() && !subDomain.isEmpty()) {
                Map<String, Map<String, Integer>> hierarchy = aggTopicHierarchy.computeIfAbsent(key, k -> new HashMap<>());
                hierarchy.computeIfAbsent(domain, d -> new HashMap<>()).merge(subDomain, 1, Integer::sum);
            }

            // Harmful merge
            if (result.getBoolean("harmful", false)) {
                Map<String, Object> harmfulMerge = aggHarmfulMerge.computeIfAbsent(key, k -> new HashMap<>());
                applyHarmful(result, rec.getTimeStampMs(), harmfulMerge);
            }

            // Token accumulation
            long inTok = rec.getInputTokens() > 0 ? rec.getInputTokens() : rec.getQueryPayload().length() / 4;
            long outTok = rec.getOutputTokens() > 0 ? rec.getOutputTokens()
                : (rec.getResponsePayload() != null ? rec.getResponsePayload().length() / 4 : 0);
            long[] tokens = aggTokens.computeIfAbsent(key, k -> new long[]{0, 0});
            tokens[0] += inTok;
            tokens[1] += outTok;

            // Track last userName and summary per key (cache hits have no summary)
            if (rec.getUserName() != null && !rec.getUserName().isEmpty()) {
                aggUserNames.put(key, rec.getUserName());
            } else {
                aggUserNames.putIfAbsent(key, "");
            }
            if (hit == null) {
                // Summary only comes from fresh AI classifications, not cache hits
                String summary = result.getString("summary", "");
                if (summary != null && !summary.isEmpty()) {
                    aggSummaries.put(key, summary);
                }
            }
        }

        // Step 5: Bulk write topics back
        if (!topicUpdates.isEmpty()) {
            loggerMaker.infoAndAddToDb("Bulk updates for topics: " + topicUpdates.size());
            try {
                SearchClientFactory.instance().bulkUpdateTopics(topicUpdates);
            } catch (Exception e) {
                loggerMaker.error("UserAnalysisCron: bulkUpdateTopics failed for accountId " + accountId + ": " + e.getMessage());
            }
        }

        // Step 6: One upsertAggregates per unique AggregateKey
        for (AggregateKey key : aggTokens.keySet()) {
            long[] tokens = aggTokens.get(key);
            // null summary means all-cache-hit; upsertAggregates will skip overwriting existing summary
            String summaryToWrite = aggSummaries.get(key);
            try {
                UserAnalysisDataDao.instance.upsertAggregates(
                    key.serviceId, key.deviceId,
                    aggUserNames.getOrDefault(key, ""),
                    aggTopicHierarchy.getOrDefault(key, new HashMap<>()),
                    tokens[0], tokens[1],
                    aggHarmfulMerge.getOrDefault(key, new HashMap<>()),
                    summaryToWrite,
                    System.currentTimeMillis()
                );
            } catch (Exception e) {
                loggerMaker.error("UserAnalysisCron: upsert failed for (" + key.serviceId + ", " + key.deviceId + "): " + e.getMessage());
            }
        }
    }

    public static long resolveStartTs(AccountSettings settings) {
        if (settings == null) return 0;
        LastCronRunInfo info = settings.getLastUpdatedCronInfo();
        if (info == null) return 0;
        return info.getLastUserAnalysisCron() * 1000L;
    }

    /**
     * Returns true for tool-execution spans (Bash, Read, Edit, MCP, etc.) that carry
     * no classifiable topic. Mirrors the classifySpan() logic in constants.js on the frontend.
     */
    private boolean isToolUseRecord(AgentQueryRecord rec) {
        // LLM records always have token counts — fast exit
        if (rec.getInputTokens() > 0 || rec.getOutputTokens() > 0) return false;

        // Zero tokens: likely a tool span. Confirm via payload structure.
        try {
            BasicDBObject obj = BasicDBObject.parse(rec.getQueryPayload());
            if (obj.containsKey("toolName")) return true;
            Object bodyObj = obj.get("body");
            if (bodyObj instanceof BasicDBObject) {
                BasicDBObject body = (BasicDBObject) bodyObj;
                if (body.containsKey("toolName")) return true;
                if (body.containsKey("result")) return true;
            }
            Object content = obj.get("content");
            if (content instanceof List) {
                for (Object c : (List<?>) content) {
                    if (c instanceof BasicDBObject
                        && "tool_use".equals(((BasicDBObject) c).getString("type"))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String getDomain(BasicDBObject result) {
        String d = result.getString("domain", "");
        return d != null ? d.trim() : "";
    }

    private static String getSubDomain(BasicDBObject result) {
        String s = result.getString("subDomain", "");
        return s != null ? s.trim() : "";
    }

    private void applyHarmful(BasicDBObject result, long timestampMs, Map<String, Object> harmfulMerge) {
        String category = result.getString("harmfulCategory", "general");
        if (category == null || category.isEmpty()) category = "general";
        String reason = result.getString("harmfulReason", "");

        BasicDBObject entry = (BasicDBObject) harmfulMerge.get(category);
        if (entry == null) {
            entry = new BasicDBObject()
                .append("count", 0)
                .append("lastSeenAt", timestampMs)
                .append("lastReason", reason);
            harmfulMerge.put(category, entry);
        }
        entry.put("count", entry.getInt("count", 0) + 1);
        entry.put("lastSeenAt", timestampMs);
        if (reason != null && !reason.isEmpty()) entry.put("lastReason", reason);
    }

    private static String md5Hash(String input) {
        if (input == null) return "";
        try {
            String normalized = input.trim().toLowerCase();
            String prefix = normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(prefix.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private static final class AggregateKey {
        final String serviceId;
        final String deviceId;

        AggregateKey(String serviceId, String deviceId) {
            this.serviceId = serviceId;
            this.deviceId = deviceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AggregateKey)) return false;
            AggregateKey that = (AggregateKey) o;
            return Objects.equals(serviceId, that.serviceId) && Objects.equals(deviceId, that.deviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceId, deviceId);
        }
    }
}
