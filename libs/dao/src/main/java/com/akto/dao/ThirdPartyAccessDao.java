package com.akto.dao;

import com.akto.dto.third_party_access.ThirdPartyAccess;

public class ThirdPartyAccessDao extends AccountsContextDao<ThirdPartyAccess> {

    public static ThirdPartyAccessDao instance = new ThirdPartyAccessDao();

    @Override
    public String getCollName() {
        return "third_party";
    }

    @Override
    public Class<ThirdPartyAccess> getClassT() {
        return ThirdPartyAccess.class;
    }
}
