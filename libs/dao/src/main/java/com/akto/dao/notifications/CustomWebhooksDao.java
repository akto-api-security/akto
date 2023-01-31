package com.akto.dao.notifications;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.notifications.CustomWebhook;

public class CustomWebhooksDao extends AccountsContextDao<CustomWebhook>{

    public static final CustomWebhooksDao instance = new CustomWebhooksDao();

    private CustomWebhooksDao() {}

    @Override
    public String getCollName() {
        return "custom_webhooks";
    }

    @Override
    public Class<CustomWebhook> getClassT() {
        return CustomWebhook.class;
    }
    
}