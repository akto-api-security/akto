package com.akto.dao;

import com.akto.dto.AccountSettings;

public class AccountSettingsDao extends AccountsContextDao<AccountSettings> {

    public static final AccountSettingsDao instance = new AccountSettingsDao();

    @Override
    public String getCollName() {
        return "accounts_settings";
    }

    @Override
    public Class<AccountSettings> getClassT() {
        return AccountSettings.class;
    }
}
