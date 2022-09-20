package com.akto.dao.notifications;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.notifications.CustomWebhookResult;

public class CustomWebhookResultDao extends AccountsContextDao<CustomWebhookResult>{

    public static final CustomWebhookResultDao instance = new CustomWebhookResultDao();

    private CustomWebhookResultDao() {}

    @Override
    public String getCollName() {
        return "custom_webhooks_result";
    }

    @Override
    public Class<CustomWebhookResult> getClassT() {
        return CustomWebhookResult.class;
    }
    
}