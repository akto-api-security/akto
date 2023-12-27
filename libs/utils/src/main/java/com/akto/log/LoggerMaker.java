package com.akto.log;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Log;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slack.api.Slack;

public class LoggerMaker  {

    public static final int LOG_SAVE_INTERVAL = 60*60; // 1 hour

    public final Logger logger;
    private final Class<?> aClass;

    private static String slackWebhookUrl;

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final Logger internalLogger = LoggerFactory.getLogger(LoggerMaker.class);

    private static final int CACHE_LIMIT = 50;
    private static HashMap<String, Integer> errorLogCache = new HashMap<>();

    static {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Config config = ConfigsDao.instance.findOne("_id", Config.SlackAlertConfig.CONFIG_ID);
                    if (config == null) {
                        return;
                    }

                    Config.SlackAlertConfig slackAlertConfig = (Config.SlackAlertConfig) config;
                    slackWebhookUrl = slackAlertConfig.getSlackWebhookUrl();
                } catch (Exception e) {
                    internalLogger.error("error in getting config: " + e.toString());
                }
            }
        }, 0, 1, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    clearErrorCache();
                } catch (Exception e) {
                    internalLogger.error("ERROR: In clearing log cache: " + e.toString());
                }
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    public static void clearErrorCache(){

        for(Map.Entry<String, Integer> err : errorLogCache.entrySet()){
            String errorMessage = err.getKey() + " : logged " + err.getValue() + " times";
            sendToSlack(errorMessage);
        }

        errorLogCache.clear();
    }

    private static int logCount = 0;
    private static int logCountResetTimestamp = Context.now();
    private static final int oneMinute = 60; 

    public enum LogDb {
        TESTING,RUNTIME,DASHBOARD,BILLING
    }

    public LoggerMaker(Class<?> c) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
    }

    private static void sendToSlack(String err) {
        if (slackWebhookUrl != null) {
            try {
                Slack slack = Slack.getInstance();
                BasicDBList sectionsList = new BasicDBList();
                BasicDBObject textObj = new BasicDBObject("type", "mrkdwn").append("text", err + "\n");
                BasicDBObject section = new BasicDBObject("type", "section").append("text", textObj);
                sectionsList.add(section);
                BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
                slack.send(slackWebhookUrl, ret.toJson());

            } catch (IOException e) {
                internalLogger.error("Can't send to Slack: " + e.getMessage(), e);
            }
        }
    }

    public void errorAndAddToDb(String err, LogDb db) {
        if(Context.accountId.get() != null){
            err = String.format("%s\nAccount id: %d", err, Context.accountId.get());
        }
        logger.error(err);
        try{
            insert(err, "error", db);
        } catch (Exception e){

        }

        if (db.equals(LogDb.BILLING) || db.equals(LogDb.DASHBOARD)) {
            if (cache) {
                try {
                    if (errorLogCache.containsKey(err)) {
                        errorLogCache.put(err, errorLogCache.get(err) + 1);
                    } else {
                        errorLogCache.put(err, 1);
                    }
                } catch (Exception e) {
                    internalLogger.error("ERROR: In adding to log cache: " + e.toString());
                }
            } else {
                sendToSlack(err);
            }
        }

        if (errorLogCache.size() > CACHE_LIMIT) {
            try {
                clearErrorCache();
            } catch (Exception e) {
                internalLogger.error("ERROR: In clearing log cache: " + e.toString());
            }
        }
    }

    public void errorAndAddToDb(Exception e, String err, LogDb db) {
        StackTraceElement stackTraceElement = e.getStackTrace()[0];
        err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
        errorAndAddToDb(err, db);
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
                case BILLING:
                    BillingLogsDao.instance.insertOne(log);
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
                break;
            case BILLING:
                logs = BillingLogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
        }
        return logs;
    }
}
