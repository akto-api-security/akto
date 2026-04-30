package com.akto.log;

import com.akto.dao.AgenticTestingLogsDao;
import com.akto.dao.AnalyserLogsDao;
import com.akto.dao.AwsApiGatewayLogsDao;
import com.akto.dao.BillingLogsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.DashboardLogsDao;
import com.akto.dao.LogsDao;
import com.akto.dao.PupeteerLogsDao;
import com.akto.dao.RuntimeLogsDao;
import com.akto.dao.monitoring.EndpointShieldLogsDao;
import com.akto.dto.monitoring.EndpointShieldLog;
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
import com.mongodb.client.model.Sorts;
import com.slack.api.Slack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.simple.SimpleLogger;

public class LoggerMaker  {

    /** Keys persisted by {@link #insert(String, String, LogDb)} for dashboard-style logs. */
    public static final Set<String> STORED_LOG_KEYS = new HashSet<>(Arrays.asList("error", "info", "warn", "debug"));

    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, System.getenv().getOrDefault("AKTO_LOG_LEVEL", "WARN"));
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.kafka", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.io.lettuce", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.io.netty", "ERROR");
        System.setProperty("org.slf4j.simpleLogger.log.org.flywaydb", "ERROR");
        System.out.printf("AKTO_LOG_LEVEL is set to: %s \n", System.getProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY));
    }

    public static final int LOG_SAVE_INTERVAL = 60*60; // 1 hour

    public final Logger logger;
    private final Class<?> aClass;

    private static String slackWebhookUrl;
    private static int counter = 0;

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    protected static final Logger internalLogger = LoggerFactory.getLogger(LoggerMaker.class);

    private static final boolean shouldNotSendLogs = System.getenv("BLOCK_LOGS") != null && System.getenv("BLOCK_LOGS").equals("true");

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

    public void setDb(LogDb db) {
        this.db = db;
    }

    public enum LogDb {
        TESTING,RUNTIME,DASHBOARD,BILLING, ANALYSER, THREAT_DETECTION, PUPPETEER, DATA_INGESTION, ENDPOINT_SHIELD, AGENTIC_TESTING, AWS_API_GATEWAY
    }

    private static AccountSettings accountSettings = null;

    private static final ScheduledExecutorService scheduler2 = Executors.newScheduledThreadPool(1);

    static {
        scheduler2.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                String cliTestIds = System.getenv("TEST_IDS");
                if(cliTestIds==null && Context.accountId.get() == 1_000_000){
                    updateAccountSettings();
                }
            }
        }, 0, 2, TimeUnit.MINUTES);
    }


    private static void updateAccountSettings() {
        try {
            internalLogger.info("Running updateAccountSettings....................................");
            Context.accountId.set(1_000_000);
            accountSettings = dataActor.fetchAccountSettingsForAccount(1_000_000);
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
            err = basicError(err, db);

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

    public void debugInfoAddToDb(String info, LogDb db) {
        if (accountSettings == null || !accountSettings.isEnableDebugLogs()) return;
        infoAndAddToDb(info, db);
    }

    @Deprecated
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

    @Deprecated
    public void infoAndAddToDb(String info, LogDb db) {
        String accountId = Context.accountId.get() != null ? Context.accountId.get().toString() : "NA";
        String infoMessage = "acc: " + accountId + ", " + info;
        logger.info(infoMessage);
        try{
            insert(infoMessage, "info",db);
        } catch (Exception e){

        }
    }

    public void warnAndAddToDb(String info, LogDb db) {
        String accountId = Context.accountId.get() != null ? Context.accountId.get().toString() : "NA";
        String infoMessage = "acc: " + accountId + ", " + info;
        logger.warn(infoMessage);
        try{
            insert(infoMessage, "warn",db);
        } catch (Exception e){

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
        
        if(shouldNotSendLogs || (accountSettings != null && accountSettings.isBlockLogs())) {
            return;
        }

        String text = aClass + " : " + info;
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
                case DATA_INGESTION:
                    dataActor.insertDataIngestionLog(log);
                    break;
                case ANALYSER:
                    dataActor.insertAnalyserLog(log);
                    break;
                case BILLING:
                    BillingLogsDao.instance.insertOne(log);
                    break;
                case THREAT_DETECTION:
                    dataActor.insertProtectionLog(log);
                    break;
                case AGENTIC_TESTING:
                    dataActor.insertAgenticTestingLog(log);
                    break;
                default:
                    break;
            }
            logCount++;
        }
    }

    public List<Log> fetchLogRecords(int logFetchStartTime, int logFetchEndTime, LogDb db) {
        return fetchLogRecords(logFetchStartTime, logFetchEndTime, db, null);
    }

    public List<Log> fetchLogRecords(int logFetchStartTime, int logFetchEndTime, LogDb db, List<String> logKeysFilter) {

        List<Log> logs = new ArrayList<>();

        if( logFetchStartTime > logFetchEndTime){
            return logs;
        }

        Bson filters = buildTimeAndKeysFilter(logFetchStartTime, logFetchEndTime, logKeysFilter);
        Bson sortAscending = Sorts.ascending(Log.TIMESTAMP);
        Bson standardProjection = Projections.include("log", Log.TIMESTAMP, Log.KEY);

        switch(db){
            case TESTING:
                logs = LogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case RUNTIME:
                logs = RuntimeLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case DASHBOARD:
                logs = DashboardLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case DATA_INGESTION:
                logs = DataIngestionLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case ANALYSER:
                logs = AnalyserLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case BILLING:
                logs = BillingLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case PUPPETEER:
                logs = PupeteerLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case THREAT_DETECTION:
                logs = ProtectionLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case ENDPOINT_SHIELD:
                Bson shieldProjection = Projections.include(
                    "log", Log.TIMESTAMP, Log.KEY,
                    EndpointShieldLog.AGENT_ID, EndpointShieldLog.DEVICE_ID, EndpointShieldLog.LEVEL);
                List<EndpointShieldLog> endpointShieldLogs = EndpointShieldLogsDao.instance.findAll(
                    filters, 0, 1_000_000, sortAscending, shieldProjection);
                logs = new ArrayList<>(endpointShieldLogs);
                break;
            case AGENTIC_TESTING:
                logs = AgenticTestingLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            case AWS_API_GATEWAY:
                logs = AwsApiGatewayLogsDao.instance.findAll(filters, 0, 1_000_000, sortAscending, standardProjection);
                break;
            default:
                break;
        }
        return logs;
    }

    private static Bson buildTimeAndKeysFilter(int logFetchStartTime, int logFetchEndTime, List<String> logKeysFilter) {
        Bson timeRange = Filters.and(
            Filters.gte(Log.TIMESTAMP, logFetchStartTime),
            Filters.lt(Log.TIMESTAMP, logFetchEndTime)
        );
        List<String> normalized = normalizeLogKeysFilter(logKeysFilter);
        if (normalized == null || normalized.size() >= STORED_LOG_KEYS.size()) {
            return timeRange;
        }
        return Filters.and(timeRange, Filters.in(Log.KEY, normalized));
    }

    /**
     * @return null if no key filter should be applied (show all known keys / empty request).
     */
    private static List<String> normalizeLogKeysFilter(List<String> logKeysFilter) {
        if (logKeysFilter == null || logKeysFilter.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String k : logKeysFilter) {
            if (k == null) {
                continue;
            }
            String t = k.trim().toLowerCase();
            if (STORED_LOG_KEYS.contains(t) && !out.contains(t)) {
                out.add(t);
            }
        }
        return out.isEmpty() ? null : out;
    }

    public void info(String message, Object... vars) {
        logger.info(message, vars);
    }

    public void error(String errorMessage, Object... vars) {
        logger.error(errorMessage, vars);
    }

    public void debug(String message, Object... vars) {
        logger.debug(message, vars);
    }

    public void warn(String message, Object... vars) {
        logger.warn(message, vars);
    }

    public void debugAndAddToDb(String message) {
        debugAndAddToDb(message, this.db);
    }

    public void debugAndAddToDbCount(String message) {
        if(counter > 500){
            return;
        }
        counter++;
        debugAndAddToDb(message, this.db);
    }

    @Deprecated
    public void debugAndAddToDb(String message, LogDb db) {
        String accountId = Context.accountId.get() != null ? Context.accountId.get().toString() : "NA";
        String debugMessage = "acc: " + accountId + ", " + message;
        debug(debugMessage);
        try{
            insert(debugMessage, "debug", db);
        } catch (Exception e){

        }
    }
}
