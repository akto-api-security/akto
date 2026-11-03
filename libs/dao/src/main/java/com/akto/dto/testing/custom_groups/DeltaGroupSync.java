package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
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
 * - With no last sync time, starts {@value #FIRST_SYNC_LOOKBACK_DAYS} days back.
 * - Spreads the writes: small synchronous batches, a pause after each, and a cap on changes per run.
 */
public abstract class DeltaGroupSync {

    private static final Logger logger = LoggerFactory.getLogger(DeltaGroupSync.class);

    // each write is one updateMany per collection with an $or of this many (url, method) pairs, so keep it small
    private static final int WRITE_BATCH_SIZE = 100;
    // pause after each write batch, and a cap per run, so a large change set is spread over time instead of spiking the primary
    private static final long WRITE_PAUSE_MS = 200;
    private static final int MAX_CHANGES_PER_RUN = 20_000;
    private static final int SLICE_SECONDS = 60 * 60;
    private static final int SYNC_BUFFER_SECONDS = 10 * 60;
    private static final int FIRST_SYNC_LOOKBACK_DAYS = 3;
    private static final long SLOW_RUN_WARN_MS = 5 * 60 * 1000L;

    public static final class Candidate {
        private final ApiInfoKey key;
        private final boolean member;
        private final boolean inGroup;

        /**
         * @param member  whether the api should be in the group
         * @param inGroup whether the api carries the group tag right now
         */
        public Candidate(ApiInfoKey key, boolean member, boolean inGroup) {
            this.key = key;
            this.member = member;
            this.inGroup = inGroup;
        }
    }

    private static final class Result {
        int slices;
        int scanned;
        int added;
        int removed;

        int changes() {
            return added + removed;
        }
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

    public final void run() {
        int accountId = Context.accountId.get();
        long startMs = System.currentTimeMillis();
        int syncStartedAt = Context.now();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        LastCronRunInfo lastRunInfo = accountSettings == null ? null : accountSettings.getLastUpdatedCronInfo();
        Integer lastSync = lastRunInfo == null || lastRunInfo.getGroupSyncTimes() == null
                ? null : lastRunInfo.getGroupSyncTimes().get(String.valueOf(groupId()));
        boolean hasMarker = lastSync != null && lastSync != 0;
        int since = hasMarker ? Math.max(lastSync - SYNC_BUFFER_SECONDS, 0)
                : syncStartedAt - FIRST_SYNC_LOOKBACK_DAYS * Constants.ONE_DAY_TIMESTAMP;
        if (!hasMarker) {
            logger.warn(String.format("AccountId: %d %s has no last sync time, checking the last %d days (since=%d)",
                    accountId, groupName(), FIRST_SYNC_LOOKBACK_DAYS, since));
        }

        Result result = new Result();
        int sliceFrom = since;
        int sliceTo = since;
        try {
            sync(result, visitor -> scanUntimed(visitor));
            for (; sliceFrom < syncStartedAt; sliceFrom = sliceTo) {
                sliceTo = Math.min(sliceFrom + SLICE_SECONDS, syncStartedAt);
                int from = sliceFrom;
                int to = sliceTo;
                sync(result, visitor -> scan(from, to, visitor));
                if (result.changes() >= MAX_CHANGES_PER_RUN) {
                    // this slice may be unfinished, so it is not saved; what is already fixed is skipped next time
                    logger.warn(String.format("AccountId: %d %s sync hit the cap of %d changes in slice [%d, %d): slices done=%d, scanned=%d, added=%d, removed=%d, took=%d ms; the rest continues next run from the last saved slice",
                            accountId, groupName(), MAX_CHANGES_PER_RUN, sliceFrom, sliceTo, result.slices, result.scanned, result.added, result.removed, System.currentTimeMillis() - startMs));
                    return;
                }
                saveLastSync(sliceTo);
                result.slices++;
            }
        } catch (Exception e) {
            logger.error(String.format("AccountId: %d %s sync failed in slice [%d, %d) after slices done=%d, scanned=%d, added=%d, removed=%d, took=%d ms, since=%d; the next run resumes from the last saved slice",
                    accountId, groupName(), sliceFrom, sliceTo, result.slices, result.scanned, result.added, result.removed, System.currentTimeMillis() - startMs, since), e);
            return;
        }

        long tookMs = System.currentTimeMillis() - startMs;
        String message = String.format("AccountId: %d %s synced: since=%d (%s), slices=%d, scanned=%d, added=%d, removed=%d, took=%d ms",
                accountId, groupName(), since,
                hasMarker ? "last sync " + (syncStartedAt - lastSync) / 60 + " min ago" : "no last sync, fallback",
                result.slices, result.scanned, result.added, result.removed, tookMs);
        if (tookMs > SLOW_RUN_WARN_MS) {
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
            // write only when the group tag disagrees with the source data
            if (candidate.member == candidate.inGroup) {
                return true;
            }
            (candidate.member ? toAdd : toRemove).add(candidate.key);

            if (toAdd.size() >= WRITE_BATCH_SIZE) {
                result.added += write(toAdd, true);
            }
            if (toRemove.size() >= WRITE_BATCH_SIZE) {
                result.removed += write(toRemove, false);
            }
            return result.changes() < MAX_CHANGES_PER_RUN;
        });

        result.added += write(toAdd, true);
        result.removed += write(toRemove, false);
    }

    private void saveLastSync(int time) {
        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.GROUP_SYNC_TIMES + "." + groupId(), time),
            new UpdateOptions().upsert(true));
    }

    /** Writes the given apis on the calling thread (so failures reach run()), then empties the list. @return how many were written */
    private int write(List<ApiInfoKey> keys, boolean add) {
        if (keys.isEmpty()) {
            return 0;
        }
        int count = keys.size();
        List<TestingEndpoints> conditions = Collections.singletonList(endpointsFor(keys));
        if (add) {
            ApiCollectionUsers.addToCollectionsForCollectionId(conditions, groupId(), true);
        } else {
            ApiCollectionUsers.removeApisFromCollectionId(conditions, groupId(), true);
        }
        keys.clear();

        try {
            Thread.sleep(WRITE_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return count;
    }
}
