package com.akto.dao;

import com.akto.dao.context.Context;

abstract public class AccountsContextDao<T> extends MCollection<T> {
    @Override
    public String getDBName() {
        return Context.accountId.get()+"";
    }
}
