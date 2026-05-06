package com.akto.dao;

import com.mongodb.client.model.Filters;
import com.akto.dto.webhook_integration.WebhookIntegration;
import org.bson.conversions.Bson;

import java.util.List;

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

    /** Find the webhook integration for the current account and the given context source (one record per context source). */
    public WebhookIntegration findByContextSource(String contextSource) {
        if (contextSource == null || contextSource.trim().isEmpty()) {
            return null;
        }
        Bson filter = Filters.eq(WebhookIntegration.CONTEXT_SOURCE, contextSource.trim());
        return findOne(filter);
    }

    /** Find all webhook integrations for the current account (one per context source). Used by the sync cron. */
    public List<WebhookIntegration> findAllForAccount() {
        return findAll(Filters.empty());
    }
}
