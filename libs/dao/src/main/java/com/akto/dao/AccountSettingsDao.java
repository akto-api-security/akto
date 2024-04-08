package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.util.ConnectionInfo;
import com.akto.util.LastCronRunInfo;
import com.akto.util.VersionUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.io.InputStream;
import java.util.Map;

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
                String version = VersionUtil.getVersion(in);
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
    
    public LastCronRunInfo getLastCronRunInfo(){
        AccountSettings account = instance.findOne(AccountSettingsDao.generateFilter());
        LastCronRunInfo timerInfo = account.getLastUpdatedCronInfo();
        if(timerInfo == null){
            return new LastCronRunInfo(0, 0, 0, 0);
        }
        return timerInfo;
    }

    public Map<String,ConnectionInfo> getIntegratedConnectionsInfo(){
        AccountSettings account = instance.findOne(AccountSettingsDao.generateFilter());
        Map<String,ConnectionInfo> connectionInfo = account.getConnectionIntegrationsInfo();
        return connectionInfo;
    }
}
