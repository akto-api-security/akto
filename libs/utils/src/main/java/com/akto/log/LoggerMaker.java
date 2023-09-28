package com.akto.log;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Log;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerMaker  {

    public final Logger logger;
    private final Class<?> aClass;

    private static int logCount = 0;
    private static int logCountResetTimestamp = Context.now();
    private static final int oneMinute = 60; 

    public enum LogDb {
        TESTING,RUNTIME,DASHBOARD, ANALYSER
    }

    private static AccountSettings accountSettings = null;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                updateAccountSettings();
            }
        }, 0, 2, TimeUnit.MINUTES);
    }


    private static void updateAccountSettings() {
        try {
            System.out.println("Running updateAccountSettings....................................");
            Context.accountId.set(1_000_000);
            accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public LoggerMaker(Class<?> c) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
    }

    public void errorAndAddToDb(String err, LogDb db) {
        logger.error(err);
        try{
            insert(err, "error", db);
        } catch (Exception e){

        }
    }

    public void debugInfoAddToDb(String info, LogDb db) {
        if (accountSettings == null || !accountSettings.isEnableDebugLogs()) return;
        infoAndAddToDb(info, db);
    }

    public void infoAndAddToDb(String info, LogDb db) {
        logger.info(info);
        try{
            insert(info, "info",db);
        } catch (Exception e){

        }
    }

    private Boolean checkUpdate(){
        if(logCount>=1000){
            if((logCountResetTimestamp + oneMinute) >= Context.now()){
                return false;
            } else {
                logCount = 0;
                logCountResetTimestamp = Context.now();
            }
        }
        return true;
    }
    
    private void insert(String info, String key, LogDb db) {
        String text = aClass + " : " + info;
        Log log = new Log(text, key, Context.now());
        
        if(checkUpdate()){
            switch(db){
                case TESTING: 
                    LogsDao.instance.insertOne(log);
                    break;
                case RUNTIME: 
                    RuntimeLogsDao.instance.insertOne(log);
                    break;
                case DASHBOARD: 
                    DashboardLogsDao.instance.insertOne(log);
                    break;
                case ANALYSER:
                    AnalyserLogsDao.instance.insertOne(log);
            }
            logCount++;
        }
    }

    public List<Log> fetchLogRecords(int logFetchStartTime, int logFetchEndTime, LogDb db) {

        List<Log> logs = new ArrayList<>();

        if( logFetchStartTime > logFetchEndTime){
            return logs;
        }
        
        Bson filters = Filters.and(
            Filters.gte(Log.TIMESTAMP, logFetchStartTime),
            Filters.lt(Log.TIMESTAMP, logFetchEndTime)
        );
        switch(db){
            case TESTING: 
                logs = LogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
                break;
            case RUNTIME: 
                logs = RuntimeLogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
                break;
            case DASHBOARD: 
                logs = DashboardLogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
                break;
            case ANALYSER:
                logs = AnalyserLogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
        }
        return logs;
    }
}
