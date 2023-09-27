package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AccountSettingsDao extends AccountsContextDao<AccountSettings> {

    public static Bson generateFilter() {
        return generateFilter(Context.accountId.get());
    }

    public static Bson generateFilter(int accountId) {
        return Filters.eq("_id", accountId);
    }

    public static final AccountSettingsDao instance = new AccountSettingsDao();

    @Override
    public String getCollName() {
        return "accounts_settings";
    }

    @Override
    public Class<AccountSettings> getClassT() {
        return AccountSettings.class;
    }

    public void updateVersion(String fieldName) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/version.txt")) {
            if (in != null) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
                String imageTag = bufferedReader.readLine();
                String buildTime = bufferedReader.readLine();
                String aktoVersion = bufferedReader.readLine();
                String version = imageTag + " - " + buildTime + " - " + aktoVersion;
                AccountSettingsDao.instance.updateOne(
                        AccountSettingsDao.generateFilter(),
                        Updates.set(fieldName, version)
                );
            } else  {
                throw new Exception("Input stream null");
            }
        }
    }

    public void updateInitStackType(String initStackType) {
        instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.INIT_STACK_TYPE, initStackType)
        );
    }

    public void updateOnboardingFlag(boolean value) {
        instance.updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.SHOW_ONBOARDING, value)
        );
    }

    public String getInitStackType() {
        return instance.findOne(AccountSettingsDao.generateFilter()).getInitStackType();
    }

    public void updateLastTelemetryUpdateSentTs(String dbName, int ts) {
        instance.updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.TELEMETRY_UPDATE_SENT_TS_MAP + "." + dbName, ts)
        );
    }
}
