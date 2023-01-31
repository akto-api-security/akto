package com.akto.dao;

import com.akto.dto.Markov;
import com.akto.dto.Relationship;
import com.mongodb.BasicDBObject;

import java.util.List;

public class RelationshipDao extends AccountsContextDao<Relationship> {

    public static final RelationshipDao instance = new RelationshipDao();

    private RelationshipDao() {}

    @Override
    public String getCollName() {
        return "relationship";
    }

    @Override
    public Class<Relationship> getClassT() {
        return Relationship.class;
    }

    public List<Relationship> fetchAll() {
        return this.findAll(new BasicDBObject());
    }

}
