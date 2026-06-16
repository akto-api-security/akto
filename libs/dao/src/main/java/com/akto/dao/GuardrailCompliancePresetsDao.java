package com.akto.dao;

import com.akto.dto.GuardrailCompliancePreset;

public class GuardrailCompliancePresetsDao extends CommonContextDao<GuardrailCompliancePreset> {

    public static final GuardrailCompliancePresetsDao instance = new GuardrailCompliancePresetsDao();

    private GuardrailCompliancePresetsDao() {}

    @Override
    public String getCollName() {
        return "guardrail_compliance_presets";
    }

    @Override
    public Class<GuardrailCompliancePreset> getClassT() {
        return GuardrailCompliancePreset.class;
    }
}
