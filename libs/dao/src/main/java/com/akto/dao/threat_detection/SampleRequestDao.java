package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.SampleRequest;

public class SampleRequestDao extends AccountsContextDao<SampleRequest> {

    public static final SampleRequestDao instance = new SampleRequestDao();

    @Override
    public String getCollName() {
        return "sample_malicious_requests";
    }

    @Override
    public Class<SampleRequest> getClassT() {
        return SampleRequest.class;
    }
}
