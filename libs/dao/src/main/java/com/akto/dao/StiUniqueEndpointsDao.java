package com.akto.dao;

import org.bson.Document;

public class StiUniqueEndpointsDao extends AccountsContextDao<Document> {

    public static final StiUniqueEndpointsDao instance = new StiUniqueEndpointsDao();

    @Override
    public String getCollName() {
        return "sti_unique_endpoints";
    }

    @Override
    public Class<Document> getClassT() {
        return Document.class;
    }
}
