package com.akto.dao.traffic_metrics;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.traffic_metrics.TrafficAlerts;

public class TrafficAlertsDao extends AccountsContextDao<TrafficAlerts>{
    public static final TrafficAlertsDao instance = new TrafficAlertsDao();

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {TrafficAlerts.LAST_DISMISSED, TrafficAlerts.LAST_DETECTED, TrafficAlerts.ALERT_SEVERITY};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[] {TrafficAlerts._ALERT_TYPE, TrafficAlerts.LAST_DETECTED, TrafficAlerts.ALERT_SEVERITY};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }


    @Override
    public String getCollName() {
        return "traffic_alerts";
    }

    @Override
    public Class<TrafficAlerts> getClassT() {
        return TrafficAlerts.class;
    }
}

