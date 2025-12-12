package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dto.Account;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.AgentBasePromptDetectionService;
import com.akto.util.AccountTask;

public class AgentBasePromptDetectionCron {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentBasePromptDetectionCron.class, LogDb.DASHBOARD);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpAgentBasePromptDetectionScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            loggerMaker.debugAndAddToDb("Starting agent base prompt detection for account: " + t.getId(), LogDb.DASHBOARD);
                            AgentBasePromptDetectionService service = new AgentBasePromptDetectionService();
                            service.runJob();
                            loggerMaker.debugAndAddToDb("Completed agent base prompt detection for account: " + t.getId(), LogDb.DASHBOARD);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e,
                                    String.format("Error while running agent base prompt detection for account %d: %s", 
                                            t.getId(), e.toString()),
                                    LogDb.DASHBOARD);
                        }
                    }
                }, "agent-base-prompt-detection");
            }
        }, 0, 24, TimeUnit.HOURS);
    }
}