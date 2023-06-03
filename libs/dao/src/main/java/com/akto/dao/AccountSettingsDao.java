package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class AccountSettingsDao extends AccountsContextDao<AccountSettings> {

    public static Bson generateFilter() {
        return generateFilter(Context.accountId.get());
    }

    public static Bson generateFilter(int accountId) {
        return Filters.eq("_id", accountId);
    }

    public static final AccountSettingsDao instance = new AccountSettingsDao();

    private static final Gson gson = new Gson();

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

                String version = imageTag + " - " + buildTime;
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

    public void updateRunCreateStiViewFlag(boolean value) {
        instance.updateOne(
            AccountSettingsDao.generateFilter(),
            Updates.set(AccountSettings.RUN_CREATE_STI_VIEW, value)
        );
    }

    public Boolean updateStiViewFlag(boolean value, String flag) {
        Object obj = instance.updateMany(
                AccountSettingsDao.generateFilter(),
                Updates.set(flag, value));

        ObjectMapper mapper = new ObjectMapper();
        String message;
        try {
            message = mapper.writeValueAsString(obj);
            Map<String, Object> json = gson.fromJson(message, Map.class);
            double modifiedCount = (double) json.get("modifiedCount");
            int count = (int) modifiedCount;
            return count > 0;
        } catch (Exception e) {
            return false;
        }
        
    }
}
