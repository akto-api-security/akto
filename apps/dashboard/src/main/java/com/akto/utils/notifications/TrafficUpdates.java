package com.akto.utils.notifications;

import com.akto.calendar.DateUtils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsAlertsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetricsAlert;
import com.akto.notifications.slack.DailyUpdate;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.slack.api.Slack;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;

public class TrafficUpdates {

    private final String webhookUrl;
    private final String metricsUrl;

    public TrafficUpdates(String webhookUrl, String metricsUrl) {
        this.webhookUrl = webhookUrl;
        this.metricsUrl = metricsUrl;
    }

    enum AlertType {
        OUTGOING_REQUESTS_MIRRORING,
        FILTERED_REQUESTS_RUNTIME
    }

    public void run() {
        populateTrafficDetails(AlertType.OUTGOING_REQUESTS_MIRRORING);
        populateTrafficDetails(AlertType.FILTERED_REQUESTS_RUNTIME);

        List<TrafficMetricsAlert> trafficMetricsAlertList = TrafficMetricsAlertsDao.instance.findAll(new BasicDBObject());
        List<TrafficMetricsAlert> filteredTrafficMetricsAlertsList = filterTrafficMetricsAlertsList(trafficMetricsAlertList);

        sendAlerts(60,AlertType.OUTGOING_REQUESTS_MIRRORING, filteredTrafficMetricsAlertsList);
        sendAlerts(60, AlertType.FILTERED_REQUESTS_RUNTIME, filteredTrafficMetricsAlertsList);
    }

    public List<TrafficMetricsAlert> filterTrafficMetricsAlertsList(List<TrafficMetricsAlert> trafficMetricsAlertList) {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaAll();
        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();
        Set<String> allowedHosts = new HashSet<>();
        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            if (count ==  null) continue;
            if (count > 20) allowedHosts.add(apiCollection.getHostName()) ;
        }

        List<TrafficMetricsAlert> filteredTrafficMetricsAlertsList = new ArrayList<>();
        for (TrafficMetricsAlert trafficMetricsAlert: trafficMetricsAlertList) {
            String host = trafficMetricsAlert.getHost();
            if (allowedHosts.contains(host)) {
                filteredTrafficMetricsAlertsList.add(trafficMetricsAlert);
            }
        }

        return filteredTrafficMetricsAlertsList;
    }

    public void populateTrafficDetails(AlertType alertType) {

        TrafficMetrics.Name name;
        switch (alertType) {
            case OUTGOING_REQUESTS_MIRRORING:
                name = TrafficMetrics.Name.OUTGOING_REQUESTS_MIRRORING;
                break;
            case FILTERED_REQUESTS_RUNTIME:
                name = TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME;
                break;
            default:
                return;
        }

        // db script to get all <unique host: last_ts> map
        List<Bson> pipeline = new ArrayList<>();

        Document idExpression = new Document("host", "$_id.host");
        pipeline.add(Aggregates.match(Filters.eq("_id.name", name.toString())));
        pipeline.add(Aggregates.project(Document.parse("{'countMap': { '$objectToArray': \"$countMap\" }}")));
        pipeline.add(Aggregates.unwind("$countMap"));
        pipeline.add(Aggregates.group(
                idExpression,
                Accumulators.max("maxTimestamp", "$countMap.k")
        ));

        MongoCursor<BasicDBObject> endpointsCursor = TrafficMetricsDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<WriteModel<TrafficMetricsAlert>> updates = new ArrayList<>();

        while(endpointsCursor.hasNext()) {
            BasicDBObject a = endpointsCursor.next();
            Integer ts = Integer.parseInt(a.getString("maxTimestamp"));
            String host = ((BasicDBObject) a.get("_id")).getString("host");

            Bson update;

            switch (name) {
                case OUTGOING_REQUESTS_MIRRORING:
                    update = Updates.set(TrafficMetricsAlert.LAST_OUTGOING_TRAFFIC_TS, ts);
                    break;
                case FILTERED_REQUESTS_RUNTIME:
                    update = Updates.set(TrafficMetricsAlert.LAST_DB_UPDATE_TS, ts);
                    break;
                default:
                    continue;
            }

            updates.add(
                    new UpdateManyModel<TrafficMetricsAlert>(
                            Filters.and(
                                    Filters.eq(TrafficMetricsAlert.HOST, host),
                                    Filters.eq(TrafficMetricsAlert.FILTER_TYPE, TrafficMetricsAlert.FilterType.HOST)
                            ),
                            update,
                            new UpdateOptions().upsert(true)
                    )
            );

        }

        if (!updates.isEmpty()) {
            TrafficMetricsAlertsDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
        }
    }

    public void sendRedAlert(TrafficMetricsAlert alert, AlertType alertType) {
        if (!alert.getFilterType().equals(TrafficMetricsAlert.FilterType.HOST)) return;

        Slack slack = Slack.getInstance();
        String payload = generateRedAlertPayload(alert, alertType, this.metricsUrl);
        if (payload == null) return;

        try {
            slack.send(this.webhookUrl, payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateTsFieldHostWise(alert.getHost(), TrafficMetricsAlert.LAST_RED_ALERT_SENT_TS, Context.now());
    }

    public static String generateRedAlertPayload(TrafficMetricsAlert alert, AlertType alertType, String metricsUrl) {
        int lastOutgoingTrafficTsHours = alert.getLastOutgoingTrafficTs();
        String text;
        switch (alert.getFilterType()) {
            case HOST:
                String host = alert.getHost();
                switch (alertType) {
                    case OUTGOING_REQUESTS_MIRRORING:
                        int lastOutgoingTrafficTs = lastOutgoingTrafficTsHours * 60 * 60;
                        text = ":warning: *Alert*: Collection <" + metricsUrl + " | " + host +"> " + "last received traffic " + DateUtils.prettifyDelta(lastOutgoingTrafficTs);
                        break;
                    case FILTERED_REQUESTS_RUNTIME:
                        int lastDbUpdateTs = alert.getLastDbUpdateTs();
                        text = ":warning: *Alert*: Collection " + "<" + metricsUrl + " | " + host +"> " + "last processed traffic " + DateUtils.prettifyDelta(lastDbUpdateTs);
                        break;
                    default:
                        return null;
                }
                break;
            default:
                return null;
        }

        BasicDBList sectionsList = new BasicDBList();
        sectionsList.add(DailyUpdate.createSimpleBlockText(text));
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
        return ret.toJson();
    }

    public void sendGreenAlert(TrafficMetricsAlert alert, AlertType alertType) {
        if (!alert.getFilterType().equals(TrafficMetricsAlert.FilterType.HOST)) return;

        Slack slack = Slack.getInstance();
        String payload = generateGreenAlertPayload(alert, alertType, this.metricsUrl);
        try {
            slack.send(this.webhookUrl, payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateTsFieldHostWise( alert.getHost(), TrafficMetricsAlert.LAST_GREEN_ALERT_SENT_TS, Context.now());
    }

    public static String generateGreenAlertPayload(TrafficMetricsAlert alert, AlertType alertType, String metricsUrl) {
        String text;

        switch (alert.getFilterType()) {
            case HOST:
                String host = alert.getHost();
                switch (alertType) {
                    case OUTGOING_REQUESTS_MIRRORING:
                        text = ":white_check_mark: *Resolved: * Collection <" + metricsUrl + " | " + host +"> has successfully resumed receiving traffic.";
                        break;
                    case FILTERED_REQUESTS_RUNTIME:
                        text = ":white_check_mark: *Resolved: * Collection <" + metricsUrl + " | " + host +"> has successfully resumed processing traffic.";
                        break;
                    default:
                        return null;
                }
                break;
            default:
                return null;
        }


        BasicDBList sectionsList = new BasicDBList();
        sectionsList.add(DailyUpdate.createSimpleBlockText(text));
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
        return ret.toJson();
    }

    public static void updateTsFieldHostWise(String host, String fieldName, int ts) {
        TrafficMetricsAlertsDao.instance.updateOne(
                Filters.and(
                        Filters.eq(TrafficMetricsAlert.HOST, host),
                        Filters.eq(TrafficMetricsAlert.FILTER_TYPE, TrafficMetricsAlert.FilterType.HOST)
                ),
                Updates.set(fieldName, ts)
        );
    }


    public void sendAlerts(int thresholdSeconds, AlertType alertType, List<TrafficMetricsAlert> trafficMetricsAlertList) {

        for (TrafficMetricsAlert alert : trafficMetricsAlertList) {
            int lastOutgoingTrafficTs = alert.getLastOutgoingTrafficTs();
            int lastRedAlertSentTs = alert.getLastRedAlertSentTs();
            int lastGreenAlertSentTs = alert.getLastGreenAlertSentTs();
            int currentTs = Context.now();

            // didn't receive traffic in last thresholdSeconds
            if (currentTs - lastOutgoingTrafficTs > thresholdSeconds) {
                // green alert was sent after red alert
                if (lastGreenAlertSentTs >= lastRedAlertSentTs) {
                    sendRedAlert(alert, alertType);
                }
            } else {
                // received traffic in last thresholdSeconds
                if (lastGreenAlertSentTs < lastRedAlertSentTs) {
                    // no green alerts were sent after red alert
                    sendGreenAlert(alert, alertType);
                }
            }
        }
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getMetricsUrl() {
        return metricsUrl;
    }
}
