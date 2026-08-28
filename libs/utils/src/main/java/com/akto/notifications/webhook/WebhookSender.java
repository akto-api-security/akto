package com.akto.notifications.webhook;

import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class WebhookSender {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(WebhookSender.class, LogDb.DASHBOARD);

    private static void initLoggerMaker(LogDb db){
        loggerMaker.setDb(db);
    }

    public static void sendCustomWebhook(CustomWebhook webhook, String payload, List<String> errors, int now, LogDb db){

        initLoggerMaker(db);

        webhook.setLastSentTimestamp(now);
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", webhook.getId()), Updates.set("lastSentTimestamp", now));

        OriginalHttpRequest request = null;
        OriginalHttpResponse response = null; // null response means api request failed. Do not use new OriginalHttpResponse() in such cases else the string parsing fails.

        try {
            Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
            request = new OriginalHttpRequest(webhook.getUrl(), webhook.getQueryParams(), webhook.getMethod().toString(), payload, headers, "");
            response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            if (response == null || response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                String statusCode = response == null ? "null" : String.valueOf(response.getStatusCode());
                errors.add("Webhook endpoint returned non-2xx status: " + statusCode);
                loggerMaker.errorAndAddToDb("webhook request sent but got non-2xx response, status: " + statusCode + " webhookId: " + webhook.getId());
            } else {
                loggerMaker.infoAndAddToDb("webhook request sent", LogDb.DASHBOARD);
            }
        } catch (Exception e) {
            errors.add("API execution failed: " + e.getMessage());
            loggerMaker.errorAndAddToDb(e, "API execution failed for webhookId: " + webhook.getId());
        }

        String message = null;
        try {
            message = convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            errors.add("Failed converting sample data");
        }

        CustomWebhookResult webhookResult = new CustomWebhookResult(webhook.getId(), webhook.getUserEmail(), now, message, errors);
        CustomWebhooksResultDao.instance.insertOne(webhookResult);
    }

}
