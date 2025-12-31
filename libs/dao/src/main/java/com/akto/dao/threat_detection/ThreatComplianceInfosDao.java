package com.akto.dao.threat_detection;

import com.akto.dao.CommonContextDao;
import com.akto.dto.threat_detection.ThreatComplianceInfo;

public class ThreatComplianceInfosDao extends CommonContextDao<ThreatComplianceInfo> {

    public static final ThreatComplianceInfosDao instance = new ThreatComplianceInfosDao();

    private ThreatComplianceInfosDao() {}

    @Override
    public String getCollName() {
        return "threat_compliance_infos";
    }

    @Override
    public Class<ThreatComplianceInfo> getClassT() {
        return ThreatComplianceInfo.class;
    }

}
