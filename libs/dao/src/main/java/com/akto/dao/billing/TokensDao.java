package com.akto.dao.billing;

import com.akto.dao.BillingContextDao;
import com.akto.dto.billing.Tokens;

public class TokensDao extends BillingContextDao<Tokens>{
    
    public static final TokensDao instance = new TokensDao();

    @Override
    public String getCollName() {
        return "tokens";
    }

    @Override
    public Class<Tokens> getClassT() {
        return Tokens.class;
    }

}
