package com.akto.dao.traffic_metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.traffic_metrics.TrafficMetricsAlert;

public class TrafficMetricsAlertsDao extends AccountsContextDao<TrafficMetricsAlert> {

    public static final TrafficMetricsAlertsDao instance = new TrafficMetricsAlertsDao();

    @Override
    public String getCollName() {
        return "traffic_metrics_alerts";
    }

    @Override
    public Class<TrafficMetricsAlert> getClassT() {
        return TrafficMetricsAlert.class;
    }
}
