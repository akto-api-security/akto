package com.akto.action;

import com.akto.dao.MicrosoftDefenderIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.dto.microsoft_defender_integration.MicrosoftDefenderIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class MicrosoftDefenderIntegrationAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MicrosoftDefenderIntegrationAction.class, LogDb.DASHBOARD);

    private static final String JOB_TYPE = "MICROSOFT_DEFENDER_AH";
    private static final String JOB_SUB_TYPE = "ADVANCED_HUNTING";
    private static final int DEFAULT_INTERVAL = 3600;

    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String dataIngestionUrl;
    private Integer recurringIntervalSeconds;

    private MicrosoftDefenderIntegration microsoftDefenderIntegration;

    public String fetchMicrosoftDefenderIntegration() {
        microsoftDefenderIntegration = MicrosoftDefenderIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(MicrosoftDefenderIntegration.CLIENT_SECRET)
        );
        return Action.SUCCESS.toUpperCase();
    }

    public String addMicrosoftDefenderIntegration() {
        if (tenantId == null || tenantId.isEmpty()) {
            addActionError("Please enter a valid tenant ID.");
            return Action.ERROR.toUpperCase();
        }

        if (clientId == null || clientId.isEmpty()) {
            addActionError("Please enter a valid client ID.");
            return Action.ERROR.toUpperCase();
        }

        if (dataIngestionUrl == null || dataIngestionUrl.isEmpty()) {
            addActionError("Please enter a valid data ingestion service URL.");
            return Action.ERROR.toUpperCase();
        }

        int interval = (recurringIntervalSeconds != null && recurringIntervalSeconds > 0)
            ? recurringIntervalSeconds
            : DEFAULT_INTERVAL;

        int now = Context.now();

        org.bson.conversions.Bson updates = Updates.combine(
            Updates.set(MicrosoftDefenderIntegration.TENANT_ID, tenantId),
            Updates.set(MicrosoftDefenderIntegration.CLIENT_ID, clientId),
            Updates.set(MicrosoftDefenderIntegration.DATA_INGESTION_URL, dataIngestionUrl),
            Updates.set(MicrosoftDefenderIntegration.RECURRING_INTERVAL_SECONDS, interval),
            Updates.setOnInsert(MicrosoftDefenderIntegration.CREATED_TS, now),
            Updates.set(MicrosoftDefenderIntegration.UPDATED_TS, now)
        );

        String resolvedClientSecret;
        if (clientSecret != null && !clientSecret.isEmpty()) {
            resolvedClientSecret = clientSecret;
            updates = Updates.combine(updates, Updates.set(MicrosoftDefenderIntegration.CLIENT_SECRET, clientSecret));
        } else {
            MicrosoftDefenderIntegration existing = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
            if (existing == null || existing.getClientSecret() == null || existing.getClientSecret().isEmpty()) {
                addActionError("Please enter a valid client secret.");
                return Action.ERROR.toUpperCase();
            }
            resolvedClientSecret = existing.getClientSecret();
        }

        MicrosoftDefenderIntegrationDao.instance.updateOne(new BasicDBObject(), updates);

        // Upsert AccountJob for the recurring defender job
        AccountJob existingJob = AccountJobDao.instance.findOne(
            Filters.and(
                Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
            )
        );

        if (existingJob == null) {
            Map<String, Object> jobConfig = new HashMap<>();
            jobConfig.put(MicrosoftDefenderIntegration.TENANT_ID, tenantId);
            jobConfig.put(MicrosoftDefenderIntegration.CLIENT_ID, clientId);
            jobConfig.put(MicrosoftDefenderIntegration.CLIENT_SECRET, resolvedClientSecret);
            jobConfig.put(MicrosoftDefenderIntegration.DATA_INGESTION_URL, dataIngestionUrl);

            AccountJob accountJob = new AccountJob(
                Context.accountId.get(),
                JOB_TYPE,
                JOB_SUB_TYPE,
                jobConfig,
                interval,
                now,
                now
            );
            accountJob.setJobStatus(JobStatus.SCHEDULED);
            accountJob.setScheduleType(ScheduleType.RECURRING);
            accountJob.setScheduledAt(now);
            accountJob.setHeartbeatAt(0);
            accountJob.setStartedAt(0);
            accountJob.setFinishedAt(0);

            AccountJobDao.instance.insertOne(accountJob);
            loggerMaker.info("Created Microsoft Defender account job", LogDb.DASHBOARD);
        } else {
            org.bson.conversions.Bson jobUpdates = Updates.combine(
                Updates.set("config." + MicrosoftDefenderIntegration.TENANT_ID, tenantId),
                Updates.set("config." + MicrosoftDefenderIntegration.CLIENT_ID, clientId),
                Updates.set("config." + MicrosoftDefenderIntegration.CLIENT_SECRET, resolvedClientSecret),
                Updates.set("config." + MicrosoftDefenderIntegration.DATA_INGESTION_URL, dataIngestionUrl),
                Updates.set(AccountJob.RECURRING_INTERVAL_SECONDS, interval),
                Updates.set(AccountJob.LAST_UPDATED_AT, now),
                Updates.set(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name()),
                Updates.set(AccountJob.SCHEDULED_AT, now)
            );
            AccountJobDao.instance.updateOneNoUpsert(
                Filters.eq(AccountJob.ID, existingJob.getId()),
                jobUpdates
            );
            loggerMaker.info("Updated Microsoft Defender account job", LogDb.DASHBOARD);
        }

        loggerMaker.infoAndAddToDb("Microsoft Defender integration saved successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeMicrosoftDefenderIntegration() {
        MicrosoftDefenderIntegrationDao.instance.deleteAll(new BasicDBObject());

        AccountJob existingJob = AccountJobDao.instance.findOne(
            Filters.and(
                Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
            )
        );

        if (existingJob != null) {
            AccountJobDao.instance.updateOneNoUpsert(
                Filters.eq(AccountJob.ID, existingJob.getId()),
                Updates.combine(
                    Updates.set(AccountJob.JOB_STATUS, JobStatus.STOPPED.name()),
                    Updates.set(AccountJob.LAST_UPDATED_AT, Context.now())
                )
            );
        }

        loggerMaker.infoAndAddToDb("Microsoft Defender integration removed successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }
}
