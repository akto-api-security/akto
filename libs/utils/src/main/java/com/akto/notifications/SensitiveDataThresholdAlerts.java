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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Accounts whose count indexes this process has already made sure of. */
    private static final Set<Integer> indexedAccounts = ConcurrentHashMap.newKeySet();

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

    /** Per (caller, callee) totals for one type from an hour on. Abstracted for tests. */
    public interface EdgeSource {
        List<SensitiveDataCountDao.Edge> edgeCountsSince(String subType, int fromHour);
    }

    /** Which end of a call a breach is about. */
    public enum Role {
        /** The service that sent the data, summed across everyone it sent to. */
        SENDER,
        /** The service that received it, summed across everyone that sent to it. */
        RECEIVER
    }

    public static class Breach {
        final String subType;
        final Role role;
        /** The breaching service: the sender for a SENDER breach, the receiver for a RECEIVER one. */
        final int apiCollectionId;
        final long count;
        final int threshold;
        final int windowDays;
        /** The other end, broken down: where it sent to, or who sent to it. Largest first. */
        final List<Peer> peers;

        Breach(String subType, Role role, int apiCollectionId, long count, int threshold, int windowDays,
               List<Peer> peers) {
            this.subType = subType;
            this.role = role;
            this.apiCollectionId = apiCollectionId;
            this.count = count;
            this.threshold = threshold;
            this.windowDays = windowDays;
            this.peers = peers;
        }

        String alertKey() {
            return keyFor(subType, role, apiCollectionId);
        }
    }

    /** One service at the other end of a breaching service's traffic. */
    public static class Peer {
        final int apiCollectionId;
        final long count;

        Peer(int apiCollectionId, long count) {
            this.apiCollectionId = apiCollectionId;
            this.count = count;
        }
    }

    /**
     * Used as a field name inside a document, so it must not contain '.' or '$'.
     *
     * The role is part of the key: one service can breach as a sender and as a receiver at once,
     * and those are two different alerts that must not suppress each other.
     */
    static String keyFor(String subType, Role role, int apiCollectionId) {
        return subType.replace('.', '_').replace('$', '_') + "|" + role.name() + "|" + apiCollectionId;
    }

    private SensitiveDataThresholdAlerts() {
    }

    /** Checks every active webhook with this option in the current account. */
    public static void run() {
        ensureCountIndexes();

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

    /**
     * The services that write the counts - mini-runtime and the database abstractor - never call
     * DaoInit.createIndices, so the collection can be created by the first increment with neither
     * index. Without the unique one each upsert scans the collection and concurrent runtimes can
     * create the same bucket twice; without the TTL nothing ever expires. Creating them here means
     * the feature does not depend on who started first.
     */
    static void ensureCountIndexes() {
        int accountId = Context.accountId.get();
        if (!indexedAccounts.add(accountId)) return;
        try {
            SensitiveDataCountDao.instance.createIndicesIfAbsent();
        } catch (Exception e) {
            indexedAccounts.remove(accountId);
            loggerMaker.errorAndAddToDb(e, "could not create sensitive data count indexes: " + e.getMessage());
        }
    }

    static void evaluate(CustomWebhook webhook, int now) {
        List<Breach> breaches = findBreaches(webhook.getSensitiveDataAlertRules(),
                webhook.getSensitiveDataAlertedAt(), now, SensitiveDataCountDao.instance::edgeCountsSince);
        if (breaches.isEmpty()) return;

        // An alert names both ends of a call, so a breach on a collection that cannot be named
        // would read as a bare hash. Those are dropped rather than sent half-legible.
        Map<Integer, String> names = collectionNames(breaches);
        List<Breach> named = new ArrayList<>();
        for (Breach b : breaches) {
            if (names.containsKey(b.apiCollectionId)) named.add(b);
        }
        if (named.isEmpty()) return;
        breaches = named;

        List<String> errors = new ArrayList<>();
        OriginalHttpResponse response = WebhookSender.sendCustomWebhook(webhook,
                buildPayload(breaches, names), errors, now, LogDb.DASHBOARD);
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
     * The breaches above their rule's threshold that have not alerted in the last
     * {@link #RESEND_AFTER_SECONDS}.
     *
     * One pass over the (caller, callee) totals yields both views: a service breaches as a SENDER
     * on what it sent in total, and as a RECEIVER on what it received in total. Summing both from
     * the same rows is what lets one alert name both ends - and means the two views cannot
     * disagree about the traffic they describe.
     *
     * The same traffic appears in both views by design: a sender's total includes what each of its
     * receivers counts. Alert totals therefore do not sum to distinct traffic.
     */
    static List<Breach> findBreaches(List<SensitiveDataAlertRule> rules, Map<String, Integer> alertedAt,
                                     int now, EdgeSource edges) {
        if (rules == null || rules.isEmpty()) return Collections.emptyList();
        Map<String, Integer> lastAlerted = alertedAt == null ? Collections.<String, Integer>emptyMap() : alertedAt;

        List<Breach> breaches = new ArrayList<>();
        for (SensitiveDataAlertRule rule : rules) {
            if (rule == null || !rule.isValid()) continue;

            int windowSeconds = rule.getWindowDays() * 86400;
            int fromHour = SensitiveDataCount.hourOf(now - windowSeconds);

            List<SensitiveDataCountDao.Edge> window = edges.edgeCountsSince(rule.getSubType(), fromHour);
            if (window == null || window.isEmpty()) continue;

            // sender -> (receiver -> count), and the mirror image of it
            Map<Integer, Map<Integer, Long>> bySender = new LinkedHashMap<>();
            Map<Integer, Map<Integer, Long>> byReceiver = new LinkedHashMap<>();
            for (SensitiveDataCountDao.Edge edge : window) {
                if (edge == null || edge.count <= 0) continue;
                bySender.computeIfAbsent(edge.senderCollectionId, k -> new LinkedHashMap<>())
                        .merge(edge.apiCollectionId, edge.count, Long::sum);
                byReceiver.computeIfAbsent(edge.apiCollectionId, k -> new LinkedHashMap<>())
                        .merge(edge.senderCollectionId, edge.count, Long::sum);
            }

            collectBreaches(breaches, rule, Role.SENDER, bySender, lastAlerted, now);
            collectBreaches(breaches, rule, Role.RECEIVER, byReceiver, lastAlerted, now);
        }
        return breaches;
    }

    /** Every service in {@code grouped} whose total is above the threshold and is not suppressed. */
    private static void collectBreaches(List<Breach> breaches, SensitiveDataAlertRule rule, Role role,
                                        Map<Integer, Map<Integer, Long>> grouped,
                                        Map<String, Integer> lastAlerted, int now) {
        for (Map.Entry<Integer, Map<Integer, Long>> e : grouped.entrySet()) {
            long total = 0;
            for (Long v : e.getValue().values()) total += v;
            if (total <= rule.getThreshold()) continue;

            Integer last = lastAlerted.get(keyFor(rule.getSubType(), role, e.getKey()));
            if (last != null && now - last < RESEND_AFTER_SECONDS) continue;

            List<Peer> peers = new ArrayList<>();
            for (Map.Entry<Integer, Long> p : e.getValue().entrySet()) {
                peers.add(new Peer(p.getKey(), p.getValue()));
            }
            peers.sort((a, b) -> Long.compare(b.count, a.count));

            breaches.add(new Breach(rule.getSubType(), role, e.getKey(), total, rule.getThreshold(),
                    rule.getWindowDays(), peers));
        }
    }

    private static Map<Integer, String> collectionNames(List<Breach> breaches) {
        List<Integer> ids = new ArrayList<>();
        for (Breach b : breaches) {
            ids.add(b.apiCollectionId);
            // The other end is named too, so an alert reads as "caller -> callee" rather than
            // naming one service and a bare id.
            for (Peer p : b.peers) ids.add(p.apiCollectionId);
        }
        Map<Integer, String> names = new HashMap<>();
        try {
            for (ApiCollection c : ApiCollectionsDao.instance.getMetaForIds(ids)) {
                String label = label(c);
                if (label != null) names.put(c.getId(), label);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "could not load collection names for sensitive data alert");
        }
        return names;
    }

    /**
     * What to call a collection in an alert.
     *
     * The service tag is preferred because it is the identity the counts are keyed by: one service
     * is one id whichever end of a call it is on. Collections routed by Host carry no tag, so the
     * host is used instead - for these it is the service's real address and reads as well as a tag
     * would. Note that it does NOT pool: two hosts of one service stay two destinations, each
     * measured against the threshold alone.
     *
     * Null when there is nothing readable, which is what keeps a bare hash out of an alert.
     */
    static String label(ApiCollection c) {
        String[] candidates = { c.getServiceTag(), c.getHostName(), c.getName() };
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) return candidate.trim();
        }
        return null;
    }

    /** A service's name, falling back to its id when no collection exists for it yet. */
    private static String nameOf(Map<Integer, String> names, int apiCollectionId) {
        return names.getOrDefault(apiCollectionId, String.valueOf(apiCollectionId));
    }

    /**
     * JSON with a summary "text" field, so the same payload also reads as a message when the
     * webhook URL is a Slack incoming webhook, plus the breaches as structured data.
     *
     * Every alert names both ends: the breaching service, and the services at the other end of its
     * traffic with what each of them accounts for.
     */
    static String buildPayload(List<Breach> breaches, Map<Integer, String> names) {
        StringBuilder text = new StringBuilder("Akto: sensitive data threshold crossed");
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Breach b : breaches) {
            String name = nameOf(names, b.apiCollectionId);
            boolean sending = b.role == Role.SENDER;

            text.append("\n• ").append(b.subType).append(": ").append(name)
                    .append(sending ? " sent " : " received ").append(b.count)
                    .append(" events in the last ").append(b.windowDays)
                    .append(" days (threshold ").append(b.threshold).append(")");

            List<Map<String, Object>> peers = new ArrayList<>();
            for (Peer p : b.peers) {
                String peerName = nameOf(names, p.apiCollectionId);
                text.append("\n    ").append(sending ? name + " -> " + peerName : peerName + " -> " + name)
                        .append(": ").append(p.count);

                Map<String, Object> peer = new LinkedHashMap<>();
                peer.put("apiCollectionId", p.apiCollectionId);
                peer.put("apiCollectionName", peerName);
                peer.put("count", p.count);
                peers.add(peer);
            }

            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("dataType", b.subType);
            alert.put("role", b.role.name());
            alert.put("apiCollectionId", b.apiCollectionId);
            alert.put("apiCollectionName", name);
            alert.put("count", b.count);
            alert.put("threshold", b.threshold);
            alert.put("windowDays", b.windowDays);
            alert.put(sending ? "destinations" : "sources", peers);
            alerts.add(alert);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text.toString());
        payload.put("alertType", CustomWebhook.WebhookOptions.SENSITIVE_DATA_THRESHOLD_ALERTS.name());
        payload.put("alerts", alerts);
        return gson.toJson(payload);
    }
}
