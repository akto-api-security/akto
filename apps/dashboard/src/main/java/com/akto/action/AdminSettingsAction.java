package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.runtime.Main;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AdminSettingsAction extends UserAction {

    AccountSettings accountSettings;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    @Override
    public String execute() throws Exception {
        accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        return SUCCESS.toUpperCase();
    }

    public AccountSettings.SetupType setupType;
    public String updateSetupType() {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.SETUP_TYPE, this.setupType),
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

    private boolean redactPayload;
    public String toggleRedactFeature() {
        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId());
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

    public AccountSettings getAccountSettings() {
        return this.accountSettings;
    }

    public void setRedactPayload(boolean redactPayload) {
        this.redactPayload = redactPayload;
    }

    public void setSetupType(AccountSettings.SetupType setupType) {
        this.setupType = setupType;
    }
}
