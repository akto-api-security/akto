package com.akto.dao.testing;

import com.akto.dao.CommonContextDao;
import com.akto.dto.testing.Remediation;

public class RemediationsDao extends CommonContextDao<Remediation> {

    public static final RemediationsDao instance = new RemediationsDao();

    private RemediationsDao() {}

    @Override
    public String getCollName() {
        return "remediations";
    }

    @Override
    public Class<Remediation> getClassT() {
        return Remediation.class;
    }
    
}
