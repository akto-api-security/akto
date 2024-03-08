package com.akto.dto.traffic_metrics;

import org.bson.types.ObjectId;

public class TrafficMetricsAlert {
    private ObjectId id;
    private String host;
    public static final String HOST = "host";

    private int lastOutgoingTrafficTs;
    public static final String LAST_OUTGOING_TRAFFIC_TS = "lastOutgoingTrafficTs";

    private int lastDbUpdateTs;
    public static final String LAST_DB_UPDATE_TS = "lastDbUpdateTs";

    private int lastOutgoingTrafficRedAlertSentTs;
    public static final String LAST_OUTGOING_TRAFFIC_RED_ALERT_SENT_TS = "lastOutgoingTrafficRedAlertSentTs";

    private int lastOutgoingTrafficGreenAlertSentTs;
    public static final String LAST_OUTGOING_TRAFFIC_GREEN_ALERT_SENT_TS = "lastOutgoingTrafficGreenAlertSentTs";

    private int lastDbUpdateRedAlertSentTs;
    public static final String LAST_DB_UPDATE_RED_ALERT_SENT_TS = "lastDbUpdateRedAlertSentTs";

    private int lastDbUpdateGreenAlertSentTs;
    public static final String LAST_DB_UPDATE_GREEN_ALERT_SENT_TS = "lastDbUpdateGreenAlertSentTs";

    public enum FilterType {
        HOST
    }


    public TrafficMetricsAlert() {}

    public TrafficMetricsAlert(String host, int lastOutgoingTrafficTs, int lastDbUpdateTs,
                               int lastOutgoingTrafficRedAlertSentTs, int lastOutgoingTrafficGreenAlertSentTs,
                               int lastDbUpdateRedAlertSentTs, int lastDbUpdateGreenAlertSentTs) {
        this.host = host;
        this.lastOutgoingTrafficTs = lastOutgoingTrafficTs;
        this.lastDbUpdateTs = lastDbUpdateTs;
        this.lastOutgoingTrafficRedAlertSentTs = lastOutgoingTrafficRedAlertSentTs;
        this.lastOutgoingTrafficGreenAlertSentTs = lastOutgoingTrafficGreenAlertSentTs;
        this.lastDbUpdateRedAlertSentTs = lastDbUpdateRedAlertSentTs;
        this.lastDbUpdateGreenAlertSentTs = lastDbUpdateGreenAlertSentTs;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }



    public int getLastOutgoingTrafficTs() {
        return lastOutgoingTrafficTs;
    }

    public void setLastOutgoingTrafficTs(int lastOutgoingTrafficTs) {
        this.lastOutgoingTrafficTs = lastOutgoingTrafficTs;
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

    public int getLastOutgoingTrafficRedAlertSentTs() {
        return lastOutgoingTrafficRedAlertSentTs;
    }

    public void setLastOutgoingTrafficRedAlertSentTs(int lastOutgoingTrafficRedAlertSentTs) {
        this.lastOutgoingTrafficRedAlertSentTs = lastOutgoingTrafficRedAlertSentTs;
    }

    public int getLastOutgoingTrafficGreenAlertSentTs() {
        return lastOutgoingTrafficGreenAlertSentTs;
    }

    public void setLastOutgoingTrafficGreenAlertSentTs(int lastOutgoingTrafficGreenAlertSentTs) {
        this.lastOutgoingTrafficGreenAlertSentTs = lastOutgoingTrafficGreenAlertSentTs;
    }

    public int getLastDbUpdateRedAlertSentTs() {
        return lastDbUpdateRedAlertSentTs;
    }

    public void setLastDbUpdateRedAlertSentTs(int lastDbUpdateRedAlertSentTs) {
        this.lastDbUpdateRedAlertSentTs = lastDbUpdateRedAlertSentTs;
    }

    public int getLastDbUpdateGreenAlertSentTs() {
        return lastDbUpdateGreenAlertSentTs;
    }

    public void setLastDbUpdateGreenAlertSentTs(int lastDbUpdateGreenAlertSentTs) {
        this.lastDbUpdateGreenAlertSentTs = lastDbUpdateGreenAlertSentTs;
    }
}
