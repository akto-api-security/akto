package com.akto.dao;

import com.akto.dto.Markov;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;

import java.util.List;

public class MarkovDao extends AccountsContextDao<Markov> {

    public static final MarkovDao instance = new MarkovDao();

    private MarkovDao() {}

    @Override
    public String getCollName() {
        return "markov";
    }

    @Override
    public Class<Markov> getClassT() {
        return Markov.class;
    }

    public List<Markov> fetchAll() {
        return this.findAll(new BasicDBObject());
    }

}
