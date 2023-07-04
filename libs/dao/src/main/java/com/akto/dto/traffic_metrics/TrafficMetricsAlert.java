package com.akto.dto.traffic_metrics;

import org.bson.types.ObjectId;

public class TrafficMetricsAlert {
    private ObjectId id;
    private FilterType filterType;
    public static final String FILTER_TYPE = "filterType";

    private String host;
    public static final String HOST = "host";

    private int lastOutgoingTrafficTs;
    public static final String LAST_OUTGOING_TRAFFIC_TS = "lastOutgoingTrafficTs";

    private int lastDbUpdateTs;
    public static final String LAST_DB_UPDATE_TS = "lastDbUpdateTs";

    private int lastRedAlertSentTs;
    public static final String LAST_RED_ALERT_SENT_TS = "lastRedAlertSentTs";

    private int lastGreenAlertSentTs;
    public static final String LAST_GREEN_ALERT_SENT_TS = "lastGreenAlertSentTs";

    public enum FilterType {
        HOST
    }


    public TrafficMetricsAlert() {}

    public TrafficMetricsAlert(FilterType filterType, int lastRedAlertSentTs, int lastGreenAlertSentTs,
                               int lastOutgoingTrafficTs, int lastDbUpdateTs) {
        this.filterType = filterType;
        this.lastRedAlertSentTs = lastRedAlertSentTs;
        this.lastGreenAlertSentTs = lastGreenAlertSentTs;
        this.lastOutgoingTrafficTs = lastOutgoingTrafficTs;
        this.lastDbUpdateTs = lastDbUpdateTs;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }


    public int getLastRedAlertSentTs() {
        return lastRedAlertSentTs;
    }

    public void setLastRedAlertSentTs(int lastRedAlertSentTs) {
        this.lastRedAlertSentTs = lastRedAlertSentTs;
    }


    public FilterType getFilterType() {
        return filterType;
    }

    public void setFilterType(FilterType filterType) {
        this.filterType = filterType;
    }

    public int getLastOutgoingTrafficTs() {
        return lastOutgoingTrafficTs;
    }

    public void setLastOutgoingTrafficTs(int lastOutgoingTrafficTs) {
        this.lastOutgoingTrafficTs = lastOutgoingTrafficTs;
    }

    public int getLastGreenAlertSentTs() {
        return lastGreenAlertSentTs;
    }

    public void setLastGreenAlertSentTs(int lastGreenAlertSentTs) {
        this.lastGreenAlertSentTs = lastGreenAlertSentTs;
    }

    public int getLastDbUpdateTs() {
        return lastDbUpdateTs;
    }

    public void setLastDbUpdateTs(int lastDbUpdateTs) {
        this.lastDbUpdateTs = lastDbUpdateTs;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
