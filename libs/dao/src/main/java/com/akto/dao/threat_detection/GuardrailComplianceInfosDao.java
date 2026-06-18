package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.GuardrailComplianceInfo;

public class GuardrailComplianceInfosDao extends AccountsContextDao<GuardrailComplianceInfo> {

    public static final GuardrailComplianceInfosDao instance = new GuardrailComplianceInfosDao();

    private GuardrailComplianceInfosDao() {}

    @Override
    public String getCollName() {
        return "guardrail_compliance_infos";
    }

    @Override
    public Class<GuardrailComplianceInfo> getClassT() {
        return GuardrailComplianceInfo.class;
    }

}
