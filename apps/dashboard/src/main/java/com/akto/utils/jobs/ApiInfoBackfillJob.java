package com.akto.utils.jobs;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.MCollection;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomAuthType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.util.AccountTask;
import com.akto.util.Constants;

import static com.akto.runtime.utils.Utils.parseCookie;
import com.akto.runtime.policies.AuthPolicy;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Endpoints show up in the inventory from single_type_info, joined against api_info for auth type.
 * An endpoint with no api_info doc therefore has no auth type at all, which reads the same as an
 * unauthenticated API.
 *
 * This job finds those endpoints and rebuilds their ApiInfo from sample_data using the same
 * AuthPolicy the runtime uses, then inserts them into api_info. Writes are $setOnInsert only, so an
 * api_info doc that already exists is never modified.
 *
 * Endpoints discovered within BACKFILL_MIN_AGE_DAYS are left alone: the runtime may still be in the
 * middle of writing their api_info, and if it is failing to, that is a live bug worth seeing rather
 * than papering over.
 *
 * Shape follows InitializerListener.backFillDiscovered: keyset pagination on _id, no skip, one
 * bulkWrite per batch. In steady state every batch short-circuits after two projection-only reads,
 * so the parsing half only ever runs for endpoints that are genuinely missing.
 */
public class ApiInfoBackfillJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiInfoBackfillJob.class, LogDb.DASHBOARD);

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final String ENABLED_ACCOUNTS_ENV = "API_INFO_BACKFILL_ACCOUNTS";

    // sample_data keys pulled per cursor page. also bounds every $in below, since a page is
    // sorted by apiCollectionId first and so is almost always a single collection
    private static final int SAMPLE_BATCH_SIZE = 1000;
    // smaller batch only where full sample bodies are pulled, to cap how much payload one query returns
    private static final int SAMPLE_FETCH_BATCH_SIZE = 100;
    // endpoints discovered more recently than this are left to the runtime
    private static final int BACKFILL_MIN_AGE_DAYS = 7;

    private static final Bson SAMPLE_SORT = Sorts.ascending("_id.apiCollectionId", "_id.url", "_id.method");

    public static void apiInfoBackfillScheduler() {
        final List<Integer> enabledAccounts = parseEnabledAccounts();
        if (enabledAccounts.isEmpty()) {
            loggerMaker.infoAndAddToDb(ENABLED_ACCOUNTS_ENV + " not set, api info backfill job disabled");
            return;
        }

        loggerMaker.infoAndAddToDb("Scheduling api info backfill job for accounts: " + enabledAccounts);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        if (!enabledAccounts.contains(account.getId())) return;
                        try {
                            backfillForAccount();
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in api info backfill job for account "
                                    + account.getId() + ": " + e.getMessage());
                        }
                    }
                }, "api-info-backfill-job");
            }
        }, 0, 3, TimeUnit.HOURS);
    }

    private static List<Integer> parseEnabledAccounts() {
        List<Integer> accounts = new ArrayList<>();
        String raw = System.getenv(ENABLED_ACCOUNTS_ENV);
        if (raw == null || raw.trim().isEmpty()) return accounts;

        for (String token: raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                accounts.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                loggerMaker.errorAndAddToDb("Ignoring bad account id in " + ENABLED_ACCOUNTS_ENV + ": " + trimmed);
            }
        }
        return accounts;
    }

    /**
     * Streams every sample_data key for the account once, staging an ApiInfo for each endpoint that
     * has neither an api_info doc nor an already staged one. Runs to completion.
     */
    public static void backfillForAccount() {
        int accountId = Context.accountId.get();
        int startTime = Context.now();
        loggerMaker.warnAndAddToDb("Starting api info backfill for account " + accountId);

        List<CustomAuthType> customAuthTypes;
        try {
            customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE, true);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Api info backfill: could not read custom auth types, "
                    + "continuing without them: " + e.getMessage());
            customAuthTypes = new ArrayList<>();
        }
        if (customAuthTypes == null) customAuthTypes = new ArrayList<>();
        loggerMaker.warn("Api info backfill: loaded " + customAuthTypes.size() + " custom auth types");

        int scanned = 0;
        int staged = 0;
        int pageNumber = 0;
        int skippedUnknownMethod = 0;
        BasicDBObject cursor = null;

        while (true) {
            pageNumber++;
            long fetchStart = System.currentTimeMillis();
            // raw documents, not the SampleData pojo: a stored method the Method enum does not know
            // (we have seen "DEBUG") makes the pojo codec throw for the whole page, which would take
            // out the run and leave the cursor unable to advance past the offending doc
            List<BasicDBObject> page = findRaw(SampleDataDao.instance.getMCollection(),
                    cursorFilter(cursor), Projections.include(Constants.ID), SAMPLE_SORT, SAMPLE_BATCH_SIZE);
            loggerMaker.warn("Api info backfill: fetched page " + pageNumber
                    + " after " + cursorLabel(cursor)
                    + " size=" + page.size()
                    + " fetchMs=" + (System.currentTimeMillis() - fetchStart));

            if (page.isEmpty()) {
                loggerMaker.warn("Api info backfill: page " + pageNumber
                        + " came back empty, sample_data exhausted");
                break;
            }

            cursor = idOf(page.get(page.size() - 1));
            scanned += page.size();

            try {
                // millis, not Context.now() seconds — a page that short-circuits takes far under a
                // second and would otherwise always log 0
                long pageStart = System.currentTimeMillis();
                int before = unknownMethodCount;
                int stagedInPage = processPage(page, customAuthTypes);
                staged += stagedInPage;
                skippedUnknownMethod += (unknownMethodCount - before);
                loggerMaker.warn("Api info backfill: processed page " + pageNumber
                        + " for account " + accountId
                        + " pageSize=" + page.size() + " stagedInPage=" + stagedInPage
                        + " tookMs=" + (System.currentTimeMillis() - pageStart)
                        + " scannedSoFar=" + scanned + " stagedSoFar=" + staged
                        + " elapsedSeconds=" + (Context.now() - startTime)
                        + " cursorAt=" + cursorLabel(cursor));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error processing api info backfill page " + pageNumber
                        + " ending at " + cursorLabel(cursor) + ": " + e.getMessage());
            }

            if (page.size() < SAMPLE_BATCH_SIZE) {
                loggerMaker.warn("Api info backfill: page " + pageNumber + " was short ("
                        + page.size() + " < " + SAMPLE_BATCH_SIZE + "), sample_data exhausted");
                break;
            }
        }

        loggerMaker.warnAndAddToDb("Finished api info backfill for account " + accountId
                + ", pages=" + pageNumber + " scanned=" + scanned + " staged=" + staged
                + " skippedUnknownMethod=" + skippedUnknownMethod
                + " in " + (Context.now() - startTime) + " seconds");
    }

    /** Where the scan has reached, for logs — collection, method and url of the last key seen. */
    private static String cursorLabel(BasicDBObject lastId) {
        if (lastId == null) return "start";
        return lastId.getInt("apiCollectionId") + " " + lastId.getString("method")
                + " " + lastId.getString("url");
    }

    private static Bson cursorFilter(BasicDBObject lastId) {
        if (lastId == null) return Filters.empty();

        // read straight off the raw _id so pagination still advances when the last doc on a page
        // carries a method the enum cannot hold
        int apiCollectionId = lastId.getInt("apiCollectionId");
        String url = lastId.getString("url", "");
        String method = lastId.getString("method", "");

        return Filters.or(
                Filters.gt("_id.apiCollectionId", apiCollectionId),
                Filters.and(
                        Filters.eq("_id.apiCollectionId", apiCollectionId),
                        Filters.gt("_id.url", url)
                ),
                Filters.and(
                        Filters.eq("_id.apiCollectionId", apiCollectionId),
                        Filters.eq("_id.url", url),
                        Filters.gt("_id.method", method)
                )
        );
    }

    private static int processPage(List<BasicDBObject> page, List<CustomAuthType> customAuthTypes) {
        Map<Integer, List<ApiInfoKey>> keysByCollection = new HashMap<>();
        for (BasicDBObject doc: page) {
            ApiInfoKey key = toApiInfoKey(idOf(doc));
            if (key == null) continue;
            keysByCollection.computeIfAbsent(key.getApiCollectionId(), k -> new ArrayList<>()).add(key);
        }

        int staged = 0;
        for (Map.Entry<Integer, List<ApiInfoKey>> entry: keysByCollection.entrySet()) {
            int apiCollectionId = entry.getKey();

            // start from every sample-backed endpoint in this page, then subtract the ones already
            // accounted for. one query per collection strips each set, never one per url.
            Set<ApiInfoKey> missing = new HashSet<>(entry.getValue());
            int candidates = missing.size();

            missing.removeAll(keysPresentIn(ApiInfoDao.instance, apiCollectionId, missing));
            int notInApiInfo = missing.size();

            // an endpoint the inventory does not list is not something the ui can show, so there is
            // nothing to fix for it — skip rather than write a record for an api that is not there
            Map<ApiInfoKey, int[]> timestamps = missing.isEmpty()
                    ? Collections.<ApiInfoKey, int[]>emptyMap()
                    : stiTimestamps(missing, apiCollectionId);
            int listedInInventory = timestamps.size();

            // leave anything discovered in the last few days to the runtime
            timestamps = dropRecentlyDiscovered(timestamps);
            int oldEnough = timestamps.size();

            List<ApiInfo> built = timestamps.isEmpty()
                    ? Collections.<ApiInfo>emptyList()
                    : buildFromSamples(timestamps, apiCollectionId, customAuthTypes);

            int written = writeToApiInfo(built);
            staged += written;

            // the funnel, so a zero at the end says which stage dropped everything rather than
            // leaving you to guess. each stage below the first only queries when the prior one
            // left something, so this costs nothing when there is no work.
            loggerMaker.warn("Api info backfill collection " + apiCollectionId
                    + ": candidates=" + candidates
                    + " notInApiInfo=" + notInApiInfo
                    + " listedInInventory=" + listedInInventory
                    + " oldEnough=" + oldEnough
                    + " builtFromSamples=" + built.size()
                    + " written=" + written);
        }
        return staged;
    }

    /**
     * Which of these keys already have a document in the given collection.
     *
     * A single $in over the whole key set — the caller's set is already bounded by SAMPLE_BATCH_SIZE,
     * so this is one round trip per collection, never one per url.
     *
     * Deliberately filters on url only. Adding an $in on method would explode the index bounds into
     * the url x method cross product while excluding almost nothing, since a page of this size
     * contains nearly every method anyway — and it still would not give exact pairs, so the key
     * match below has to happen either way.
     */
    private static Set<ApiInfoKey> keysPresentIn(MCollection<ApiInfo> collection, int apiCollectionId,
                                                 Set<ApiInfoKey> keys) {
        Set<ApiInfoKey> present = new HashSet<>();
        if (keys.isEmpty()) return present;

        Bson filter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.in("_id.url", urlsOf(keys))
        );

        // the $in is on url alone, so this also returns other methods on the same url — confirm the
        // whole key, method included, before treating it as present
        for (BasicDBObject doc: findRaw(collection.getMCollection(), filter,
                Projections.include(Constants.ID), null, 0)) {
            ApiInfoKey key = toApiInfoKey(idOf(doc));
            if (key != null && keys.contains(key)) {
                present.add(key);
            }
        }
        return present;
    }

    /**
     * Gate plus timestamps in one read: keeps only keys the inventory actually lists, and returns
     * {discoveredTimestamp, lastSeen} from their sti rows so the staged doc does not land with a
     * zeroed lastSeen.
     *
     * "In the inventory" means a host header row, not merely any sti row — reusing
     * SingleTypeInfoDao.filterForHostHostHeaderRaw(), the same predicate the endpoints table is
     * built from. An endpoint with leftover param rows but no host row is not listed in the ui, so
     * there is nothing there for us to fix.
     */
    private static Map<ApiInfoKey, int[]> stiTimestamps(Set<ApiInfoKey> keys, int apiCollectionId) {
        Map<ApiInfoKey, int[]> result = new HashMap<>();
        if (keys.isEmpty()) return result;

        List<Bson> filters = new ArrayList<>(SingleTypeInfoDao.filterForHostHostHeaderRaw());
        filters.add(Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId));
        filters.add(Filters.in(SingleTypeInfo._URL, urlsOf(keys)));
        Bson filter = Filters.and(filters);

        List<SingleTypeInfo> stis = SingleTypeInfoDao.instance.findAll(filter,
                Projections.include(SingleTypeInfo._URL, SingleTypeInfo._METHOD,
                        SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._TIMESTAMP,
                        SingleTypeInfo.LAST_SEEN));

        for (SingleTypeInfo sti: stis) {
            if (sti.getUrl() == null) continue;
            // strict, not Method.fromString — that maps anything unknown onto OTHER, which would
            // silently attach a DEBUG row's timestamps to a genuine OTHER endpoint
            URLMethods.Method method = strictMethod(sti.getMethod());
            if (method == null) continue;
            ApiInfoKey key = new ApiInfoKey(sti.getApiCollectionId(), sti.getUrl(), method);
            if (!keys.contains(key)) continue;

            int timestamp = sti.getTimestamp();
            int lastSeen = (int) Math.max(sti.getLastSeen(), timestamp);

            int[] existing = result.get(key);
            if (existing == null) {
                result.put(key, new int[]{timestamp, lastSeen});
            } else {
                if (timestamp > 0 && (existing[0] == 0 || timestamp < existing[0])) existing[0] = timestamp;
                if (lastSeen > existing[1]) existing[1] = lastSeen;
            }
        }
        return result;
    }

    /**
     * Drops endpoints discovered within BACKFILL_MIN_AGE_DAYS. A freshly discovered endpoint may
     * simply not have had its api_info written yet, and if the runtime is failing to write it that
     * is a live bug — better surfaced than hidden behind a backfilled doc.
     */
    private static Map<ApiInfoKey, int[]> dropRecentlyDiscovered(Map<ApiInfoKey, int[]> timestamps) {
        if (timestamps.isEmpty()) return timestamps;

        int cutoff = Context.now() - BACKFILL_MIN_AGE_DAYS * 24 * 60 * 60;
        Map<ApiInfoKey, int[]> oldEnough = new HashMap<>();
        for (Map.Entry<ApiInfoKey, int[]> e: timestamps.entrySet()) {
            int discovered = e.getValue()[0];
            // discovered == 0 means sti carried no usable timestamp; treat as old, not as new
            if (discovered == 0 || discovered < cutoff) oldEnough.put(e.getKey(), e.getValue());
        }
        return oldEnough;
    }

    /**
     * Second sample_data pass, this time pulling the samples themselves — only for endpoints that
     * survived every check above, so the large documents are never read for the common case.
     */
    private static List<ApiInfo> buildFromSamples(Map<ApiInfoKey, int[]> timestamps, int apiCollectionId,
                                                  List<CustomAuthType> customAuthTypes) {
        Map<ApiInfoKey, ApiInfo> built = new LinkedHashMap<>();
        List<ApiInfoKey> keyList = new ArrayList<>(timestamps.keySet());

        for (int i = 0; i < keyList.size(); i += SAMPLE_FETCH_BATCH_SIZE) {
            List<String> urls = urlSlice(keyList, i);
            Bson filter = Filters.and(
                    Filters.eq("_id.apiCollectionId", apiCollectionId),
                    Filters.in("_id.url", urls)
            );

            List<BasicDBObject> sampleDataList = findRaw(SampleDataDao.instance.getMCollection(),
                    filter, Projections.include(Constants.ID, SampleData.SAMPLES), null, 0);

            for (BasicDBObject doc: sampleDataList) {
                ApiInfoKey key = toApiInfoKey(idOf(doc));
                if (key == null || !timestamps.containsKey(key)) continue;

                ApiInfo apiInfo = built.get(key);
                if (apiInfo == null) {
                    apiInfo = new ApiInfo(key);
                    int[] ts = timestamps.get(key);
                    apiInfo.setDiscoveredTimestamp(ts[0]);
                    apiInfo.setLastSeen(ts[1]);
                }

                if (accumulateAuthTypes(apiInfo, samplesOf(doc), customAuthTypes)) {
                    built.put(key, apiInfo);
                }
            }
        }

        List<ApiInfo> result = new ArrayList<>();
        for (ApiInfo apiInfo: built.values()) {
            apiInfo.calculateActualAuth();
            result.add(apiInfo);
        }
        return result;
    }

    /**
     * $setOnInsert only, so an api_info doc that appeared since the diff above is left exactly as it
     * is — this can add apis, never alter one.
     *
     * Note this cannot be a ReplaceOneModel: an upsert carrying a replacement document requires _id
     * to be specified whole, while ApiInfoDao.getFilter matches on _id sub-paths. Update operators
     * are fine with that — mongo seeds the new _id from the query's equality conditions, which is
     * the same thing ApiInfoBulkUpdate relies on.
     */
    private static int writeToApiInfo(List<ApiInfo> apiInfoList) {
        if (apiInfoList.isEmpty()) return 0;

        List<WriteModel<ApiInfo>> writes = new ArrayList<>();
        for (ApiInfo apiInfo: apiInfoList) {
            List<Bson> subUpdates = new ArrayList<>();
            subUpdates.add(Updates.setOnInsert(ApiInfo.ALL_AUTH_TYPES_FOUND, apiInfo.getAllAuthTypesFound()));
            subUpdates.add(Updates.setOnInsert(ApiInfo.LAST_SEEN, apiInfo.getLastSeen()));
            subUpdates.add(Updates.setOnInsert(ApiInfo.DISCOVERED_TIMESTAMP, apiInfo.getDiscoveredTimestamp()));
            subUpdates.add(Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, apiInfo.getCollectionIds()));
            subUpdates.add(Updates.setOnInsert(ApiInfo.API_ACCESS_TYPES, new HashSet<>()));
            subUpdates.add(Updates.setOnInsert(ApiInfo.VIOLATIONS, new HashMap<>()));

            writes.add(new UpdateOneModel<>(
                    ApiInfoDao.getFilter(apiInfo.getId()),
                    Updates.combine(subUpdates),
                    new UpdateOptions().upsert(true)
            ));
        }

        ApiInfoDao.instance.getMCollection().bulkWrite(writes);
        return writes.size();
    }

    private static final String COOKIE_HEADER = "cookie";

    // AuthPolicy only derives auth types that need a header *value* (bearer, basic, jwt, cookies,
    // custom). These three are decided by the header name alone and are applied on top of it.
    private static final Pattern API_KEY_PATTERN = Pattern.compile(".*(apikey|passkey).*");
    private static final Pattern MTLS_PATTERN = Pattern.compile(".*(clientcert|sslcert|clientdn|sslclientsdn|sslclientverify|forwardedclientcert).*");
    private static final Pattern SESSION_TOKEN_PATTERN = Pattern.compile("(?i)(session[_\\-.]?(id|key|token)?)");

    // visible for testing
    static boolean accumulateAuthTypes(ApiInfo apiInfo, List<String> samples, List<CustomAuthType> customAuthTypes) {
        if (samples == null || samples.isEmpty()) return false;
        if (apiInfo.getAllAuthTypesFound() == null) apiInfo.setAllAuthTypesFound(new HashSet<>());

        boolean found = false;
        for (String sample: samples) {
            if (sample == null || sample.isEmpty()) continue;
            try {
                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                if (httpResponseParams == null || httpResponseParams.getRequestParams() == null) continue;

                // run the shared policy against a throwaway ApiInfo so the resulting set can be
                // augmented before it is interned into apiInfo's Set<Set<..>> — mutating a set
                // already inside a HashSet would corrupt its bucket
                ApiInfo scratch = new ApiInfo(apiInfo.getId());
                scratch.setAllAuthTypesFound(new HashSet<>());
                AuthPolicy.findAuthType(httpResponseParams, scratch, null, customAuthTypes);

                Set<ApiInfo.AuthType> authTypes = new HashSet<>();
                for (Set<ApiInfo.AuthType> fromPolicy: scratch.getAllAuthTypesFound()) {
                    authTypes.addAll(fromPolicy);
                }
                authTypes.addAll(headerNameAuthTypes(httpResponseParams.getRequestParams().getHeaders()));

                if (authTypes.size() > 1) authTypes.remove(ApiInfo.AuthType.UNAUTHENTICATED);
                if (authTypes.isEmpty()) authTypes.add(ApiInfo.AuthType.UNAUTHENTICATED);

                apiInfo.getAllAuthTypesFound().add(authTypes);
                found = true;
            } catch (Exception e) {
                // a single unparseable sample should not drop the endpoint
            }
        }
        return found;
    }

    /**
     * API_KEY, MTLS and SESSION_TOKEN are identified purely by header (or cookie) name, so they can
     * be derived without the value. Kept here rather than in AuthPolicy to leave runtime code alone.
     */
    private static Set<ApiInfo.AuthType> headerNameAuthTypes(Map<String, List<String>> headers) {
        Set<ApiInfo.AuthType> types = new HashSet<>();
        if (headers == null) return types;

        for (Map.Entry<String, List<String>> entry: headers.entrySet()) {
            String header = entry.getKey();
            if (header == null) continue;
            String normalized = header.toLowerCase().replaceAll("[_-]", "");

            if (API_KEY_PATTERN.matcher(normalized).matches()) {
                types.add(ApiInfo.AuthType.API_KEY);
            }
            if (SESSION_TOKEN_PATTERN.matcher(header).find()) {
                types.add(ApiInfo.AuthType.SESSION_TOKEN);
            }
            // an empty forwarded-cert header means no client cert was presented
            if (MTLS_PATTERN.matcher(normalized).matches() && hasNonEmptyValue(entry.getValue())) {
                types.add(ApiInfo.AuthType.MTLS);
            }
        }

        for (String cookieName: parseCookie(headers.getOrDefault(COOKIE_HEADER, new ArrayList<>())).keySet()) {
            if (cookieName != null && SESSION_TOKEN_PATTERN.matcher(cookieName).find()) {
                types.add(ApiInfo.AuthType.SESSION_TOKEN);
            }
        }
        return types;
    }

    private static boolean hasNonEmptyValue(List<String> values) {
        if (values == null) return false;
        for (String value: values) {
            if (value != null && !value.trim().isEmpty()) return true;
        }
        return false;
    }

    private static List<String> urlSlice(List<ApiInfoKey> keys, int from) {
        List<String> urls = new ArrayList<>();
        for (int i = from; i < Math.min(from + SAMPLE_FETCH_BATCH_SIZE, keys.size()); i++) {
            urls.add(keys.get(i).getUrl());
        }
        return urls;
    }

    /** Distinct urls for a key set — several methods can share one url, so dedupe before the $in. */
    private static List<String> urlsOf(Set<ApiInfoKey> keys) {
        Set<String> urls = new HashSet<>();
        for (ApiInfoKey key: keys) {
            urls.add(key.getUrl());
        }
        return new ArrayList<>(urls);
    }

    /** Endpoints whose stored method has no enum constant, counted so the run reports them. */
    private static int unknownMethodCount = 0;

    private static ApiInfoKey toApiInfoKey(BasicDBObject id) {
        if (id == null) return null;
        String url = id.getString("url");
        if (url == null) return null;

        URLMethods.Method method = strictMethod(id.getString("method"));
        if (method == null) {
            // nothing to stage: api_info could not hold this method either
            unknownMethodCount++;
            return null;
        }
        return new ApiInfoKey(id.getInt("apiCollectionId"), url, method);
    }

    /**
     * Method.fromString folds anything unrecognised into OTHER, which would merge a "DEBUG" endpoint
     * into a real OTHER one. This returns null instead so the caller can skip it outright.
     */
    private static URLMethods.Method strictMethod(String method) {
        if (method == null) return null;
        for (URLMethods.Method candidate: URLMethods.Method.getValuesArray()) {
            if (candidate.name().equalsIgnoreCase(method)) return candidate;
        }
        return null;
    }

    private static BasicDBObject idOf(BasicDBObject doc) {
        if (doc == null) return null;
        Object id = doc.get(Constants.ID);
        return id instanceof BasicDBObject ? (BasicDBObject) id : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> samplesOf(BasicDBObject doc) {
        if (doc == null) return new ArrayList<>();
        Object samples = doc.get(SampleData.SAMPLES);
        return samples instanceof List ? (List<String>) samples : new ArrayList<>();
    }

    /**
     * Reads documents without going through the pojo codec, so a field the dto cannot represent
     * cannot take out the whole query. limit <= 0 means unlimited, sort null means unsorted.
     */
    private static <T> List<BasicDBObject> findRaw(com.mongodb.client.MongoCollection<T> collection,
                                                   Bson filter, Bson projection, Bson sort, int limit) {
        List<BasicDBObject> results = new ArrayList<>();
        com.mongodb.client.FindIterable<BasicDBObject> iterable =
                collection.find(filter, BasicDBObject.class).projection(projection);
        if (sort != null) iterable = iterable.sort(sort);
        if (limit > 0) iterable = iterable.limit(limit);

        MongoCursor<BasicDBObject> cursor = iterable.iterator();
        try {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        } finally {
            cursor.close();
        }
        return results;
    }
}
