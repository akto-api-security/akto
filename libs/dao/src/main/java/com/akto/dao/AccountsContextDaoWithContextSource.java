package com.akto.dao;

import com.akto.dao.context.Context;

public abstract class AccountsContextDaoWithContextSource<T> extends MCollection<T> {

    public static final String CONTEXT_SOURCE_FIELD = "contextSource";

    @Override
    public String getDBName() {
        return Context.accountId.get() + "";
    }
}
