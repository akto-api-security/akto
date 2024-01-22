package com.akto.utils.notifications;

import com.akto.calendar.DateUtils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsAlertsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetricsAlert;
import com.akto.log.LoggerMaker;
import com.akto.notifications.slack.DailyUpdate;
import com.akto.runtime.Main;
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


    private int lookBackPeriod;
    public TrafficUpdates(int lookBackPeriod) {
        this.lookBackPeriod = lookBackPeriod;
    }

    enum AlertType {
        OUTGOING_REQUESTS_MIRRORING,
        FILTERED_REQUESTS_RUNTIME
    }

    private static final LoggerMaker loggerMaker = new LoggerMaker(TrafficUpdates.class);

    public void populate() {
        loggerMaker.infoAndAddToDb("Starting populateTrafficDetails for " + AlertType.OUTGOING_REQUESTS_MIRRORING, LoggerMaker.LogDb.DASHBOARD);
        populateTrafficDetails(AlertType.OUTGOING_REQUESTS_MIRRORING);
        loggerMaker.infoAndAddToDb("Finished populateTrafficDetails for " + AlertType.OUTGOING_REQUESTS_MIRRORING, LoggerMaker.LogDb.DASHBOARD);

        loggerMaker.infoAndAddToDb("Starting populateTrafficDetails for " + AlertType.FILTERED_REQUESTS_RUNTIME, LoggerMaker.LogDb.DASHBOARD);
        populateTrafficDetails(AlertType.FILTERED_REQUESTS_RUNTIME);
        loggerMaker.infoAndAddToDb("Finished populateTrafficDetails for " + AlertType.FILTERED_REQUESTS_RUNTIME, LoggerMaker.LogDb.DASHBOARD);
    }

    public void sendAlerts(String webhookUrl, String metricsUrl, int thresholdSeconds) {

        List<TrafficMetricsAlert> trafficMetricsAlertList = TrafficMetricsAlertsDao.instance.findAll(new BasicDBObject());
        List<TrafficMetricsAlert> filteredTrafficMetricsAlertsList = filterTrafficMetricsAlertsList(trafficMetricsAlertList);
        loggerMaker.infoAndAddToDb("filteredTrafficMetricsAlertsList: " + filteredTrafficMetricsAlertsList.size(), LoggerMaker.LogDb.DASHBOARD);

        loggerMaker.infoAndAddToDb("Starting sendAlerts for " + AlertType.FILTERED_REQUESTS_RUNTIME, LoggerMaker.LogDb.DASHBOARD);
        sendAlerts(thresholdSeconds,AlertType.OUTGOING_REQUESTS_MIRRORING, filteredTrafficMetricsAlertsList, webhookUrl, metricsUrl);
        loggerMaker.infoAndAddToDb("Finished sendAlerts for " + AlertType.FILTERED_REQUESTS_RUNTIME, LoggerMaker.LogDb.DASHBOARD);

        loggerMaker.infoAndAddToDb("Starting sendAlerts for " + AlertType.FILTERED_REQUESTS_RUNTIME, LoggerMaker.LogDb.DASHBOARD);
        sendAlerts(thresholdSeconds, AlertType.FILTERED_REQUESTS_RUNTIME, filteredTrafficMetricsAlertsList, webhookUrl, metricsUrl);
        loggerMaker.infoAndAddToDb("Finished sendAlerts for " + AlertType.FILTERED_REQUESTS_RUNTIME, LoggerMaker.LogDb.DASHBOARD);
    }

    public List<TrafficMetricsAlert> filterTrafficMetricsAlertsList(List<TrafficMetricsAlert> trafficMetricsAlertList) {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaAll();
        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();
        Set<String> allowedHosts = new HashSet<>();
        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            if (count ==  null) continue;
            if (count > 20 && apiCollection.getHostName()!=null) allowedHosts.add(apiCollection.getHostName()) ;
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

        // we want to bring only last 3 days data to find traffic alerts. More efficient than getting all traffic.
        int time = (Context.now() - lookBackPeriod) / (60*60*24);

        Bson filter = Filters.and(
                Filters.eq("_id." + TrafficMetrics.Key.NAME, name.toString()),
                Filters.gte("_id."+TrafficMetrics.Key.BUCKET_START_EPOCH, time)
        );

        Document idExpression = new Document("host", "$_id.host");
        pipeline.add(Aggregates.match(filter));
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
            Integer ts = Integer.parseInt(a.getString("maxTimestamp")) * (60 * 60); // converting hours to seconds
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
                            Filters.eq(TrafficMetricsAlert.HOST, host),
                            update,
                            new UpdateOptions().upsert(true)
                    )
            );

        }

        if (!updates.isEmpty()) {
            TrafficMetricsAlertsDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
        }
    }

    public void sendRedAlert(Set<String> hosts, AlertType alertType, String webhookUrl, String metricsUrl) {
        Slack slack = Slack.getInstance();
        String payload = generateRedAlertPayload(hosts, alertType, metricsUrl);
        if (payload == null) return;

        try {
            slack.send(webhookUrl, payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateTsFieldHostWise(hosts, alertType, Context.now(), true);
    }

    public static String generateRedAlertPayload(Set<String> hosts, AlertType alertType, String metricsUrl) {
        String text;
        switch (alertType) {
            case OUTGOING_REQUESTS_MIRRORING:
                text = ":warning: Stopped receiving traffic for hosts " + prettifyHosts(hosts, 3) + ". <" + metricsUrl + " | Open Dashboard.>";
                break;
            case FILTERED_REQUESTS_RUNTIME:
                text = ":warning: Stopped processing traffic for hosts " + prettifyHosts(hosts, 3) + ". <" + metricsUrl + " | Open Dashboard.>";
                break;
            default:
                return null;
        }

        BasicDBList sectionsList = new BasicDBList();
        sectionsList.add(DailyUpdate.createSimpleBlockText(text));
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
        return ret.toJson();
    }

    public static String prettifyHosts(Set<String> hosts, int limit)  {
        StringBuilder result = new StringBuilder();
        int count = 0;

        for (String host : hosts) {
            if (count < limit) {
                if (count > 0) {
                    result.append(", ");
                }
                result.append(host);
                count++;
            } else {
                break;
            }
        }

        if (count < hosts.size()) {
            result.append(" and ").append(hosts.size() - count).append(" more");
        }

        return result.toString();
    }

    public void sendGreenAlert(Set<String> hosts, AlertType alertType, String webhookUrl, String metricsUrl) {
        Slack slack = Slack.getInstance();
        String payload = generateGreenAlertPayload(hosts, alertType, metricsUrl);
        try {
            slack.send(webhookUrl, payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        updateTsFieldHostWise(hosts, alertType, Context.now(), false);
    }

    public static String generateGreenAlertPayload(Set<String> hosts, AlertType alertType, String metricsUrl) {
        String text;

        switch (alertType) {
            case OUTGOING_REQUESTS_MIRRORING:
                text = ":white_check_mark: Resumed receiving traffic for hosts " + prettifyHosts(hosts, 3) + ". <" + metricsUrl + " | Open Dashboard.>";
                break;
            case FILTERED_REQUESTS_RUNTIME:
                text = ":white_check_mark: Resumed processing traffic for hosts " + prettifyHosts(hosts, 3) + ". <" + metricsUrl + " | Open Dashboard.>";
                break;
            default:
                return null;
        }


        BasicDBList sectionsList = new BasicDBList();
        sectionsList.add(DailyUpdate.createSimpleBlockText(text));
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);
        return ret.toJson();
    }

    public static void updateTsFieldHostWise(Set<String> hosts, AlertType alertType, int ts, boolean isRed) {

        String fieldName;
        switch (alertType) {
            case OUTGOING_REQUESTS_MIRRORING:
                fieldName = isRed ? TrafficMetricsAlert.LAST_OUTGOING_TRAFFIC_RED_ALERT_SENT_TS : TrafficMetricsAlert.LAST_OUTGOING_TRAFFIC_GREEN_ALERT_SENT_TS;
                break;
            case FILTERED_REQUESTS_RUNTIME:
                fieldName = isRed ? TrafficMetricsAlert.LAST_DB_UPDATE_RED_ALERT_SENT_TS : TrafficMetricsAlert.LAST_DB_UPDATE_GREEN_ALERT_SENT_TS;
                break;
            default:
                return;
        }

        TrafficMetricsAlertsDao.instance.updateMany(
                Filters.in(TrafficMetricsAlert.HOST, hosts),
                Updates.set(fieldName, ts)
        );
    }

    public static class AlertResult {
        Set<String> redAlertHosts = new HashSet<>();
        Set<String> greenAlertHosts = new HashSet<>();

        public AlertResult(Set<String> redAlertHosts, Set<String> greenAlertHosts) {
            this.redAlertHosts = redAlertHosts;
            this.greenAlertHosts = greenAlertHosts;
        }
    }

    public static AlertResult generateAlertResult(int thresholdSeconds, AlertType alertType, List<TrafficMetricsAlert> trafficMetricsAlertList) {
        Set<String> redAlertHosts = new HashSet<>();
        Set<String> greenAlertHosts = new HashSet<>();

        for (TrafficMetricsAlert alert : trafficMetricsAlertList) {
            int lastOutgoingTrafficTs =  alertType.equals(AlertType.OUTGOING_REQUESTS_MIRRORING) ? alert.getLastOutgoingTrafficTs() : alert.getLastDbUpdateTs();
            int lastRedAlertSentTs = alertType.equals(AlertType.OUTGOING_REQUESTS_MIRRORING) ? alert.getLastOutgoingTrafficRedAlertSentTs() : alert.getLastDbUpdateRedAlertSentTs();
            int lastGreenAlertSentTs = alertType.equals(AlertType.OUTGOING_REQUESTS_MIRRORING) ? alert.getLastOutgoingTrafficGreenAlertSentTs() : alert.getLastDbUpdateGreenAlertSentTs();
            int currentTs = Context.now();


            // didn't receive traffic in last thresholdSeconds
            if (currentTs - lastOutgoingTrafficTs > thresholdSeconds) {
                // green alert was sent after red alert
                if (lastGreenAlertSentTs >= lastRedAlertSentTs) {
                    redAlertHosts.add(alert.getHost());
                }
            } else {
                // received traffic in last thresholdSeconds
                if (lastGreenAlertSentTs < lastRedAlertSentTs) {
                    // no green alerts were sent after red alert
                    greenAlertHosts.add(alert.getHost());
                }
            }
        }

        return new AlertResult(redAlertHosts, greenAlertHosts);
    }

    public void sendAlerts(int thresholdSeconds, AlertType alertType, List<TrafficMetricsAlert> trafficMetricsAlertList,
                           String webhookUrl, String metricsUrl) {
        AlertResult alertResult = generateAlertResult(thresholdSeconds, alertType, trafficMetricsAlertList);

        if (!alertResult.redAlertHosts.isEmpty()) sendRedAlert(alertResult.redAlertHosts, alertType, webhookUrl, metricsUrl);
        if (!alertResult.greenAlertHosts.isEmpty()) sendGreenAlert(alertResult.greenAlertHosts, alertType, webhookUrl,  metricsUrl);
    }

}
