package com.akto.action;

import java.util.List;

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
        if(logDb==null){
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
}
