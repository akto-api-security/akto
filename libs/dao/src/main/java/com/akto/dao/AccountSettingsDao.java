package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;


public class AccountSettingsDao extends AccountsContextDao<AccountSettings> {

    public static Bson generateFilter() {
        return Filters.eq("_id", Context.accountId.get());
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

}
