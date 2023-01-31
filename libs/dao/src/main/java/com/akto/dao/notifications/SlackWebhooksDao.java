package com.akto.dao.notifications;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.notifications.SlackWebhook;

public class SlackWebhooksDao extends AccountsContextDao<SlackWebhook> {

    public static final SlackWebhooksDao instance = new SlackWebhooksDao();

    private SlackWebhooksDao() {}

    @Override
    public String getCollName() {
        return "slack_webhooks";
    }

    @Override
    public Class<SlackWebhook> getClassT() {
        return SlackWebhook.class;
    }
    
}
