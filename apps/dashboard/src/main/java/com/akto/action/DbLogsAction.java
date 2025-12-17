package com.akto.action;

import java.util.List;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class DbLogsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbLogsAction.class, LogDb.DASHBOARD);;

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

    public String fetchLogsFromDb() {
        if (logDb == null) {
            addActionError("Invalid log collection");
            return ERROR.toUpperCase();
        }
        logs = loggerMaker.fetchLogRecords(startTime, endTime, logDb);

        return SUCCESS.toUpperCase();
    }

    public List<Log> getLogs() {
        return logs;
    }

    public void setLogs(List<Log> logs) {
        this.logs = logs;
    }

    private String log;
    private String key;

    public String insertLogsInDb() {
        if (logDb == null) {
            addActionError("Invalid log collection");
            return ERROR.toUpperCase();
        }

        if (log == null || log.isEmpty()) {
            addActionError("Log message is required");
            return ERROR.toUpperCase();
        }

        try {
            int timestamp = Context.now();
            Log logEntry = new Log(log, key, timestamp);

            switch (logDb) {
                case TESTING:
                    LogsDao.instance.insertOne(logEntry);
                    break;
                case RUNTIME:
                    RuntimeLogsDao.instance.insertOne(logEntry);
                    break;
                case DASHBOARD:
                    DashboardLogsDao.instance.insertOne(logEntry);
                    break;
                case DATA_INGESTION:
                    DataIngestionLogsDao.instance.insertOne(logEntry);
                    break;
                case ANALYSER:
                    AnalyserLogsDao.instance.insertOne(logEntry);
                    break;
                case BILLING:
                    BillingLogsDao.instance.insertOne(logEntry);
                    break;
                case THREAT_DETECTION:
                    ProtectionLogsDao.instance.insertOne(logEntry);
                    break;
                case PUPPETEER:
                    PupeteerLogsDao.instance.insertOne(logEntry);
                    break;
                default:
                    addActionError("Unsupported log collection type");
                    return ERROR.toUpperCase();
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error inserting log: " + e.getMessage());
            addActionError("Failed to insert log: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}