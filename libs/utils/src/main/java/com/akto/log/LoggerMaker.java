package com.akto.log;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Config;
import com.akto.dto.Log;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    protected static final Logger internalLogger = LoggerFactory.getLogger(LoggerMaker.class);

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
                    internalLogger.error("error in getting slack config: " + e.toString());
                }
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private static int logCount = 0;
    private static int logCountResetTimestamp = Context.now();
    private static final int oneMinute = 60; 

    private LogDb db;

    public enum LogDb {
        TESTING,RUNTIME,DASHBOARD,BILLING, ANALYSER
    }

    public LoggerMaker(Class<?> c) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
    }

    public LoggerMaker(Class<?> c, LogDb db) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
        this.db = db;
    }

    protected static void sendToSlack(String err) {
        if (slackWebhookUrl != null) {
            try {
                Slack slack = Slack.getInstance();
                BasicDBList sectionsList = new BasicDBList();
                BasicDBObject textObj = new BasicDBObject("type", "mrkdwn").append("text", err + "\n");
                BasicDBObject section = new BasicDBObject("type", "section").append("text", textObj);
                sectionsList.add(section);
                BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
                slack.send(slackWebhookUrl, ret.toJson());

            } catch (Exception e) {
                internalLogger.error("Can't send to Slack: " + e.getMessage(), e);
            }
        }
    }

    protected String basicError(String err, LogDb db) {
        if(Context.accountId.get() != null){
            err = String.format("%s\nAccount id: %d", err, Context.accountId.get());
        }
        logger.error(err);
        try{
            insert(err, "error", db);
        } catch (Exception e){

        }
        return err;
    }

    public void errorAndAddToDb(String err, LogDb db) {
        try {
            basicError(err, db);

            if (db.equals(LogDb.BILLING) || db.equals(LogDb.DASHBOARD)) {
                sendToSlack(err);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void debugInfoAddToDb(String info, LogDb db) {
        infoAndAddToDb(info, db);
    }
    
    public void errorAndAddToDb(Exception e, String err, LogDb db) {
        try {
            if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                StackTraceElement stackTraceElement = e.getStackTrace()[0];
                err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
            } else {
                err = String.format("Err msg: %s\nStackTrace not available", err);
                e.printStackTrace();
            }
            errorAndAddToDb(err, db);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    public void infoAndAddToDb(String info, LogDb db) {
        logger.info(info);
        try{
            insert(info, "info",db);
        } catch (Exception e){

        }
    }

    public void errorAndAddToDb(String err) {
        errorAndAddToDb(err, this.db);
    }

    public void infoAndAddToDb(String info) {
        infoAndAddToDb(info, this.db);
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
        
        if(checkUpdate() && db!=null){
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
                    break;
                case BILLING:
                    BillingLogsDao.instance.insertOne(log);
                    break;
                default:
                    break;
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
                break;
            case BILLING:
                logs = BillingLogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
                break;
            default:
                break;
        }
        return logs;
    }
}
