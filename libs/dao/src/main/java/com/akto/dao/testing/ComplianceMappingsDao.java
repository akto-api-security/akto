package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.ComplianceMapping;

public class ComplianceMappingsDao extends AccountsContextDao<ComplianceMapping> {

    public static final ComplianceMappingsDao instance = new ComplianceMappingsDao();
    
    private ComplianceMappingsDao() {}

    @Override
    public String getCollName() {
       return "compliance_mappings";
    }

    @Override
    public Class<ComplianceMapping> getClassT() {
        return ComplianceMapping.class;
    }
    
}
