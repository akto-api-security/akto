package com.akto.action.traffic_metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficAlertsDao;
import com.akto.dto.traffic_metrics.TrafficAlerts;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class TrafficAlertsAction {
    
    private List<TrafficAlerts> trafficAlerts ;
    private static int REFRESH_LIMIT = 60 * 60 * 12; // show last refreshed data

    private TrafficAlerts trafficAlert;

    public String getAllTrafficAlerts(){

        List<Severity> allowedSeverityList = new ArrayList<>(Arrays.asList(Severity.HIGH, Severity.MEDIUM, Severity.LOW));

        Bson filterQ = Filters.and(
            Filters.eq(TrafficAlerts.LAST_DISMISSED, 0),
            Filters.in(TrafficAlerts.ALERT_SEVERITY, allowedSeverityList)
        );

        List<TrafficAlerts> trafficAlertsList = TrafficAlertsDao.instance.findAll(filterQ);
        this.trafficAlerts = trafficAlertsList;
        
        return Action.SUCCESS.toUpperCase();
    }

    public String markAlertAsDismissed(){
        Bson filterQ = Filters.and(
            Filters.eq(TrafficAlerts._ALERT_TYPE, this.trafficAlert.getAlertType()),
            Filters.eq(TrafficAlerts.LAST_DETECTED, this.trafficAlert.getLastDetected()),
            Filters.eq(TrafficAlerts.ALERT_SEVERITY, this.trafficAlert.getSeverity())  
        );
        TrafficAlertsDao.instance.updateOneNoUpsert(filterQ, Updates.set(TrafficAlerts.LAST_DISMISSED, Context.now()));
        return Action.SUCCESS.toUpperCase();
    }

    public List<TrafficAlerts> getTrafficAlerts() {
        return trafficAlerts;
    } 

    public void setTrafficAlert(TrafficAlerts trafficAlert) {
        this.trafficAlert = trafficAlert;
    }

}
