package com.akto.utils.jobs;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * ApiInfo.discoveredTimestamp is what the api changes table filters on, while the summary cards and
 * the trend chart above it come from single_type_info.timestamp. A doc missing the field is
 * therefore invisible to the table while still being counted by the cards — the page reads as
 * "740 new endpoints" above an empty list.
 *
 * The field is meant to be written once, at insert, by the runtime (AktoPolicyNew sets it,
 * ApiInfoBulkUpdate writes it with $setOnInsert). Docs that already existed before that shipped
 * never receive it, since $setOnInsert cannot fire on an existing doc. This job fills those in from
 * the minimum single_type_info timestamp for the endpoint, which is the same definition the old
 * InitializerListener.backFillDiscovered used.
 *
 * Why it is not that method. backFillDiscovered walked every sti doc in the account to derive one
 * int per endpoint, paging 100_000 pojos at a time, and paged by _id — insertion order, not endpoint
 * order — so an endpoint's params were spread across many pages and it was re-written once per page
 * it appeared in, two UpdateOneModels each time. On a large account that is tens of millions of
 * reads and heap it cannot hold, which is why it ended up commented out.
 *
 * This inverts the driver: page the *broken* api_info docs, look up only their sti rows, write each
 * doc once. Cost is proportional to what is actually broken rather than to the size of the account,
 * and in steady state a run costs one index seek that returns nothing.
 *
 * No checkpoint is stored. Every doc in a page is written — the real min, or a fallback when the
 * endpoint has no sti rows left — so the broken set shrinks monotonically and the filter is its own
 * cursor across runs. Dropping the fallback would break that: unfixable docs would come back on
 * every run and eventually consume the whole budget.
 */
public class DiscoveredTimestampBackfillJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DiscoveredTimestampBackfillJob.class, LogDb.DASHBOARD);

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** On by default: every account is drained. Set to "false" to switch the job off entirely. */
    private static final String ENABLED_ENV = "DISCOVERED_TS_BACKFILL_ENABLED";
    private static final String LIMIT_ENV = "DISCOVERED_TS_BACKFILL_LIMIT";

    /** Endpoints fixed per account per run. Throughput is this times the runs per day, so raising
     *  the cadence is as good as raising this and costs a smaller burst of oplog each time. */
    private static final int DEFAULT_LIMIT = 10_000;

    /** Broken docs read per page. Grouped by collection, then chunked into URL_BATCH-sized $ins. */
    private static final int PAGE_SIZE = 1_000;

    /** Urls per $in. Bounds one aggregation: at ~12 params per endpoint this is a few thousand sti
     *  rows, and it keeps collections with very wide endpoints (we have seen 500+ params on one url)
     *  from turning a page into a six figure scan. */
    private static final int URL_BATCH = 300;

    private static final int BULK_WRITE_BATCH = 1_000;

    /** Paced so a run cannot push replication lag on the primary it shares with live traffic. */
    private static final long SLEEP_BETWEEN_WRITES_MS = 100;

    /** Wall clock cap per account, so one pathological collection cannot hold the account loop
     *  past the schedule interval. */
    private static final int MAX_RUN_SECONDS = 600;

    /**
     * Absent or zero. Both need repair, and they are not the same query: mongo type brackets range
     * operators, so {$lte: 0} matches only the numeric zeros and skips every doc where the field is
     * absent — which is the overwhelming majority of them. {field: null} is what matches missing.
     */
    private static final Bson BROKEN = Filters.or(
            Filters.eq(ApiInfo.DISCOVERED_TIMESTAMP, null),
            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0));

    public static void discoveredTimestampBackfillScheduler() {
        if (!isEnabled()) {
            loggerMaker.infoAndAddToDb(ENABLED_ENV + "=false, discovered timestamp backfill job disabled");
            return;
        }

        loggerMaker.infoAndAddToDb("Scheduling discovered timestamp backfill job for all accounts"
                + " limitPerAccount=" + limitPerRun());

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                int cycleStart = Context.now();
                // AccountTask runs the consumer per account, so cycle totals have to accumulate in
                // something the anonymous class can reach
                AtomicInteger accountsRun = new AtomicInteger();
                AtomicInteger accountsFailed = new AtomicInteger();
                AtomicInteger totalFixed = new AtomicInteger();

                loggerMaker.warnAndAddToDb("Discovered timestamp backfill CYCLE START at=" + cycleStart
                        + " limitPerAccount=" + limitPerRun());

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        accountsRun.incrementAndGet();
                        try {
                            totalFixed.addAndGet(backfillForAccount());
                        } catch (Exception e) {
                            accountsFailed.incrementAndGet();
                            loggerMaker.errorAndAddToDb(e, "Error in discovered timestamp backfill for account "
                                    + account.getId() + ": " + e.getMessage());
                        }
                    }
                }, "discovered-timestamp-backfill-job");

                loggerMaker.warnAndAddToDb("Discovered timestamp backfill CYCLE END at=" + Context.now()
                        + " durationSeconds=" + (Context.now() - cycleStart)
                        + " accountsRun=" + accountsRun.get()
                        + " accountsFailed=" + accountsFailed.get()
                        + " totalFixed=" + totalFixed.get());
            }
        }, 0, 3, TimeUnit.HOURS);
    }

    /** @return endpoints fixed, so the scheduler can total a cycle across accounts. */
    public static int backfillForAccount() {
        int accountId = Context.accountId.get();
        int limit = limitPerRun();

        RunStats stats = new RunStats();
        stats.pickedAt = Context.now();

        // deliberately an existence check and not a count: counting the broken set is an index scan
        // over every broken key, which on a large account costs more than the work this run will do
        boolean hasWork = !findRaw(BROKEN, Projections.include(Constants.ID), 1).isEmpty();
        if (!hasWork) {
            // the job runs for every account, and most of them are drained. logging this to the db
            // would write a row per account per cycle forever, so the quiet case stays local.
            loggerMaker.info("Discovered timestamp backfill PICKED account=" + accountId
                    + " at=" + stats.pickedAt + " hasWork=false, nothing to do");
            return 0;
        }

        loggerMaker.warnAndAddToDb("Discovered timestamp backfill PICKED account=" + accountId
                + " at=" + stats.pickedAt + " limit=" + limit + " hasWork=true");

        while (stats.fixed < limit) {
            if (Context.now() - stats.pickedAt > MAX_RUN_SECONDS) {
                stats.stopReason = "timeCap";
                break;
            }

            stats.pages++;
            int pageSize = Math.min(PAGE_SIZE, limit - stats.fixed);

            // no sort. the filter is the cursor: every doc read here is written before the next
            // page, so it cannot come back. a sort on lastSeen would add a blocking sort stage for
            // no plan benefit, and blocking sorts on find() die at the 32mb limit.
            List<BasicDBObject> page = findRaw(BROKEN,
                    Projections.include(Constants.ID, ApiInfo.LAST_SEEN), pageSize);

            if (page.isEmpty()) {
                stats.pages--;
                stats.stopReason = "drained";
                break;
            }
            stats.docsRead += page.size();

            int fixedBefore = stats.fixed;
            processPage(page, stats);
            int written = stats.fixed - fixedBefore;

            loggerMaker.warn("Discovered timestamp backfill account=" + accountId
                    + " page=" + stats.pages + " read=" + page.size() + " modified=" + written
                    + " fixedSoFar=" + stats.fixed + " elapsedSeconds=" + (Context.now() - stats.pickedAt));

            // a page that wrote nothing means the next page would be the same docs, forever. bail
            // rather than spin the budget on them.
            if (written == 0) {
                stats.stopReason = "noProgress";
                loggerMaker.errorAndAddToDb("Discovered timestamp backfill: page " + stats.pages
                        + " wrote nothing for account " + accountId + ", stopping to avoid a no-progress loop");
                break;
            }
        }
        if (stats.fixed >= limit) stats.stopReason = "limitReached";

        // one index seek, so the summary can say whether this account still needs future runs
        boolean moreWorkPending = !findRaw(BROKEN, Projections.include(Constants.ID), 1).isEmpty();

        loggerMaker.warnAndAddToDb("Discovered timestamp backfill STATS account=" + accountId
                + " pickedAt=" + stats.pickedAt
                + " durationSeconds=" + (Context.now() - stats.pickedAt)
                + " limit=" + limit
                + " stopReason=" + stats.stopReason
                + " pages=" + stats.pages
                + " docsRead=" + stats.docsRead
                + " fixed=" + stats.fixed
                + " fromSti=" + stats.fromSti
                + " fromLastSeen=" + stats.fromLastSeen
                + " fromNow=" + stats.fromNow
                + " noOpWrites=" + stats.noOpWrites
                + " stiLookups=" + stats.stiLookups
                + " collectionsTouched=" + stats.collections.size()
                + " moreWorkPending=" + moreWorkPending);

        // fromNow means we had neither an sti row nor a lastSeen and stamped the endpoint as
        // discovered now, which makes it show up as newly discovered today. Expected to be zero;
        // a non-zero here is worth chasing rather than ignoring.
        if (stats.fromNow > 0) {
            loggerMaker.errorAndAddToDb("Discovered timestamp backfill account=" + accountId
                    + " stamped " + stats.fromNow + " endpoints with the current time (no sti rows and"
                    + " no lastSeen); they will read as discovered today on the api changes page");
        }

        return stats.fixed;
    }

    /** Fixes one page, folding every counter into {@code stats}. */
    private static void processPage(List<BasicDBObject> page, RunStats stats) {
        Map<Integer, List<BasicDBObject>> byCollection = new HashMap<>();
        for (BasicDBObject doc: page) {
            BasicDBObject id = idOf(doc);
            if (id == null) continue;
            // no getInt(key) without a default anywhere in this job: it throws on a missing field,
            // and the whole reason these docs are read raw is that we do not trust their shape
            Object rawCollectionId = id.get(ApiInfo.ApiInfoKey.API_COLLECTION_ID);
            if (!(rawCollectionId instanceof Number)) continue;
            byCollection.computeIfAbsent(((Number) rawCollectionId).intValue(),
                    k -> new ArrayList<>()).add(doc);
        }

        int attempted = 0;
        int modified = 0;
        List<WriteModel<ApiInfo>> writes = new ArrayList<>();

        for (Map.Entry<Integer, List<BasicDBObject>> entry: byCollection.entrySet()) {
            int apiCollectionId = entry.getKey();
            List<BasicDBObject> docs = entry.getValue();
            stats.collections.add(apiCollectionId);

            for (int i = 0; i < docs.size(); i += URL_BATCH) {
                List<BasicDBObject> chunk = docs.subList(i, Math.min(i + URL_BATCH, docs.size()));
                stats.stiLookups++;
                Map<String, Integer> minTimestamps = stiMinTimestamps(apiCollectionId, chunk);

                for (BasicDBObject doc: chunk) {
                    BasicDBObject id = idOf(doc);
                    if (id == null) continue;
                    String url = id.getString(ApiInfo.ApiInfoKey.URL, "");
                    String method = id.getString(ApiInfo.ApiInfoKey.METHOD, "");

                    // three sources, best first. every one of them writes something: skipping a doc
                    // would leave it in the broken set to be re-read on every future run, which is
                    // what the class comment means about convergence.
                    Integer ts = minTimestamps.get(key(url, method));
                    if (ts != null && ts > 0) {
                        stats.fromSti++;
                    } else {
                        // no sti rows left for this endpoint: collection dropped, or rows aged out.
                        // lastSeen is the tightest bound we still have — an endpoint cannot have
                        // been discovered after it was last seen.
                        int lastSeen = doc.getInt(ApiInfo.LAST_SEEN, 0);
                        if (lastSeen > 0) {
                            ts = lastSeen;
                            stats.fromLastSeen++;
                        } else {
                            // nothing left to derive from. counted separately and reported loudly,
                            // because this stamps the endpoint as discovered today and it will read
                            // as new on the api changes page.
                            ts = Context.now();
                            stats.fromNow++;
                        }
                    }
                    attempted++;

                    writes.add(new UpdateOneModel<>(
                            // re-assert BROKEN: between reading this page and writing it the
                            // runtime may have inserted a real value, and it is better than ours
                            Filters.and(ApiInfoDao.getFilter(url, method, apiCollectionId), BROKEN),
                            Updates.set(ApiInfo.DISCOVERED_TIMESTAMP, ts),
                            new UpdateOptions().upsert(false)));

                    if (writes.size() >= BULK_WRITE_BATCH) {
                        modified += flush(writes);
                    }
                }
            }
        }

        modified += flush(writes);

        stats.fixed += modified;
        // writes whose re-asserted BROKEN clause no longer matched: the runtime filled the doc
        // between our read and our write. Not an error — it means we correctly stood down.
        stats.noOpWrites += (attempted - modified);
    }

    /**
     * min(timestamp) per endpoint for one chunk of urls, computed server side.
     *
     * $in on url rather than a url range: a range spans every endpoint between the bounds, broken or
     * not, so it reads 1/density times more sti rows than it needs. $in reads only the endpoints
     * asked for, and each element is one b-tree seek followed by a contiguous run of that url's
     * entries — the url index has url as its prefix.
     *
     * Grouped by url+method, not url alone: one url commonly carries several methods and their
     * discovery times differ.
     */
    private static Map<String, Integer> stiMinTimestamps(int apiCollectionId, List<BasicDBObject> docs) {
        Map<String, Integer> result = new HashMap<>();

        Set<String> urls = new HashSet<>();
        for (BasicDBObject doc: docs) {
            BasicDBObject id = idOf(doc);
            if (id != null) urls.add(id.getString(ApiInfo.ApiInfoKey.URL, ""));
        }
        if (urls.isEmpty()) return result;

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                        Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                        Filters.in(SingleTypeInfo._URL, urls))),
                Aggregates.group(
                        new BasicDBObject(SingleTypeInfo._URL, "$" + SingleTypeInfo._URL)
                                .append(SingleTypeInfo._METHOD, "$" + SingleTypeInfo._METHOD),
                        Accumulators.min("minTs", "$" + SingleTypeInfo._TIMESTAMP)));

        MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .allowDiskUse(true)
                .cursor();
        try {
            while (cursor.hasNext()) {
                BasicDBObject row = cursor.next();
                BasicDBObject id = idOf(row);
                if (id == null) continue;
                result.put(key(id.getString(SingleTypeInfo._URL, ""),
                                id.getString(SingleTypeInfo._METHOD, "")),
                        row.getInt("minTs", 0));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /** @return docs actually modified, which is what the no-progress guard has to key off. It can
     *  be lower than writes.size() when the runtime filled a doc in between our read and our write —
     *  the re-asserted BROKEN clause turns those into no-ops on purpose. */
    private static int flush(List<WriteModel<ApiInfo>> writes) {
        if (writes.isEmpty()) return 0;
        int modified = ApiInfoDao.instance.getMCollection().bulkWrite(writes).getModifiedCount();
        writes.clear();
        try {
            Thread.sleep(SLEEP_BETWEEN_WRITES_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return modified;
    }

    /**
     * Reads without the pojo codec. The dto cannot hold every value that is actually stored — a
     * method string the enum does not know throws for the whole page — and this job only needs _id
     * and lastSeen, so there is nothing to gain from decoding the rest.
     */
    private static List<BasicDBObject> findRaw(Bson filter, Bson projection, int limit) {
        List<BasicDBObject> results = new ArrayList<>();
        MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.getMCollection()
                .find(filter, BasicDBObject.class)
                .projection(projection)
                .limit(limit)
                .cursor();
        try {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        } finally {
            cursor.close();
        }
        return results;
    }

    private static BasicDBObject idOf(BasicDBObject doc) {
        if (doc == null) return null;
        Object id = doc.get(Constants.ID);
        return id instanceof BasicDBObject ? (BasicDBObject) id : null;
    }

    private static String key(String url, String method) {
        return url + " " + method;
    }

    private static int limitPerRun() {
        return envInt(LIMIT_ENV, DEFAULT_LIMIT);
    }

    private static int envInt(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            loggerMaker.errorAndAddToDb("Ignoring bad value for " + name + ": " + raw);
            return fallback;
        }
    }

    /** Defaults to on, so a fresh deployment starts draining without anyone setting anything. */
    private static boolean isEnabled() {
        String raw = System.getenv(ENABLED_ENV);
        if (raw == null || raw.trim().isEmpty()) return true;
        return !"false".equalsIgnoreCase(raw.trim());
    }

    /** Per-account counters for one run. Kept as an object rather than an int[] so the summary line
     *  can say what the run actually did — where each value came from, what it skipped and why it
     *  stopped — instead of only that it ran. */
    private static class RunStats {
        int pickedAt;
        int pages;
        int docsRead;
        int fixed;
        int fromSti;
        int fromLastSeen;
        int fromNow;
        int noOpWrites;
        int stiLookups;
        final Set<Integer> collections = new HashSet<>();
        String stopReason = "drained";
    }
}
