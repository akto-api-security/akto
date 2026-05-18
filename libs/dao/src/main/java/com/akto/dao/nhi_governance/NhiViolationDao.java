package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.nhi_governance.NhiViolation;

public class NhiViolationDao extends AccountsContextDao<NhiViolation> {

    public static NhiViolationDao instance = new NhiViolationDao();

    @Override
    public String getCollName() {
        return "nhi_violations";
    }

    @Override
    public Class<NhiViolation> getClassT() {
        return NhiViolation.class;
    }
}
