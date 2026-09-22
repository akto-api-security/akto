package com.akto.dao;

import com.akto.util.DbNames;

public abstract class BillingContextDao<T> extends MCollection<T> {
    @Override
    public String getDBName() {
        return DbNames.BILLING;
    }
}
