package com.akto.ticketing;

import com.akto.dao.context.Context;
import com.akto.dao.testing.BidirectionalSyncSettingsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.testing.BidirectionalSyncSettings;
import com.akto.jobs.JobScheduler;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TicketSyncScheduler {

    private static final int SYNC_INTERVAL_SECONDS = 3600;

    public static void start(TicketSource source, String key) throws Exception {
        BidirectionalSyncSettings settings = find(source, key);

        if (settings == null) {
            Job job = JobScheduler.scheduleRecurringJob(Context.accountId.get(),
                new TicketSyncJobParams(source.name(), key, Context.now()),
                JobExecutorType.DASHBOARD, SYNC_INTERVAL_SECONDS);
            if (job == null) {
                throw new Exception("Failed to schedule the sync job. Please try again.");
            }

            BidirectionalSyncSettings created = new BidirectionalSyncSettings();
            created.setSource(source);
            created.setProjectKey(key);
            created.setActive(true);
            created.setLastSyncedAt(Context.now());
            created.setJobId(job.getId());
            BidirectionalSyncSettingsDao.instance.insertOne(created);
            return;
        }

        ObjectId jobId = settings.getJobId();
        if (!settings.isActive()) {
            Job job = JobScheduler.restartJob(jobId);
            if (job == null) {
                throw new Exception("Failed to restart the sync job. Please try again.");
            }
            jobId = job.getId();
        }

        BidirectionalSyncSettingsDao.instance.updateOne(
            Filters.eq(BidirectionalSyncSettings.ID, settings.getId()),
            Updates.combine(
                Updates.set(BidirectionalSyncSettings.ACTIVE, true),
                Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now()),
                Updates.set(BidirectionalSyncSettings.JOB_ID, jobId)
            )
        );
    }

    public static void stop(TicketSource source, String key) {
        BidirectionalSyncSettings settings = find(source, key);
        if (settings == null || !settings.isActive()) {
            return;
        }

        BidirectionalSyncSettingsDao.instance.updateOne(
            Filters.eq(BidirectionalSyncSettings.ID, settings.getId()),
            Updates.combine(
                Updates.set(BidirectionalSyncSettings.ACTIVE, false),
                Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now())
            )
        );

        if (settings.getJobId() != null) {
            JobScheduler.deleteJob(settings.getJobId());
        }
    }

    private static BidirectionalSyncSettings find(TicketSource source, String key) {
        return BidirectionalSyncSettingsDao.instance.findOne(Filters.and(
            Filters.eq(BidirectionalSyncSettings.SOURCE, source.name()),
            Filters.eq(BidirectionalSyncSettings.PROJECT_KEY, key)
        ));
    }
}
