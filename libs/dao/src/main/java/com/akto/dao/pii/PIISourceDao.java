package com.akto.dao.pii;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.pii.PIISource;

public class PIISourceDao extends AccountsContextDao<PIISource> {

    public static final PIISourceDao instance = new PIISourceDao();

    private PIISourceDao() {}

    @Override
    public String getCollName() {
        return "pii_sources";
    }

    @Override
    public Class<PIISource> getClassT() {
        return PIISource.class;
    }
}
