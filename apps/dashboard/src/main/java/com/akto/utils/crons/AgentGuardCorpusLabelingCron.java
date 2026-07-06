package com.akto.utils.crons;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AgentGuardCorpusDao;
import com.akto.dao.AgentGuardCorpusQueueDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.AgentGuardCorpusEntry;
import com.akto.dto.AgentGuardCorpusQueueEntry;
import com.akto.gpt.handlers.gpt_prompts.AgentGuardIntentClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.LastCronRunInfo;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AgentGuardCorpusLabelingCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentGuardCorpusLabelingCron.class, LogDb.DASHBOARD);

    private static final int CRON_INTERVAL_MINUTES = 10;
    private static final int SLACK_SECONDS = 60;
    private static final int MAX_ROWS_PER_ACCOUNT_PER_TICK = 1000;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpAgentGuardCorpusLabelingCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        try {
                            loggerMaker.infoAndAddToDb("Starting agent-guard corpus labeling cron for account " + account.getId());
                            processAccount(account.getId());
                        } catch (Exception e) {
                            loggerMaker.error("AgentGuardCorpusLabelingCron: failure for account "
                                + account.getId() + ": " + e.getMessage());
                        }
                    }
                }, "agent-guard-corpus-labeling-cron");
            }
        }, 0, CRON_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void processAccount(int accountId) {
        Context.accountId.set(accountId);
        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        long nowMs = System.currentTimeMillis();
        long endTs = nowMs - SLACK_SECONDS * 1000L;
        long startTs = UserAnalysisCron.resolveStartTs(settings);

        List<AgentGuardCorpusQueueEntry> rows =
            AgentGuardCorpusQueueDao.instance.findAll(
                Filters.and(Filters.gte(AgentGuardCorpusQueueEntry.CREATED_AT, startTs), Filters.lte(AgentGuardCorpusQueueEntry.CREATED_AT, endTs)),
                0,
                MAX_ROWS_PER_ACCOUNT_PER_TICK,
                Sorts.descending(AgentGuardCorpusQueueEntry.CREATED_AT)
            );

        long cursorTs = endTs;
        if (!rows.isEmpty()) {
            loggerMaker.info("AgentGuardCorpusLabelingCron: labeling " + rows.size() + " queued units for account " + accountId);
            labelAndPersist(rows);
            if (rows.size() == MAX_ROWS_PER_ACCOUNT_PER_TICK) {
                cursorTs = rows.get(0).getCreatedAt();
            }
        }

        AccountSettingsDao.instance.updateOne(
            Filters.eq(Constants.ID, accountId),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_AGENT_GUARD_CORPUS_CRON, cursorTs)
        );
    }

    private void labelAndPersist(List<AgentGuardCorpusQueueEntry> rows) {
        AgentGuardIntentClassifier classifier = new AgentGuardIntentClassifier();

        List<BasicDBObject> inputs = new ArrayList<>();
        for (AgentGuardCorpusQueueEntry entry : rows) {
            inputs.add(new BasicDBObject()
                .append(AgentGuardIntentClassifier.AGENT_HOST, entry.getAgentHost())
                .append(AgentGuardIntentClassifier.UNIT_TEXT, entry.getUnitText()));
        }

        // One LLM call per row internally (see AgentGuardIntentClassifier);
        // resultsPerRow[j] is every atomic instruction extracted from rows.get(j).
        List<List<BasicDBObject>> resultsPerRow;
        try {
            resultsPerRow = classifier.handleBatch(inputs);
        } catch (Exception e) {
            loggerMaker.error("AgentGuardCorpusLabelingCron: handleBatch error: " + e.getMessage());
            return;
        }
        if (resultsPerRow == null) return;

        List<AgentGuardCorpusEntry> toInsert = new ArrayList<>();
        for (int j = 0; j < rows.size() && j < resultsPerRow.size(); j++) {
            AgentGuardCorpusQueueEntry sourceRow = rows.get(j);
            for (BasicDBObject result : resultsPerRow.get(j)) {
                if (result == null || result.containsKey("error")) continue;
                String taskIntent = result.getString("taskIntent", "");
                if (taskIntent.isEmpty()) continue;
                toInsert.add(buildLabeledEntry(sourceRow, result));
            }
        }

        if (!toInsert.isEmpty()) {
            try {
                AgentGuardCorpusDao.instance.insertMany(toInsert);
            } catch (Exception e) {
                loggerMaker.error("AgentGuardCorpusLabelingCron: insertMany failed: " + e.getMessage());
            }
        }
    }

    private static AgentGuardCorpusEntry buildLabeledEntry(AgentGuardCorpusQueueEntry queued, BasicDBObject result) {
        AgentGuardCorpusEntry entry = new AgentGuardCorpusEntry();
        entry.setAgentHost(queued.getAgentHost());
        entry.setCreatedAt((int) queued.getCreatedAt() / 1000);

        entry.setTaskIntent(result.getString("taskIntent", ""));
        entry.setRiskCategory(result.getString("riskCategory", ""));
        entry.setExtractionMethod(result.getString("extractionMethod", ""));

        AgentGuardCorpusEntry.Breakdown breakdown = new AgentGuardCorpusEntry.Breakdown();
        breakdown.setGroundTruthSourceKey(result.getString("groundTruthSourceKey", ""));
        breakdown.setGroundTruthInstructionText(result.getString("groundTruthInstructionText", ""));
        entry.setBreakdown(breakdown);

        return entry;
    }
}
