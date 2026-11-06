package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

/**
 * Base class for keeping an automated api group in line with its source data without ever resetting it.
 *
 * To support a new group, extend this class and implement:
 * - {@link #groupId()} and {@link #groupName()},
 * - {@link #scan} to report the apis that changed in a time range and whether each should be in the group,
 * - {@link #endpointsFor} to build the condition that matches exactly some apis,
 * - optionally {@link #scanUntimed} for apis that cannot be found by time,
 * then call {@link #run()} from the cron.
 *
 * What this class does for every group:
 * - Writes only the apis whose group tag disagrees with the source data, so running it again is harmless.
 * - Goes through the time since the last sync in order, one slice at a time, and saves the group's last sync time in
 *   account settings after every finished slice. If a run breaks (error, crash, restart, cap), the next run resumes
 *   from the last finished slice instead of starting over.
 * - Keeps a separate last sync time per group (groupSyncTimes.groupId), so a failing group does not affect the others.
 * - With no last sync time, starts a few days back.
 * - Never updates in bulk: the writes are small synchronous batches with a pause after each.
 *
 * Algorithm of {@link #run()}:
 * 1. Read the group's last sync time from account settings (none: start a few days back) and start from it, minus a
 *    small overlap.
 * 2. Check the apis that cannot be found by time ({@link #scanUntimed}), then each time slice in order up to now.
 * 3. For every reported api, compare "should be in the group" with "has the group tag"; if they agree, do nothing.
 * 4. Otherwise collect it, and write each full batch with one updateMany per collection (add or pull the tag), then pause.
 * 5. After a slice is fully written, save the time of the newest data read so far as the group's last sync time (when
 *    nothing newer is read it stays, but not more than the hold back behind now).
 * 6. On any error stop without saving the unfinished slice; the next run resumes from the last saved one.
 * 7. Log one line: apis scanned, added, removed, write batches sent and documents modified.
 *
 * The tuning values below have defaults and can be overridden by a group.
 */
public abstract class ApiGroupSync {

    private static final Logger logger = LoggerFactory.getLogger(ApiGroupSync.class);

    public static final class Candidate {
        final ApiInfoKey key;
        final boolean member;
        final boolean inGroup;
        final int time;

        /**
         * @param member  whether the api should be in the group
         * @param inGroup whether the api carries the group tag right now
         * @param time    the time of the data it was found by (0 when it has none); the last sync time follows the newest one read
         */
        public Candidate(ApiInfoKey key, boolean member, boolean inGroup, int time) {
            this.key = key;
            this.member = member;
            this.inGroup = inGroup;
            this.time = time;
        }
    }

    private static final class Result {
        int slices;
        int scanned;
        int added;
        int removed;
        // the newest time of the data read in this run
        int newestSeen;
        // database side of the writes: write batches sent (each is one updateMany per collection) and documents modified
        int batches;
        long docsModified;
    }

    /** Each write is one updateMany per collection with an $or of this many (url, method) pairs, so keep it small. */
    protected int writeBatchSize() {
        return 100;
    }

    /** Pause after each write batch, so a large change set is spread over time instead of spiking the primary. */
    protected long writePauseMs() {
        return 200;
    }

    /**
     * Length of the time ranges a run goes through in order; the last sync time is saved after each. The first run of a
     * group, which has no last sync time, is the exception: it goes through its whole look back in one go.
     */
    protected int sliceSeconds() {
        return 60 * 60;
    }

    /**
     * A run starts this much before the last sync time, to cover docs that were still being written when the last run
     * read them. Re-checking only costs reads: a doc is written only when its tag is wrong. A service that was down and
     * writes its backlog later is covered by the last sync time not moving past the newest data, not by this.
     */
    protected int syncBufferSeconds() {
        return 10 * 60;
    }

    /** How far back to start when the group has no last sync time. */
    protected int firstSyncLookbackDays() {
        return 3;
    }

    /** A run longer than this is logged as a warning. */
    protected long slowRunWarnMs() {
        return 5 * 60 * 1000L;
    }

    /**
     * The last sync time is the time of the newest data a run really read, so data that is still on its way (for
     * example while the service that consumes the runtime's data is down) is not skipped. When a run reads nothing it
     * stays where it was, but it is not held back more than this behind now, so an account with no traffic does not
     * keep re-reading a growing window.
     */
    protected int maxHoldBackSeconds() {
        return 12 * 60 * 60;
    }

    protected abstract int groupId();

    /** used in log lines */
    protected abstract String groupName();

    /**
     * Reports the apis that changed in [from, to) (epoch seconds, by whatever time field the source data has) and stops
     * as soon as the visitor returns false.
     */
    protected abstract void scan(int from, int to, Predicate<Candidate> visitor);

    /** Reports apis that cannot be found by time, once per run. Nothing by default. */
    protected void scanUntimed(Predicate<Candidate> visitor) {
    }

    /** The condition matching exactly the given apis, used to add them to or remove them from the group. */
    protected abstract TestingEndpoints endpointsFor(List<ApiInfoKey> keys);

    /**
     * For groups whose members come from single_type_info: reports every api that has a doc matching the filter, as a
     * member. An api counts as tagged only if all of its matching docs carry the group tag. Apis with an unknown method
     * are skipped. @return false when the visitor asked to stop
     */
    protected final boolean scanSingleTypeInfo(Bson filter, Predicate<Candidate> visitor) {
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + SingleTypeInfo._API_COLLECTION_ID)
                .append(SingleTypeInfo._URL, "$" + SingleTypeInfo._URL)
                .append(SingleTypeInfo._METHOD, "$" + SingleTypeInfo._METHOD);
        BasicDBObject allTagged = new BasicDBObject("$in", Arrays.<Object>asList(groupId(),
                new BasicDBObject("$ifNull", Arrays.<Object>asList("$" + SingleTypeInfo._COLLECTION_IDS, Collections.emptyList()))));
        List<Bson> pipeline = Arrays.asList(
            Aggregates.match(filter),
            Aggregates.group(groupedId, Accumulators.min("allTagged", allTagged),
                Accumulators.max("time", "$" + SingleTypeInfo._TIMESTAMP)));

        try (MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).batchSize(1000).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                BasicDBObject id = (BasicDBObject) doc.get("_id");
                Method method = Method.fromString(id.getString(SingleTypeInfo._METHOD));
                if (method.equals(Method.OTHER)) {
                    continue;
                }
                ApiInfoKey key = new ApiInfoKey(id.getInt(SingleTypeInfo._API_COLLECTION_ID), id.getString(SingleTypeInfo._URL), method);
                if (!visitor.test(new Candidate(key, true, doc.getBoolean("allTagged"), doc.getInt("time", 0)))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The api collections whose apis can be in a group: not groups themselves, and not mcp server or gen ai collections. */
    protected static Set<Integer> fetchApiCollectionIds() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.and(
                UsersCollectionsList.getApiContextFilter(),
                Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString())
            ), Projections.include("_id")
        );
        Set<Integer> ids = new HashSet<>();
        for (ApiCollection apiCollection : apiCollections) {
            ids.add(apiCollection.getId());
        }
        return ids;
    }

    public final void run() {
        int accountId = Context.accountId.get();
        long startMs = System.currentTimeMillis();
        int syncStartedAt = Context.now();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter(),
                Projections.include(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.GROUP_SYNC_TIMES + "." + groupId()));
        LastCronRunInfo lastRunInfo = accountSettings == null ? null : accountSettings.getLastUpdatedCronInfo();
        Integer lastSync = lastRunInfo == null || lastRunInfo.getGroupSyncTimes() == null
                ? null : lastRunInfo.getGroupSyncTimes().get(String.valueOf(groupId()));
        boolean hasMarker = lastSync != null && lastSync != 0;
        // from the last sync time (a data time, not the clock) minus a small overlap
        int since = hasMarker ? Math.max(lastSync - syncBufferSeconds(), 0)
                : syncStartedAt - firstSyncLookbackDays() * Constants.ONE_DAY_TIMESTAMP;
        if (!hasMarker) {
            logger.warn(String.format("AccountId: %d %s has no last sync time, checking the last %d days (since=%d)",
                    accountId, groupName(), firstSyncLookbackDays(), since));
        }

        Result result = new Result();
        int sliceFrom = since;
        int sliceTo = since;
        int lastSaved = hasMarker ? lastSync : 0;
        try {
            // later runs go hour by hour, the first run covers its whole look back at once instead of many small queries
            int sliceLength = hasMarker ? sliceSeconds() : Math.max(syncStartedAt - since, 1);
            sync(result, visitor -> scanUntimed(visitor));
            for (; sliceFrom < syncStartedAt; sliceFrom = sliceTo) {
                sliceTo = Math.min(sliceFrom + sliceLength, syncStartedAt);
                int from = sliceFrom;
                int to = sliceTo;
                sync(result, visitor -> scan(from, to, visitor));
                // the last sync time follows the newest data read, not the clock
                if (result.newestSeen > lastSaved) {
                    saveLastSync(result.newestSeen);
                    lastSaved = result.newestSeen;
                }
                result.slices++;
            }
            // nothing newer was read: stay where it was, but not more than the hold back behind now
            int heldBack = syncStartedAt - maxHoldBackSeconds();
            if (heldBack > lastSaved) {
                saveLastSync(heldBack);
                lastSaved = heldBack;
            }
        } catch (Exception e) {
            logger.error(String.format("AccountId: %d %s sync failed in slice [%d, %d) after slices done=%d, scanned=%d, added=%d, removed=%d, write batches=%d, docs modified=%d, took=%d ms, since=%d; the next run resumes from the last saved slice",
                    accountId, groupName(), sliceFrom, sliceTo, result.slices, result.scanned, result.added, result.removed, result.batches, result.docsModified, System.currentTimeMillis() - startMs, since), e);
            return;
        }

        long tookMs = System.currentTimeMillis() - startMs;
        String message = String.format("AccountId: %d %s synced: since=%d (%s), newest data read=%d, saved=%d, slices=%d, scanned=%d, added=%d, removed=%d, write batches=%d, docs modified=%d, took=%d ms",
                accountId, groupName(), since,
                hasMarker ? "last sync " + (syncStartedAt - lastSync) / 60 + " min ago" : "no last sync, fallback",
                result.newestSeen, lastSaved, result.slices, result.scanned, result.added, result.removed, result.batches, result.docsModified, tookMs);
        if (tookMs > slowRunWarnMs()) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }

    /** Feeds the candidates of one scan through the compare and write logic, and writes what is left at the end. */
    private void sync(Result result, Consumer<Predicate<Candidate>> scanner) {
        List<ApiInfoKey> toAdd = new ArrayList<>();
        List<ApiInfoKey> toRemove = new ArrayList<>();

        scanner.accept(candidate -> {
            result.scanned++;
            result.newestSeen = Math.max(result.newestSeen, candidate.time);
            // write only when the group tag disagrees with the source data
            if (candidate.member == candidate.inGroup) {
                return true;
            }
            (candidate.member ? toAdd : toRemove).add(candidate.key);

            if (toAdd.size() >= writeBatchSize()) {
                result.added += write(toAdd, true, result);
            }
            if (toRemove.size() >= writeBatchSize()) {
                result.removed += write(toRemove, false, result);
            }
            return true;
        });

        result.added += write(toAdd, true, result);
        result.removed += write(toRemove, false, result);
    }

    private void saveLastSync(int time) {
        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.GROUP_SYNC_TIMES + "." + groupId(), time),
            new UpdateOptions().upsert(true));
    }

    /** Writes the given apis on the calling thread (so failures reach run()), then empties the list. @return how many were written */
    private int write(List<ApiInfoKey> keys, boolean add, Result result) {
        if (keys.isEmpty()) {
            return 0;
        }
        int count = keys.size();
        List<TestingEndpoints> conditions = Collections.singletonList(endpointsFor(keys));
        result.docsModified += add
                ? ApiCollectionUsers.addToCollectionsForCollectionId(conditions, groupId(), true)
                : ApiCollectionUsers.removeApisFromCollectionId(conditions, groupId(), true);
        result.batches++;
        keys.clear();

        try {
            Thread.sleep(writePauseMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return count;
    }
}
