package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.nhi_governance.NhiIdentity;

public class NhiIdentityDao extends AccountsContextDao<NhiIdentity> {

    public static NhiIdentityDao instance = new NhiIdentityDao();

    @Override
    public String getCollName() {
        return "nhi_identities";
    }

    @Override
    public Class<NhiIdentity> getClassT() {
        return NhiIdentity.class;
    }
}
