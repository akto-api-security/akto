package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * Keeps an api group in line with its source data without ever resetting it: a group only reports which apis changed
 * since the last sync and whether each should be in the group, and only the apis whose group tag is wrong get written.
 *
 * Every group has its own last sync time in account settings, saved only after all its writes succeeded, so a failing
 * group retries from its own last good point and does not affect the others. With no last sync time the last
 * {@value #FIRST_SYNC_LOOKBACK_DAYS} days are checked.
 */
public abstract class DeltaGroupSync {

    private static final Logger logger = LoggerFactory.getLogger(DeltaGroupSync.class);

    // each write is one updateMany per collection with an $or of this many (url, method) pairs, so keep it small
    private static final int WRITE_BATCH_SIZE = 100;
    // pause after each write batch, and a cap per run, so a large change set is spread over time instead of spiking the primary
    private static final long WRITE_PAUSE_MS = 200;
    private static final int MAX_CHANGES_PER_RUN = 20_000;
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

    /** Reports the apis that may have changed since the given time; stops as soon as the visitor returns false. */
    protected abstract void scan(int since, Predicate<Candidate> visitor);

    /** The condition matching exactly the given apis, used to add them to or remove them from the group. */
    protected abstract TestingEndpoints endpointsFor(List<ApiInfoKey> keys);

    public final void run() {
        int accountId = Context.accountId.get();
        long startMs = System.currentTimeMillis();
        int syncStartedAt = Context.now();
        String markerKey = LastCronRunInfo.GROUP_SYNC_TIMES + "." + groupId();

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
        try {
            sync(since, result);
        } catch (Exception e) {
            logger.error(String.format("AccountId: %d %s sync failed after scanned=%d, added=%d, removed=%d, took=%d ms, since=%d; last sync time not saved, next run starts from the same point",
                    accountId, groupName(), result.scanned, result.added, result.removed, System.currentTimeMillis() - startMs, since), e);
            return;
        }

        long tookMs = System.currentTimeMillis() - startMs;
        if (result.changes() >= MAX_CHANGES_PER_RUN) {
            // marker left as is: what is already fixed is skipped next time, so the next run carries on from here
            logger.warn(String.format("AccountId: %d %s sync hit the cap of %d changes: scanned=%d, added=%d, removed=%d, took=%d ms, since=%d; last sync time not saved, the rest continues next run",
                    accountId, groupName(), MAX_CHANGES_PER_RUN, result.scanned, result.added, result.removed, tookMs, since));
            return;
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + markerKey, syncStartedAt),
            new UpdateOptions().upsert(true));

        String message = String.format("AccountId: %d %s synced: since=%d (%s), scanned=%d, added=%d, removed=%d, took=%d ms",
                accountId, groupName(), since,
                hasMarker ? "last sync " + (syncStartedAt - lastSync) / 60 + " min ago" : "no last sync, fallback",
                result.scanned, result.added, result.removed, tookMs);
        if (tookMs > SLOW_RUN_WARN_MS) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }

    private void sync(int since, Result result) {
        List<ApiInfoKey> toAdd = new ArrayList<>();
        List<ApiInfoKey> toRemove = new ArrayList<>();

        scan(since, candidate -> {
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
