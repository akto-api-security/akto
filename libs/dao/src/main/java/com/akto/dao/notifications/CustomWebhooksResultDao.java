package com.akto.dao.notifications;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.notifications.CustomWebhookResult;

public class CustomWebhooksResultDao extends AccountsContextDao<CustomWebhookResult>{

    public static final CustomWebhooksResultDao instance = new CustomWebhooksResultDao();

    private CustomWebhooksResultDao() {}

    @Override
    public String getCollName() {
        return "custom_webhooks_result";
    }

    @Override
    public Class<CustomWebhookResult> getClassT() {
        return CustomWebhookResult.class;
    }
    
}