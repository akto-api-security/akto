package com.akto.notifications;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveDataCountDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.SensitiveDataAlertRule;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.webhook.WebhookSender;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends custom webhooks configured with {@link CustomWebhook.WebhookOptions#SENSITIVE_DATA_THRESHOLD_ALERTS}.
 *
 * Runs from the hourly custom-webhook pass, independent of each webhook's own frequency. For each rule it sums the hourly event
 * counts the runtime stores in sensitive_data_counts over the rule's window, per API collection,
 * and alerts on every collection above the threshold. A (data type, collection) pair alerts at
 * most once a day for as long as it keeps breaching, so a collection that stays above the
 * threshold does not alert every hour.
 */
public class SensitiveDataThresholdAlerts {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SensitiveDataThresholdAlerts.class, LogDb.DASHBOARD);
    private static final Gson gson = new Gson();

    /**
     * How long a (data type, collection) pair stays quiet after it alerts.
     *
     * The window is rolling, so once a spike lands the total stays above the threshold for the
     * whole window - without suppression a single incident on a 14 day rule would alert every
     * hour, 336 times. Suppressing for the window instead goes too far the other way: a second
     * spike, or the count growing tenfold, would pass unnoticed until the window elapsed. A day
     * is the compromise: at most one alert per pair per day for as long as it keeps breaching.
     */
    public static final int RESEND_AFTER_SECONDS = 86400;

    /** Totals per collection for one type from an hour on, above a count. Abstracted for tests. */
    public interface CountSource {
        Map<Integer, Long> countsByCollectionAbove(String subType, int fromHour, long moreThan);
    }

    public static class Breach {
        final String subType;
        final int apiCollectionId;
        final long count;
        final int threshold;
        final int windowDays;

        Breach(String subType, int apiCollectionId, long count, int threshold, int windowDays) {
            this.subType = subType;
            this.apiCollectionId = apiCollectionId;
            this.count = count;
            this.threshold = threshold;
            this.windowDays = windowDays;
        }

        String alertKey() {
            return keyFor(subType, apiCollectionId);
        }
    }

    /** Used as a field name inside a document, so it must not contain '.' or '$'. */
    static String keyFor(String subType, int apiCollectionId) {
        return subType.replace('.', '_').replace('$', '_') + "|" + apiCollectionId;
    }

    private SensitiveDataThresholdAlerts() {
    }

    /** Checks every active webhook with this option in the current account. */
    public static void run() {
        List<CustomWebhook> webhooks = CustomWebhooksDao.instance.findAll(Filters.and(
                Filters.in(CustomWebhook.SELECTED_WEBHOOK_OPTIONS,
                        CustomWebhook.WebhookOptions.SENSITIVE_DATA_THRESHOLD_ALERTS.name()),
                Filters.eq("activeStatus", CustomWebhook.ActiveStatus.ACTIVE.name())));
        if (webhooks == null || webhooks.isEmpty()) return;

        int now = Context.now();
        for (CustomWebhook webhook : webhooks) {
            try {
                evaluate(webhook, now);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "sensitive data threshold alert failed for webhook "
                        + webhook.getId() + ": " + e.getMessage());
            }
        }
    }

    static void evaluate(CustomWebhook webhook, int now) {
        List<Breach> breaches = findBreaches(webhook.getSensitiveDataAlertRules(),
                webhook.getSensitiveDataAlertedAt(), now, SensitiveDataCountDao.instance::countsByCollectionAbove);
        if (breaches.isEmpty()) return;

        List<String> errors = new ArrayList<>();
        OriginalHttpResponse response = WebhookSender.sendCustomWebhook(webhook,
                buildPayload(breaches, collectionNames(breaches)), errors, now, LogDb.DASHBOARD);
        int status = response == null ? 0 : response.getStatusCode();
        if (status < 200 || status >= 300) {
            // Not marked as alerted, so the next run tries again.
            loggerMaker.errorAndAddToDb("sensitive data threshold alert for webhook " + webhook.getId()
                    + " was not delivered: status " + status + " " + errors);
            return;
        }

        List<Bson> marks = new ArrayList<>();
        for (Breach b : breaches) {
            marks.add(Updates.set(CustomWebhook.SENSITIVE_DATA_ALERTED_AT + "." + b.alertKey(), now));
        }
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", webhook.getId()), Updates.combine(marks));
    }

    /**
     * The (type, collection) pairs above their rule's threshold that have not alerted in the last
     * {@link #RESEND_AFTER_SECONDS}.
     */
    static List<Breach> findBreaches(List<SensitiveDataAlertRule> rules, Map<String, Integer> alertedAt,
                                     int now, CountSource counts) {
        if (rules == null || rules.isEmpty()) return Collections.emptyList();
        Map<String, Integer> lastAlerted = alertedAt == null ? Collections.<String, Integer>emptyMap() : alertedAt;

        List<Breach> breaches = new ArrayList<>();
        for (SensitiveDataAlertRule rule : rules) {
            if (rule == null || !rule.isValid()) continue;

            int windowSeconds = rule.getWindowDays() * 86400;
            int fromHour = SensitiveDataCount.hourOf(now - windowSeconds);
            Map<Integer, Long> above = counts.countsByCollectionAbove(rule.getSubType(), fromHour, rule.getThreshold());

            for (Map.Entry<Integer, Long> e : above.entrySet()) {
                Integer last = lastAlerted.get(keyFor(rule.getSubType(), e.getKey()));
                if (last != null && now - last < RESEND_AFTER_SECONDS) continue;
                breaches.add(new Breach(rule.getSubType(), e.getKey(), e.getValue(), rule.getThreshold(), rule.getWindowDays()));
            }
        }
        return breaches;
    }

    private static Map<Integer, String> collectionNames(List<Breach> breaches) {
        List<Integer> ids = new ArrayList<>();
        for (Breach b : breaches) ids.add(b.apiCollectionId);
        Map<Integer, String> names = new HashMap<>();
        try {
            for (ApiCollection c : ApiCollectionsDao.instance.getMetaForIds(ids)) {
                String name = c.getDisplayName();
                if (name == null || name.isEmpty()) name = c.getHostName();
                if (name != null) names.put(c.getId(), name);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "could not load collection names for sensitive data alert");
        }
        return names;
    }

    /**
     * JSON with a summary "text" field, so the same payload also reads as a message when the
     * webhook URL is a Slack incoming webhook, plus the breaches as structured data.
     */
    static String buildPayload(List<Breach> breaches, Map<Integer, String> names) {
        StringBuilder text = new StringBuilder("Akto: sensitive data threshold crossed");
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Breach b : breaches) {
            String name = names.getOrDefault(b.apiCollectionId, String.valueOf(b.apiCollectionId));
            text.append("\n• ").append(b.subType).append(" in ").append(name).append(": ")
                    .append(b.count).append(" events in the last ").append(b.windowDays)
                    .append(" days (threshold ").append(b.threshold).append(")");

            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("dataType", b.subType);
            alert.put("apiCollectionId", b.apiCollectionId);
            alert.put("apiCollectionName", name);
            alert.put("count", b.count);
            alert.put("threshold", b.threshold);
            alert.put("windowDays", b.windowDays);
            alerts.add(alert);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text.toString());
        payload.put("alertType", CustomWebhook.WebhookOptions.SENSITIVE_DATA_THRESHOLD_ALERTS.name());
        payload.put("alerts", alerts);
        return gson.toJson(payload);
    }
}
