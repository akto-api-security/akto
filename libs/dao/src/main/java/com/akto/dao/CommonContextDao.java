package com.akto.dao;

public abstract class CommonContextDao<T> extends MCollection<T> {
    @Override
    public String getDBName() {
        return "common";
    }
}
