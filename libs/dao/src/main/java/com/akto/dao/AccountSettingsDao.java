package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.util.VersionUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.io.InputStream;

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

    public void updateOnboardingFlag(boolean value) {
        instance.updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.SHOW_ONBOARDING, value)
        );
    }
}
