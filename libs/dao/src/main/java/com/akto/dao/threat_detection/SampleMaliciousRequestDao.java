package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.SampleMaliciousRequest;

public class SampleMaliciousRequestDao extends AccountsContextDao<SampleMaliciousRequest> {

    public static final SampleMaliciousRequestDao instance = new SampleMaliciousRequestDao();

    @Override
    public String getCollName() {
        return "sample_malicious_requests";
    }

    @Override
    public Class<SampleMaliciousRequest> getClassT() {
        return SampleMaliciousRequest.class;
    }
}
