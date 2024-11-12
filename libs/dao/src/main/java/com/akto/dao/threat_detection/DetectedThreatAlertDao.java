package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.DetectedThreatAlert;

public class DetectedThreatAlertDao extends AccountsContextDao<DetectedThreatAlert> {

    public static final DetectedThreatAlertDao instance = new DetectedThreatAlertDao();

    @Override
    public String getCollName() {
        return "detected_threat_alerts";
    }

    @Override
    public Class<DetectedThreatAlert> getClassT() {
        return DetectedThreatAlert.class;
    }

}
