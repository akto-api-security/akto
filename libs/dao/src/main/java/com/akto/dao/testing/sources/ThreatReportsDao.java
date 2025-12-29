package com.akto.dao.testing.sources;

import com.akto.dto.testing.sources.ThreatReports;
import com.akto.dao.AccountsContextDao;

public class ThreatReportsDao extends AccountsContextDao<ThreatReports> {

    public static final ThreatReportsDao instance = new ThreatReportsDao();

    private ThreatReportsDao() {}

    @Override
    public String getCollName() {
        return "threat_reports";
    }

    @Override
    public Class<ThreatReports> getClassT() {
        return ThreatReports.class;
    }
}
