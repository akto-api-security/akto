package com.akto.dao;

public abstract class BillingContextDao<T> extends MCollection<T> {
    @Override
    public String getDBName() {
        return "billing";
    }
}
