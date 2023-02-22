package com.akto.log;

import com.akto.dao.DashboardLogsDao;
import com.akto.dao.LogsDao;
import com.akto.dao.RuntimeLogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerMaker  {

    public final Logger logger;
    private final Class<?> aClass;

    private static int logCount = 0;
    private static int logCountResetTimestamp = Context.now();
    private static final int ONEMINUTE = 60; 

    public enum LogDb {
        TESTING,RUNTIME,DASHBOARD
    }

    private LogDb db;

    public LoggerMaker(Class<?> c, LogDb db) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
        this.db = db;
    }

    public void errorAndAddToDb(String err) {
        logger.error(err);
        try{
            insert(err, "error");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void infoAndAddToDb(String info) {
        logger.info(info);
        try{
            insert(info, "info");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static Boolean checkUpdate(){
        if(logCount>=1000){
            if((logCountResetTimestamp + ONEMINUTE) >= Context.now()){
                return false;
            } else {
                logCount = 0;
                logCountResetTimestamp = Context.now();
            }
        }
        return true;
    }

    private static void updateLogCount(){
        logCount++;
    }
    
    private void insert(String info, String key) {
        String text = aClass + " : " + info;
        Log log = new Log(text, key, Context.now());
        
        if(Boolean.TRUE.equals(checkUpdate())){
            switch(db){
                case TESTING: 
                    LogsDao.instance.insertOne(log);
                    break;
                case RUNTIME: 
                    RuntimeLogsDao.instance.insertOne(log);
                    break;
                case DASHBOARD: 
                    DashboardLogsDao.instance.insertOne(log);
            }
            updateLogCount();
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
        }
        return logs;
    }
}
