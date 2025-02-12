package com.akto.dao.testing;

import com.akto.dao.CommonContextDao;
import com.akto.dto.testing.ComplianceInfo;

public class ComplianceInfosDao extends CommonContextDao<ComplianceInfo> {

    public static final ComplianceInfosDao instance = new ComplianceInfosDao();

    private ComplianceInfosDao() {}

    @Override
    public String getCollName() {
        return "compliance_infos";
    }

    @Override
    public Class<ComplianceInfo> getClassT() {
        return ComplianceInfo.class;
    }
    
}
