package com.akto.dao;

import com.akto.dto.Attempt;

import org.bson.BsonValue;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

public class AttemptsDao extends AccountsContextDao<Attempt> {

    public static final AttemptsDao instance = new AttemptsDao();

    private AttemptsDao() {}

    @Override
    public String getCollName() {
        return "attempts";
    }

    @Override
    public Class<Attempt> getClassT() {
        return Attempt.class;
    }
    
    public void insertAttempts(List<Attempt> attempts) {
        insertMany(attempts);
    }
}
