package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.User;
import com.akto.dto.type.CollectionReplaceDetails;
import com.akto.runtime.Main;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.apache.kafka.common.protocol.types.Field.Str;
import org.bson.conversions.Bson;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AdminSettingsAction extends UserAction {

    AccountSettings accountSettings;
    private int globalRateLimit = 0;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    @Override
    public String execute() throws Exception {
        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        return SUCCESS.toUpperCase();
    }

    public AccountSettings.SetupType setupType;
    public Boolean newMergingEnabled;

    public Boolean enableTelemetry;

    public String updateSetupType() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.SETUP_TYPE, this.setupType),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    public String updateGlobalRateLimit() {

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.GLOBAL_RATE_LIMIT, globalRateLimit));
        return SUCCESS.toUpperCase();
    }

    public String toggleNewMergingEnabled() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.URL_REGEX_MATCHING_ENABLED, this.newMergingEnabled),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    public String toggleTelemetry() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ENABLE_TELEMETRY, this.enableTelemetry),
                new UpdateOptions().upsert(true)
        );
        return SUCCESS.toUpperCase();
    }

    public String updateMergeAsyncOutside() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.MERGE_ASYNC_OUTSIDE, true),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    private int trafficAlertThresholdSeconds;
    public String updateTrafficAlertThresholdSeconds() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();

        if (trafficAlertThresholdSeconds > 3600*24*6) {
            // this was done because our lookback period to calculate last timestamp is 6 days
            addActionError("Alert can't be set for more than 6 days");
            return ERROR.toUpperCase();
        }

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.TRAFFIC_ALERT_THRESHOLD_SECONDS, trafficAlertThresholdSeconds),
                new UpdateOptions().upsert(true)
        );

        return SUCCESS.toUpperCase();
    }

    private boolean redactPayload;
    public String toggleRedactFeature() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) return ERROR.toUpperCase();

        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.combine(
                    Updates.set(AccountSettings.REDACT_PAYLOAD, redactPayload),
                    Updates.set(AccountSettings.SAMPLE_DATA_COLLECTION_DROPPED, false)
                ),
                new UpdateOptions().upsert(true)
        );


        if (!redactPayload) return SUCCESS.toUpperCase();

        dropCollectionsInitial(Context.accountId.get());

        int accountId = Context.accountId.get();

        executorService.schedule( new Runnable() {
            public void run() {
                dropCollections(accountId);
            }
        }, 3*Main.sync_threshold_time, TimeUnit.SECONDS);

        return SUCCESS.toUpperCase();
    }

    private static void dropCollectionsInitial(int accountId) {
        System.out.println("Dropping collection initial");
        Context.accountId.set(accountId);
        SampleDataDao.instance.getMCollection().drop();
        FilterSampleDataDao.instance.getMCollection().drop();
        SensitiveSampleDataDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.deleteValues();
    }

    public static void dropCollections(int accountId) {
        System.out.println("CALLED: " + Context.now());
        dropCollectionsInitial(accountId);
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.SAMPLE_DATA_COLLECTION_DROPPED, true), new UpdateOptions().upsert(true)
        );
    }

    private boolean enableDebugLogs;
    public String toggleDebugLogsFeature() {
        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ENABLE_DEBUG_LOGS, enableDebugLogs)
        );

        return SUCCESS.toUpperCase();
    }


    private Map<String, String> filterHeaderValueMap;

    public String addFilterHeaderValueMap() {
        Bson update;
        if (this.filterHeaderValueMap == null) {
            update = Updates.unset(AccountSettings.FILTER_HEADER_VALUE_MAP);
        } else {
            update = Updates.set(AccountSettings.FILTER_HEADER_VALUE_MAP, this.filterHeaderValueMap);
        }

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), update
        );

        return SUCCESS.toUpperCase();
    }

    private String regex;
    private String newName;
    private String headerName = "host";

    public String addApiCollectionNameMapper() {
        String hashStr = regex.hashCode()+"";
        Bson update = Updates.set(AccountSettings.API_COLLECTION_NAME_MAPPER+"."+hashStr, new CollectionReplaceDetails(regex, newName, headerName));

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), update
        );

        return SUCCESS.toUpperCase();
    }

    public String deleteApiCollectionNameMapper() {

        String hashStr = regex.hashCode()+"";

        Bson update = Updates.unset(AccountSettings.API_COLLECTION_NAME_MAPPER+"."+hashStr);

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), update
        );

        return SUCCESS.toUpperCase();
    }

    public AccountSettings getAccountSettings() {
        return this.accountSettings;
    }

    public void setRedactPayload(boolean redactPayload) {
        this.redactPayload = redactPayload;
    }

    public void setSetupType(AccountSettings.SetupType setupType) {
        this.setupType = setupType;
    }

    public Boolean getNewMergingEnabled() {
        return newMergingEnabled;
    }

    public void setNewMergingEnabled(Boolean newMergingEnabled) {
        this.newMergingEnabled = newMergingEnabled;
    }

    public void setEnableDebugLogs(boolean enableDebugLogs) {
        this.enableDebugLogs = enableDebugLogs;
    }

    public void setFilterHeaderValueMap(Map<String, String> filterHeaderValueMap) {
        this.filterHeaderValueMap = filterHeaderValueMap;
    }

    public Map<String, String> getFilterHeaderValueMap() {
        return filterHeaderValueMap;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public int getGlobalRateLimit() {
        return globalRateLimit;
    }

    public void setGlobalRateLimit(int globalRateLimit) {
        this.globalRateLimit = globalRateLimit;
    }

    public void setTrafficAlertThresholdSeconds(int trafficAlertThresholdSeconds) {
        this.trafficAlertThresholdSeconds = trafficAlertThresholdSeconds;
    }


    public Boolean getEnableTelemetry() {
        return enableTelemetry;
    }

    public void setEnableTelemetry(Boolean enableTelemetry) {
        this.enableTelemetry = enableTelemetry;
    }
}
