package com.akto.utils.crons;

import static com.akto.task.Cluster.callDibs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;
import org.json.JSONObject;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiInfo;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightClassificationHelper;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class ToolClassificationCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ToolClassificationCron.class, LogDb.DASHBOARD);

    private static final int PER_ACCOUNT_LIMIT = 200;
    private static final int RECLASSIFY_THRESHOLD_SECONDS = 30 * 24 * 60 * 60;

    private static final Pattern TOOL_URL = Pattern.compile("/tools?/");

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpToolClassificationCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, 0, 60, TimeUnit.MINUTES);
    }

    private void run() {
        try {
            Context.accountId.set(1_000_000);
            if (!callDibs(Cluster.TOOL_CLASSIFICATION_CRON_INFO, 3300, 60)) {
                loggerMaker.infoAndAddToDb("Tool classification cron dibs not acquired, thus skipping cron");
                return;
            }
            AccountTask.instance.executeTask(this::processAccount, "tool-classification-cron");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in tool classification cron: " + e.getMessage());
        }
    }

    void processAccount(Account account) {
        int accountId = account.getId();
        try {
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, TestExecutorModifier._AKTO_GPT_AI);
            if (featureAccess == null || !featureAccess.getIsGranted()) {
                loggerMaker.infoAndAddToDb("Tool classification cron: skipping accountId=" + accountId
                        + " (feature access not granted)");
                return;
            }

            List<ApiInfo> candidates = findCandidates();
            if (candidates.isEmpty()) {
                loggerMaker.infoAndAddToDb("Tool classification cron: no candidates for accountId=" + accountId);
                return;
            }

            loggerMaker.infoAndAddToDb("Tool classification cron processing accountId=" + accountId
                    + ", candidates=" + candidates.size());

            long startMs = System.currentTimeMillis();
            List<WriteModel<ApiInfo>> updates = new ArrayList<>();
            for (ApiInfo tool : candidates) {
                classify(accountId, tool, updates);
            }

            if (!updates.isEmpty()) {
                ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
            }
            loggerMaker.infoAndAddToDb("Tool classification cron finished accountId=" + accountId
                    + ", classified=" + updates.size() + "/" + candidates.size()
                    + ", took=" + (System.currentTimeMillis() - startMs) + "ms");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in tool classification cron for accountId=" + accountId
                    + ": " + e.getMessage());
        }
    }

    /**
     * Tool rows in Argus collections whose classification is missing or stale.
     *
     * The collection scope is resolved explicitly rather than left to the DAO's RBAC filter:
     * that filter only engages when a userId or contextSource is set on the thread, and neither
     * is inside a cron, so an unscoped query would pick up Atlas collections too.
     */
    List<ApiInfo> findCandidates() {
        Set<Integer> argusCollectionIds = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.AGENTIC);
        if (argusCollectionIds == null || argusCollectionIds.isEmpty()) return new ArrayList<>();

        Bson filter = Filters.and(
                Filters.in(ApiInfo.ID_API_COLLECTION_ID, argusCollectionIds),
                Filters.regex(ApiInfo.ID_URL, TOOL_URL),
                Filters.or(
                        Filters.exists(ApiInfo.TOOL_INFO_CALCULATED_AT, false),
                        Filters.lte(ApiInfo.TOOL_INFO_CALCULATED_AT, Context.now() - RECLASSIFY_THRESHOLD_SECONDS)));

        return ApiInfoDao.instance.findAll(filter, 0, PER_ACCOUNT_LIMIT,
                Sorts.descending(ApiInfo.LAST_SEEN), Projections.include(Constants.ID));
    }

    void classify(int accountId, ApiInfo tool, List<WriteModel<ApiInfo>> updates) {
        ApiInfo.ApiInfoKey key = tool.getId();
        if (key == null || key.getUrl() == null || key.getMethod() == null) {
            loggerMaker.infoAndAddToDb("Tool classification cron: skipping row with incomplete key, accountId="
                    + accountId);
            return;
        }

        try {
            String rawSample = SampleDataDao.getLatestSampleData(key.getApiCollectionId(), key.getUrl(),
                    key.getMethod().name());
            if (rawSample == null) {
                loggerMaker.infoAndAddToDb("Tool classification cron: no sample data, accountId=" + accountId
                        + ", url=" + key.getUrl() + ", will retry next tick");
                return;
            }

            String sample = stripHeaders(rawSample);
            if (sample.isEmpty()) {
                loggerMaker.infoAndAddToDb("Tool classification cron: sample unparseable, accountId=" + accountId
                        + ", url=" + key.getUrl() + ", will retry next tick");
                return;
            }

            String toolName = toolNameFromUrl(key.getUrl());
            InsightClassificationHelper.ToolDangerVerdict verdict =
                    InsightClassificationHelper.classifyToolDanger(toolName, sample);
            if (verdict == null) {
                loggerMaker.errorAndAddToDb("Tool classification cron: classification failed, accountId=" + accountId
                        + ", tool=" + toolName + ", will retry next tick");
                return;
            }

            loggerMaker.infoAndAddToDb("Tool classification cron: accountId=" + accountId
                    + ", collectionId=" + key.getApiCollectionId() + ", tool=" + toolName
                    + ", capability=" + verdict.capability + ", dangerous=" + verdict.dangerous);

            updates.add(new UpdateOneModel<>(
                    ApiInfoDao.getFilter(key),
                    Updates.combine(
                            Updates.set(ApiInfo.TOOL_INFO_CAPABILITY, verdict.capability),
                            Updates.set(ApiInfo.TOOL_INFO_CALCULATED_AT, Context.now()))));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Tool classification failed for accountId=" + accountId
                    + ", url=" + key.getUrl() + ": " + e.getMessage());
        }
    }

    /**
     * Drops the header blocks before the sample reaches the LLM. They carry bearer tokens, cookies
     * and api keys, and say nothing about what the tool does. Everything else is left intact.
     */
    static String stripHeaders(String rawSample) {
        try {
            JSONObject sample = new JSONObject(rawSample);
            sample.remove("requestHeaders");
            sample.remove("responseHeaders");
            return sample.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static String toolNameFromUrl(String url) {
        int idx = url.lastIndexOf('/');
        return idx >= 0 && idx < url.length() - 1 ? url.substring(idx + 1) : url;
    }

    public void forceRunForAccount(int accountId) {
        Context.accountId.set(accountId);
        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, accountId));
        if (account == null) {
            loggerMaker.errorAndAddToDb("forceRunForAccount: no account found for accountId=" + accountId);
            return;
        }
        processAccount(account);
    }
}
