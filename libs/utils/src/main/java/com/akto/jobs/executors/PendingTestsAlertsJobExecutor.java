package com.akto.jobs.executors;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.PendingTestsAlertsJobParams;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.info.ScheduledTestInfo;
import com.akto.dto.testing.info.ScheduledTestsDetails;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.notifications.webhook.WebhookSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;

import static com.akto.runtime.utils.Utils.convertEpochToDateTime;


public class PendingTestsAlertsJobExecutor extends JobExecutor<PendingTestsAlertsJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(PendingTestsAlertsJobExecutor.class);
    public static final PendingTestsAlertsJobExecutor INSTANCE = new PendingTestsAlertsJobExecutor();
    public static final int oneHour = 3600; // 1 hour in seconds

    public PendingTestsAlertsJobExecutor() {
        super(PendingTestsAlertsJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        logger.info("Starting sendPendingTestsWebhookAlert job for job: {}", job.getId());
        PendingTestsAlertsJobParams params = paramClass.cast(job.getJobParams());
        int webhookId = params.getCustomWebhookId();
        int lastSyncedAt = params.getLastSyncedAt();

        int accountId = Context.accountId.get();
        List<TestingRun> scheduledTests = getScheduledTestsForNextHour();

        if(scheduledTests.isEmpty()) {
            logger.info("No scheduled tests found for the next hour for accountId {}. Skipping job execution.", accountId);
            params.setLastSyncedAt(Context.now());
            updateJobParams(job, params);
            return;
        }else {

            logger.info("Found {} scheduled tests for the next hour", scheduledTests.size());
            createPayloadForPendingTests(scheduledTests, webhookId);

        }

        for(TestingRun testingRun : scheduledTests) {

            logger.info("Updating sendPendingTestsWebhookTimestamp for test: {}", testingRun.getName());
            WriteConcern writeConcern = WriteConcern.W1;
            Bson completedUpdate = Updates.combine(
                    Updates.set(TestingRun.SEND_PENDING_TESTS_WEBHOOK_TIMESTAMP, Context.now())
            );
            TestingRunDao.instance.getMCollection().withWriteConcern(writeConcern).findOneAndUpdate(
                    Filters.eq("_id", testingRun.getId()),  completedUpdate
            );
            logger.info("Updated sendPendingTestsWebhookTimestamp for test: {}", testingRun.getName());

            logger.info("Evaluating test [{}]: Scheduled at {}, Last alert sent at {}",
                    testingRun.getId(), testingRun.getScheduleTimestamp(), testingRun.getSendPendingTestsWebhookTimestamp());
        }
        params.setLastSyncedAt(Context.now());
        updateJobParams(job, params);

        logger.info("sendPendingTestsWebhookTimestamp Alert job completed.");
    }

    private void createPayloadForPendingTests(List<TestingRun> pendingTests, int webhookId) {
        try {
            CustomWebhook webhook = CustomWebhooksDao.instance.findOne(Filters.eq("_id", webhookId));
            logger.info("Starting to create Webhook Payload for pending tests with webhookId: {}", webhookId);
            if (pendingTests == null || pendingTests.isEmpty()) {
                logger.info("No pending tests found. Skipping webhook sending.");
                return;
            }
            if (webhook.getUrl() == null || webhook.getUrl().isEmpty()) {
                logger.warn("Webhook URL is not set. Skipping webhook sending for pending tests.");
                return;
            }

            List<ScheduledTestInfo> tests = pendingTests.stream()
                    .map(tr -> new ScheduledTestInfo(tr.getName(), convertEpochToDateTime(tr.getScheduleTimestamp())))
                    .collect(Collectors.toList());

            ScheduledTestsDetails details = new ScheduledTestsDetails(pendingTests.size(), tests);
            logger.info("Created ScheduledTestsDetails with {} pending tests", details.getCountPendingTests());

            ObjectMapper objectMapper = new ObjectMapper();
            String payload = objectMapper.writeValueAsString(details);
            int now = Context.now();
            List<String> errors = new ArrayList<>();
            WebhookSender.sendCustomWebhook(webhook, payload, errors, now, LoggerMaker.LogDb.TESTING);

        } catch (Exception e) {
            logger.error("Error sending webhook for pending tests: {}", e.getMessage(), e);
        }
    }


    public List<TestingRun> getScheduledTestsForNextHour() {
        int now = Context.now();
        int oneHourLater = now + oneHour; // 3600 seconds = 1 hour
        int oneHourBefore = now - oneHour;

        Bson filter = Filters.and(
                Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, now),
                Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, oneHourLater),
                Filters.or(
                        Filters.exists(TestingRun.SEND_PENDING_TESTS_WEBHOOK_TIMESTAMP, false),
                        Filters.eq(TestingRun.SEND_PENDING_TESTS_WEBHOOK_TIMESTAMP, 0),
                        Filters.lt(TestingRun.SEND_PENDING_TESTS_WEBHOOK_TIMESTAMP, oneHourBefore)
                )
        );
        return TestingRunDao.instance.findAll(filter);
    }
}

