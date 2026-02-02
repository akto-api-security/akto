package com.akto.dao;

import com.akto.dto.webhook_integration.WebhookIntegration;

public class WebhookIntegrationDao extends AccountsContextDao<WebhookIntegration> {

    public static final WebhookIntegrationDao instance = new WebhookIntegrationDao();

    @Override
    public String getCollName() {
        return "threat_activity_webhook_integration";
    }

    @Override
    public Class<WebhookIntegration> getClassT() {
        return WebhookIntegration.class;
    }
}
