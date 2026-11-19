package com.akto.action;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.akto.dto.Log;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import lombok.Getter;
import lombok.Setter;

public class DbLogsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbLogsAction.class, LogDb.DASHBOARD);;

    /**
     * Log groups of modules that can be self-hosted in the customer environment
     * (see ModuleInfo.ModuleType). Everything else (DASHBOARD, BILLING, ANALYSER, ...) is internal.
     */
    private static final Set<LogDb> CUSTOMER_VISIBLE_LOG_DBS = Collections.unmodifiableSet(EnumSet.of(
        LogDb.RUNTIME, LogDb.TESTING, LogDb.THREAT_DETECTION, LogDb.DATA_INGESTION,
        LogDb.AGENTIC_TESTING, LogDb.GUARDRAILS_SERVICE, LogDb.SMART_TESTING,
        LogDb.MINI_TESTING_ORCHESTRATOR, LogDb.DAST
    ));

    private static final String INTERNAL_EMAIL_DOMAIN = "@akto.io";

    private List<LogDb> allowedLogGroups;
    private List<Log> logs;
    private int startTime;

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }
    private int endTime;
    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }
    private LogDb logDb;
    public LogDb getLogDb() {
        return logDb;
    }

    public void setLogDb(LogDb logDb) {
        this.logDb = logDb;
    }

    /** Optional. Omit or empty = no {@code key} filter. Otherwise subset of {@link com.akto.log.LoggerMaker#STORED_LOG_KEYS}. */
    @Getter
    @Setter
    private List<String> logKeys;

    /** Testing run result summary id whose scoped TESTING logs should be fetched. */
    @Getter
    @Setter
    private String testingRunResultSummaryId;

    private boolean isInternalUser() {
        User user = getSUser();
        return user != null && user.getLogin() != null
            && user.getLogin().toLowerCase().endsWith(INTERNAL_EMAIL_DOMAIN);
    }

    private List<LogDb> computeAllowedLogGroups() {
        boolean internal = isInternalUser();
        return Arrays.stream(LogDb.values())
            .filter(db -> internal || CUSTOMER_VISIBLE_LOG_DBS.contains(db))
            .collect(Collectors.toList());
    }

    public String fetchAllowedLogGroups() {
        allowedLogGroups = computeAllowedLogGroups();
        return SUCCESS.toUpperCase();
    }

    public String fetchLogsFromDb() {
        if(logDb==null){
            addActionError("Invalid log collection");
            return ERROR.toUpperCase();
        }
        if (!computeAllowedLogGroups().contains(logDb)) {
            addActionError("You do not have access to these logs");
            return "FORBIDDEN";
        }
        logs = loggerMaker.fetchLogRecords(startTime, endTime, logDb, logKeys);

        return SUCCESS.toUpperCase();
    }

    public String fetchTestRunLogs() {
        if (testingRunResultSummaryId == null || testingRunResultSummaryId.isEmpty()) {
            addActionError("Invalid testing run result summary id");
            return ERROR.toUpperCase();
        }
        logs = loggerMaker.fetchTestingLogRecordsByActivity(Log.ActivityType.TESTING_RUN_RESULT_SUMMARY_ACTIVITY, testingRunResultSummaryId, logKeys);

        return SUCCESS.toUpperCase();
    }
    public List<LogDb> getAllowedLogGroups() {
        return allowedLogGroups;
    }
    public List<Log> getLogs() {
        return logs;
    }
    public void setLogs(List<Log> logs) {
        this.logs = logs;
    }
}
