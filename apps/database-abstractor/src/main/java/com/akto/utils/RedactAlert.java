package com.akto.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class RedactAlert {
    private static final ExecutorService executorService = Executors.newFixedThreadPool(20);
    private static final LoggerMaker loggerMaker = new LoggerMaker(RedactAlert.class, LogDb.DB_ABS);

    static final String regex = ".*\\*\\*\\*\\*.*";
    static final Pattern pattern = Pattern.compile(regex);
    static final String connectRegex = ".*CONNECT.*";
    static final Pattern connectPattern = Pattern.compile(connectRegex);

    private static final int CACHE_INTERVAL = 2 * 60;
    private static Map<Integer, Integer> lastFetchedMap = new HashMap<>();
    private static Map<Integer, Boolean> redactMap = new HashMap<>();

    private static boolean checkRedact() {
        int now = Context.now();
        int accountId = Context.accountId.get();
        if (redactMap.containsKey(accountId) &&
                lastFetchedMap.containsKey(accountId) &&
                lastFetchedMap.get(accountId) + CACHE_INTERVAL > now) {
            return redactMap.get(accountId);
        }

        redactMap.put(accountId, false);
        try{
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            if (accountSettings.isRedactPayload()) {
                redactMap.put(accountId, true);
            }
            lastFetchedMap.put(accountId, now);
        } catch (Exception e){
            loggerMaker.errorAndAddToDb(e, "Error in checkRedact");
        }
        return redactMap.get(accountId);
    }

    public static void sendToCyborgSlack(String message) {
        String slackCyborgWebhookUrl = null;
        try {
            Config.SlackAlertCyborgConfig slackCyborgWebhook = com.akto.onprem.Constants.getSlackAlertCyborgConfig();
            if (slackCyborgWebhook != null && slackCyborgWebhook.getSlackWebhookUrl() != null
                    && !slackCyborgWebhook.getSlackWebhookUrl().isEmpty()) {
                slackCyborgWebhookUrl = slackCyborgWebhook.getSlackWebhookUrl();
                LoggerMaker.sendToSlack(slackCyborgWebhookUrl, message);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Unable to send slack alert");
        }
    }


    private static void checkRedactedDataAndSendAlert(List<String> data,
            int apiCollectionId, String method, String url) {

        /*
         * This condition fails if the sample only
         * contains host request header and nothing else.
         * This was being observed in CONNECT APIs,
         * thus added an additional check for that.
         */
        for (String d : data) {
            if (!pattern.matcher(d).matches() && !connectPattern.matcher(d).matches()) {
                int accountId = Context.accountId.get();
                String message = String.format("Un-redacted sample data coming for account %d for API: %d %s %s",
                        accountId, apiCollectionId, method, url);
                sendToCyborgSlack(message);
            }
        }
    }

    public static void submitSampleDataForChecking(List<String> data,
            int apiCollectionId, String method, String url) {
        int accountId = Context.accountId.get();

        if (!checkRedact()) {
            return;
        }
        executorService.submit(() -> {
            Context.accountId.set(accountId);
            try {
                checkRedactedDataAndSendAlert(data, apiCollectionId, method, url);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in check redact and send alert" + e.getMessage());
            }
        });
    }

    public static void submitSensitiveSampleDataCall(int apiCollectionId) {
        int accountId = Context.accountId.get();

        if (!checkRedact()) {
            return;
        }
        executorService.submit(() -> {
            Context.accountId.set(accountId);
            String message = String.format(
                    "Unredacted sensitive sample data coming for account %d for API collection: %d",
                    accountId, apiCollectionId);
            sendToCyborgSlack(message);
        });
    }
}