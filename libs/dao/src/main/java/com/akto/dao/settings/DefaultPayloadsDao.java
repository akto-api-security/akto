package com.akto.dao.settings;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.settings.DefaultPayload;

public class DefaultPayloadsDao extends AccountsContextDao<DefaultPayload> {

    public static final DefaultPayloadsDao instance = new DefaultPayloadsDao();

    private DefaultPayloadsDao() {}

    @Override
    public String getCollName() {
        return "default_payloads";
    }

    @Override
    public Class<DefaultPayload> getClassT() {
        return DefaultPayload.class;
    }
}
