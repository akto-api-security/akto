package com.akto.utils.crons;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AccountsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.TestingInstanceHeartBeatDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.Account;
import com.akto.dto.Config;
import com.akto.dto.TestingInstanceHeartBeat;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.Constants;
import com.akto.util.tasks.OrganizationTask;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import static com.akto.task.Cluster.callDibs;

public class TestingAlertsCron {
    private static final LoggerMaker logger = new LoggerMaker(TestingAlertsCron.class, LogDb.DASHBOARD);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Cache for organizations with AKTO_TESTING_ALERTS feature
    private static final Map<String, Organization> orgsWithTestingAlertsFeature = Collections.synchronizedMap(new HashMap<>());
    
    // Heartbeat threshold: 1 minute
    private static final int HEARTBEAT_THRESHOLD_SECONDS = 60;
    
    // Buffer for scheduled tests: 1 minute (only alert if test was supposed to run more than 1 minute ago)
    private static final int SCHEDULED_TEST_BUFFER_SECONDS = 60;
    
    // Slack webhook URL cache
    private static String cachedSlackWebhookUrl = null;
    private static int lastSlackUrlRefreshTs = 0;
    private static final int SLACK_URL_REFRESH_INTERVAL = 10 * 60; // 10 minutes

    private final static String TESTING_ALERTS_FEATURE = "AKTO_TESTING_ALERTS";

    public void setUpOrganizationFeatureCacheScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(1_000_000);
                    logger.debug("Starting organization feature cache update");
                    orgsWithTestingAlertsFeature.clear();
                    OrganizationTask.instance.executeTask(new Consumer<Organization>() {
                        @Override
                        public void accept(Organization org) {
                            try {
                                // Check if organization has AKTO_TESTING_ALERTS feature
                                HashMap<String, FeatureAccess> featureWiseAllowed = org.getFeatureWiseAllowed();
                                if (featureWiseAllowed != null) {
                                    FeatureAccess featureAccess = featureWiseAllowed.get(TESTING_ALERTS_FEATURE);
                                    if (featureAccess != null && featureAccess.getIsGranted()) {
                                        orgsWithTestingAlertsFeature.put(org.getId(), org);
                                        logger.debug("Added org " + org.getId() + " to testing alerts cache");
                                    }
                                }
                            } catch (Exception e) {
                                logger.errorAndAddToDb(e, "Error while checking feature for org " + org.getId());
                            }
                        }
                    }, "update-org-feature-cache");
                    
                    logger.debug("Completed organization feature cache update. Cached " + orgsWithTestingAlertsFeature.size() + " orgs");
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Error in organization feature cache update");
                }
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    public void setUpTestingAlertsScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(1_000_000);
                    // Call dibs inside the runnable so it's checked on each run
                    // expiryPeriod: 10 minutes (longer than 5-minute cron interval)
                    boolean dibs = callDibs(Cluster.ALERTS_CRON, 10 * 60, 5*60);
                    if (!dibs) {
                        logger.debug("Cron for testing alerts not acquired, thus skipping cron");
                        return;
                    }
                    
                    logger.debug("Starting testing alerts check");
                    
                    // Iterate over cached organizations
                    Map<String, Organization> orgsToCheck = new HashMap<>(orgsWithTestingAlertsFeature);
                    
                    if (orgsToCheck.isEmpty()) {
                        logger.debug("No organizations to check for testing alerts");
                        return;
                    }
                    
                    // Check accounts for each organization
                    for (Map.Entry<String, Organization> entry : orgsToCheck.entrySet()) {
                        Organization org = entry.getValue();
                        if (org == null || org.getAccounts() == null || org.getAccounts().isEmpty()) {
                            continue;
                        }
                        
                        // Directly probe accounts by setting context
                        for (Integer accountId : org.getAccounts()) {
                            try {
                                Context.accountId.set(accountId);
                                Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, accountId));
                                if (account == null || account.isInactive()) {
                                    continue;
                                }
                                
                                // Check mini-testing module heartbeat for this account
                                checkMiniTestingHeartbeat(account, org);
                                
                                // Check scheduled tests that should have started for this account
                                checkScheduledTests(account, org);
                            } catch (Exception e) {
                                logger.errorAndAddToDb("Error while checking account " + accountId + " for testing alerts");
                            }
                        }
                    }
                    
                    // Check common testing module heartbeat
                    checkCommonTestingModuleHeartbeat();
                    
                    logger.debug("Completed testing alerts check");
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Error in testing alerts scheduler");
                }
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void checkMiniTestingHeartbeat(Account account, Organization org) {
        try {
            // Check if mini-testing module has recent heartbeat (within threshold)
            int now = Context.now();
            int heartbeatThreshold = now - HEARTBEAT_THRESHOLD_SECONDS;
            
            List<ModuleInfo> moduleInfos = ModuleInfoDao.instance.findAll(
                Filters.and(
                    Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.MINI_TESTING),
                    Filters.gt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, heartbeatThreshold)
                )
            );
            
            if (moduleInfos == null || moduleInfos.isEmpty()) {
                String message = "Mini-testing module heartbeat not received for account " + account.getId() + " in last " + HEARTBEAT_THRESHOLD_SECONDS + " seconds" + formatOrgInfo(org);
                sendToTestingAlertsSlack(message);
            } else {
                logger.debugAndAddToDb("Mini-testing module heartbeat is functional for account " + account.getId() + ". Found " + moduleInfos.size() + " active instance(s)");
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while checking mini-testing heartbeat for account " + account.getId());
        }
    }

    private void checkScheduledTests(Account account, Organization org) {
        try {
            int now = Context.now();
            
            // Check if any tests are currently running for this account
            boolean hasRunningTests = hasRunningTestsForAccount();
            if (hasRunningTests) {
                logger.debug("Tests are running for account " + account.getId() + ", skipping scheduled test alerts");
                return;
            }
            
            // Find scheduled tests that should have started (scheduleTimestamp <= now - buffer, but state is still SCHEDULED)
            // Using buffer to account for processing time
            int bufferThreshold = now - SCHEDULED_TEST_BUFFER_SECONDS;
            
            // Check for scheduled tests in TestingRun using the same filter as Main.findPendingTestingRun
            TestingRun scheduledTest = TestingRunDao.instance.findOne(
                TestingRunDao.instance.createScheduledTestingRunFilter(bufferThreshold),
            Projections.include(Constants.ID, TestingRun.STATE));
            
            // Check for scheduled tests in TestingRunResultSummary using the same filter as Main.findPendingTestingRunResultSummary
            // Use DEFAULT_DELTA_IGNORE_TIME to avoid checking very old summaries
            int minStartTimestamp = now - TestingRunResultSummariesDao.DEFAULT_DELTA_IGNORE_TIME_SECONDS;
            TestingRunResultSummary scheduledSummary = TestingRunResultSummariesDao.instance.findOne(
                TestingRunResultSummariesDao.instance.createScheduledTestingRunResultSummaryFilter(bufferThreshold, minStartTimestamp),
                Projections.include(Constants.ID, TestingRunResultSummary.STATE));
            
            if (scheduledTest != null || scheduledSummary != null) {
                String message = "Found scheduled test(s) that should have started for account " + account.getId() + formatOrgInfo(org);
                sendToTestingAlertsSlack(message);
            } else {
                logger.debug("No scheduled tests pending for account " + account.getId());
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while checking scheduled tests for account " + account.getId());
        }
    }

    private boolean hasRunningTestsForAccount() {
        try {
            // Check if any TestingRun has state RUNNING
            TestingRun runningTest = TestingRunDao.instance.findOne(
                Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING)
            );
            if (runningTest != null) {
                return true;
            }
            
            // Check if any TestingRunResultSummary has state RUNNING
            // Use the same time filter as getCurrentRunningTestsSummaries to avoid checking very old summaries
            TestingRunResultSummary runningSummary = TestingRunResultSummariesDao.instance.findOne(
                Filters.eq(TestingRunResultSummary.STATE, TestingRun.State.RUNNING)
            );
            if (runningSummary != null) {
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while checking for running tests");
        }
        // Default: assume worst case, no tests are running
        return false;
    }

    private void checkCommonTestingModuleHeartbeat() {
        try {
            Context.accountId.set(1_000_000);
            int now = Context.now();
            int heartbeatThreshold = now - HEARTBEAT_THRESHOLD_SECONDS;
            
            // Check if there are any recent heartbeats from testing instances
            List<TestingInstanceHeartBeat> heartbeats = TestingInstanceHeartBeatDao.instance.findAll(
                Filters.gt(TestingInstanceHeartBeat.TS, heartbeatThreshold)
            );
            
            if (heartbeats == null || heartbeats.isEmpty()) {
                String message = "Common testing module heartbeat not received in last " + HEARTBEAT_THRESHOLD_SECONDS + " seconds";
                sendToTestingAlertsSlack(message);
            } else {
                logger.debug("Common testing module heartbeat is functional. Found " + heartbeats.size() + " active instance(s)");
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while checking common testing module heartbeat");
        }
    }

    private String formatOrgInfo(Organization org) {
        if (org == null) {
            return "";
        }
        String orgInfo = " (Org ID: " + org.getId();
        if (org.getAdminEmail() != null && !org.getAdminEmail().isEmpty()) {
            orgInfo += ", Org Email: " + org.getAdminEmail();
        }
        orgInfo += ")";
        return orgInfo;
    }

    private void sendToTestingAlertsSlack(String message) {
        String slackWebhookUrl = getSlackWebhookUrl();
        LoggerMaker.sendToSlack(slackWebhookUrl, message);
    }

    private String getSlackWebhookUrl() {
        int now = Context.now();
        // Refresh cache every 10 minutes
        if (cachedSlackWebhookUrl == null || (now - lastSlackUrlRefreshTs) >= SLACK_URL_REFRESH_INTERVAL) {
            synchronized (TestingAlertsCron.class) {
                // Double-check after acquiring lock
                if (cachedSlackWebhookUrl == null || (now - lastSlackUrlRefreshTs) >= SLACK_URL_REFRESH_INTERVAL) {
                    try {
                        Config config = ConfigsDao.instance.findOne(
                            Filters.eq("configType", Config.ConfigType.SLACK_ALERT_INTERNAL.name())
                        );
                        lastSlackUrlRefreshTs = now;
                        if(config == null) {
                            return null;
                        }

                        Config.SlackAlertInternalConfig slackAlertInternalConfig = (Config.SlackAlertInternalConfig) config;
                        cachedSlackWebhookUrl = slackAlertInternalConfig.getSlackWebhookUrl();
                    } catch (Exception e) {
                        logger.error("Unable to find slack webhook URL for testing alerts", e);
                    }
                }
            }
        }
        return cachedSlackWebhookUrl;
    }
}

