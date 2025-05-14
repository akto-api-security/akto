package com.akto.telemetry;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Log;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.util.EmailAccountName;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import okhttp3.*;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TelemetryJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TelemetryJob.class);
    private static final String telemetryUrl = "https://logs.akto.io/ingest";

    public static String getTelemetryUrl(){
        return telemetryUrl;
    }

    public void run(Organization org) {
        if (org == null) {
            loggerMaker.infoAndAddToDb("Organization is missing, skipping telemetry cron", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        if(org.getAccounts() == null || org.getAccounts().isEmpty()){
            loggerMaker.infoAndAddToDb("Organization has no accounts, skipping telemetry cron", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        loggerMaker.infoAndAddToDb(String.format("Running telemetry cron for org: %s, which has %d accounts",org.getName(), org.getAccounts().size()), LoggerMaker.LogDb.DASHBOARD);
        for (int accountId : org.getAccounts()) {
            try {
                Context.accountId.set(accountId);
                loggerMaker.infoAndAddToDb("Running telemetry cron for account:" + accountId, LoggerMaker.LogDb.DASHBOARD);
                AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                if (accountSettings == null) {
                    loggerMaker.infoAndAddToDb(String.format("AccountSettings is missing for account: %d, skipping telemetry cron", accountId), LoggerMaker.LogDb.DASHBOARD);
                    return;
                }
                if (accountSettings.getTelemetrySettings() == null) {
                    loggerMaker.infoAndAddToDb(String.format("TM settings missing in account settings for %d", accountId), LoggerMaker.LogDb.DASHBOARD);
                    return;
                }
                if(accountSettings.getTelemetrySettings().isTelemetryEnabled()){
                    loggerMaker.infoAndAddToDb(String.format("TM is enabled for account: %d", accountId), LoggerMaker.LogDb.DASHBOARD);
                } else {
                    loggerMaker.infoAndAddToDb(String.format("TM is disabled for account: %d", accountId), LoggerMaker.LogDb.DASHBOARD);
                    return;
                }

                int now = Context.now();
                //Fetch logs from lastRunTs to now
                fetchAndSendLogs(now, LoggerMaker.LogDb.TESTING, org, accountId, accountSettings.getDashboardVersion());
                fetchAndSendLogs(now, LoggerMaker.LogDb.DASHBOARD, org, accountId, accountSettings.getDashboardVersion());
                fetchAndSendLogs(now, LoggerMaker.LogDb.RUNTIME, org, accountId, accountSettings.getDashboardVersion());
                fetchAndSendLogs(now, LoggerMaker.LogDb.ANALYSER, org, accountId, accountSettings.getDashboardVersion());
                fetchAndSendLogs(now, LoggerMaker.LogDb.BILLING, org, accountId, accountSettings.getDashboardVersion());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("TM cron failed due to:" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            }

        }
    }

    private static AccountsContextDao<Log> getInstance(LoggerMaker.LogDb dbName){
        switch (dbName){
            case DASHBOARD:
                return DashboardLogsDao.instance;
            case DATA_INGESTION:
                return DataIngestionLogsDao.instance;
            case RUNTIME:
                return RuntimeLogsDao.instance;
            case ANALYSER:
                return AnalyserLogsDao.instance;
            case BILLING:
                return BillingLogsDao.instance;
            case PUPPETEER:
                return PupeteerLogsDao.instance;
            default:
                return LogsDao.instance;
        }
    }

    public static void fetchAndSendLogs(int toTs, LoggerMaker.LogDb dbName, Organization org, int accountId, String version){

      AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        Map<String, Integer> telemetryUpdateSentTsMap = accountSettings.getTelemetryUpdateSentTsMap();
        if (telemetryUpdateSentTsMap == null) {
            telemetryUpdateSentTsMap = new HashMap<>();
        }
        int skip = 0;
        int limit = 100;
        int fromTs = telemetryUpdateSentTsMap.getOrDefault(dbName.name(), 0);
        Bson filter = Filters.and(
                Filters.gte(Log.TIMESTAMP, fromTs),
                Filters.lte(Log.TIMESTAMP, toTs)
        );
        AccountsContextDao<Log> instance = getInstance(dbName);
        List<Log> logs;
        loggerMaker.infoAndAddToDb("Starting logs for " + dbName.name(), LoggerMaker.LogDb.DASHBOARD);
        do{
            //Send logs to telemetry server
            logs = instance.findAll(filter, skip, limit, Sorts.ascending(Log.TIMESTAMP), null);
            skip += limit;
            //send logs to telemetry server
            int lastLogTs = sendLogs(logs, dbName, org, accountId, version);
            if(lastLogTs <=0) {
                break;
            }
            AccountSettingsDao.instance.updateLastTelemetryUpdateSentTs(dbName.name(), lastLogTs);
            loggerMaker.infoAndAddToDb("Successfully completed a batch of " + logs.size() + " logs for " + dbName.name(), LoggerMaker.LogDb.DASHBOARD);
        }while (logs.size() == limit);
    }

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
                .writeTimeout(1,TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .callTimeout(1, TimeUnit.SECONDS)
                .build();

    private static int sendLogs(List<Log> logs, LoggerMaker.LogDb dbName, Organization org, int accountId, String version) {
        if(logs.isEmpty()){
            loggerMaker.infoAndAddToDb("Logs list is empty for db:" + dbName + " skipping", LoggerMaker.LogDb.DASHBOARD);
            return 0;
        }
        String adminEmail = org.getAdminEmail();
        BasicDBList telemetryLogs = logs.stream()
                .map(log ->
                        new BasicDBObject("_id", log.getId())
                                .append("log", log.getLog())
                                .append("key", log.getKey())
                                .append("timestamp", log.getTimestamp())
                                )
                .collect(Collectors.toCollection(BasicDBList::new));

        BasicDBObject data = new BasicDBObject("service", dbName.name())
                .append("logs", telemetryLogs)

                .append("orgId", org.getId())
                .append("orgName", new EmailAccountName(adminEmail).getAccountName())
                .append("accountId", accountId)
                .append("version", version)
                .append("adminEmail", org.getAdminEmail())
                .append("orgName", new EmailAccountName(org.getAdminEmail()).getAccountName())
                .append("type", "TELEMETRY");

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new BasicDBObject("data", data).toJson(), mediaType);
        Request request = new Request.Builder()
                .url(telemetryUrl)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = null;
        try {
            response =  client.newCall(request).execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return -1;
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return response.isSuccessful() ? logs.get(logs.size()-1).getTimestamp(): -1;
    }

}
