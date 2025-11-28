package com.akto.log;

import com.akto.RuntimeMode;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.AccountSettings;
import com.akto.dto.Config;
import com.akto.dto.Log;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
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
import org.slf4j.simple.SimpleLogger;
import com.slack.api.Slack;

public class LoggerMaker {
    
    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, System.getenv().getOrDefault("AKTO_LOG_LEVEL", "WARN"));
        System.out.printf("AKTO_LOG_LEVEL is set to: %s \n", System.getProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY));
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka", "INFO");
        System.setProperty("org.slf4j.simpleLogger.log.io.lettuce", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.io.netty", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.org.flywaydb", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss");
    }

    public static final int LOG_SAVE_INTERVAL = 60*60; // 1 hour

    public final Logger logger;
    private final Class<?> aClass;

    private static String slackWebhookUrl;

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    protected static final Logger internalLogger = LoggerFactory.getLogger(LoggerMaker.class);
    private static final boolean shouldNotSendLogs = System.getenv("BLOCK_LOGS") != null && System.getenv("BLOCK_LOGS").equals("true");

    private static String moduleId = "";

    public static void setModuleId(String moduleId) {
        if (moduleId == null) {
            LoggerMaker.moduleId = "";
        } else {
            LoggerMaker.moduleId = moduleId;
        }
    }

    static {
        scheduler.scheduleAtFixedRate(new Runnable() {
            
            @Override
            public void run() {
                try {

                    if(RuntimeMode.isHybridDeployment()){
                        return;
                    }

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
        TESTING, RUNTIME, DASHBOARD, BILLING, ANALYSER
    }

    private static AccountSettings accountSettings = null;

    private static final ScheduledExecutorService scheduler2 = Executors.newScheduledThreadPool(1);

    static {
        scheduler2.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                updateAccountSettings();
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private static void updateAccountSettings() {
        try {
            internalLogger.info("Running updateAccountSettings....................................");
            accountSettings = dataActor.fetchAccountSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Deprecated
    public LoggerMaker(Class<?> c) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
    }

    public LoggerMaker(Class<?> c, LogDb db) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
        this.db = db;
    }

    public static void sendToSlack(String slackWebhookUrl, String message){
        if (slackWebhookUrl != null) {
            try {
                Slack slack = Slack.getInstance();
                BasicDBList sectionsList = new BasicDBList();
                BasicDBObject textObj = new BasicDBObject("type", "mrkdwn").append("text", message + "\n");
                BasicDBObject section = new BasicDBObject("type", "section").append("text", textObj);
                sectionsList.add(section);
                BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
                slack.send(slackWebhookUrl, ret.toJson());

            } catch (Exception e) {
                internalLogger.error("Can't send to Slack: " + e.getMessage(), e);
            }
        }
    }

    protected static void sendToSlack(String err) {
        sendToSlack(slackWebhookUrl, err);
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

    @Deprecated
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

    public void errorAndAddToDb(Exception e, String err) {
        errorAndAddToDb(e, err, this.db);
    }

    public void debugInfoAddToDb(String info) {
        debugInfoAddToDb(info, this.db);
    }

    public void debugInfoAddToDb(String info, LogDb db) {
        if (accountSettings == null || !accountSettings.isEnableDebugLogs()) return;
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

    private String formatMessageWithAccountId(String info) {
        String accountId = Context.accountId.get() != null ? Context.accountId.get().toString() : "NA";
        return "acc: " + accountId + ", " + info;
    }

    @Deprecated
    public void infoAndAddToDb(String info, LogDb db) {
        String infoMessage = formatMessageWithAccountId(info);
        logger.info(infoMessage);
        try {
            insert(infoMessage, "info", db);
        } catch (Exception e) {
        }
    }

    private void warnAndAddToDb(String info, LogDb db) {
        String infoMessage = formatMessageWithAccountId(info);
        logger.warn(infoMessage);
        try {
            insert(infoMessage, "warn", db);
        } catch (Exception e) {
        }
    }

    public void insertImportantTestingLog(String info) {
        String infoMessage = formatMessageWithAccountId(info);
        logger.info(infoMessage);
        if(checkUpdate()){
            String text = aClass + " : " + " [" + moduleId + " ] " + info;
            Log log = new Log(text, "info", Context.now());
            dataActor.insertTestingLog(log);
            logCount++;
        }
    }

    public void errorAndAddToDb(String err) {
        errorAndAddToDb(err, this.db);
    }

    public void infoAndAddToDb(String info) {
        infoAndAddToDb(info, this.db);
    }

    public void warnAndAddToDb(String info) {
        warnAndAddToDb(info, this.db);
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

        if(shouldNotSendLogs){
            return;
        }  

        if (moduleId == null) {
            moduleId = "";
        }

        String text = aClass + " : " + " [" + moduleId + " ] " + info;
        Log log = new Log(text, key, Context.now());
        
        if(checkUpdate() && db!=null){
            switch(db){
                case TESTING:
                    dataActor.insertTestingLog(log);
                    break;
                case RUNTIME: 
                    dataActor.insertRuntimeLog(log);
                    break;
                case DASHBOARD: 
                    DashboardLogsDao.instance.insertOne(log);
                    break;
                case ANALYSER:
                    dataActor.insertAnalyserLog(log);
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

    public void info(String msg){
        logger.info(msg);
    }

    public void info(String msg, Object... vars){
        logger.info(msg, vars);
    }

     public void warn(String msg){
        logger.warn(msg);
    }

    public void warn(String msg, Object... vars){
        logger.warn(msg, vars);
    }


    public void error(String msg){
        logger.error(msg);
    }

    public void error(String msg, Throwable t){
        logger.error(msg, t);
    }

    public void error(String msg, Object... vars){
        logger.error(msg, vars);
    }

    public void debug(String msg, Object... vars){
        logger.debug(msg, vars);
    }
}
