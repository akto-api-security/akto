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

    public enum LogDb {
        TESTING,RUNTIME,DASHBOARD
    }

    public LoggerMaker(Class<?> c) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
    }

    public void errorAndAddToDb(String err, LogDb db) {
        logger.error(err);
        insert(err, "error", db);
    }

    public void infoAndAddToDb(String info, LogDb db) {
        logger.info(info);
        insert(info, "info",db);
    }

    private void insert(String info, String key, LogDb db) {
        String text = aClass + " : " + info;
        Log log = new Log(text, key, Context.now());
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
    }

    public List<Log> fetchLogRecords(int logFetchStartTime, int logFetchEndTime, LogDb db) {

        List<Log> logs = new ArrayList<>();

        if( logFetchStartTime > logFetchEndTime){
            return logs;
        }
        
        Bson filters = Filters.and(
            Filters.gte(Log.TIMESTAMP, logFetchStartTime),
            Filters.lte(Log.TIMESTAMP, logFetchEndTime)
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
