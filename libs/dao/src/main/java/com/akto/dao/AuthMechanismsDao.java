package com.akto.dao;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dto.testing.AuthMechanism;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;

public class AuthMechanismsDao extends AccountsContextDao<AuthMechanism> {

    public static AuthMechanismsDao instance = new AuthMechanismsDao();

    public BasicDBObject findOneDocument(Bson q) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(q));
        MongoCursor<BasicDBObject> cursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        while(cursor.hasNext()) {
            return cursor.next();
        }

        return new BasicDBObject();
    }

    @Override
    public String getCollName() {
        return "auth_mechanisms";
    }

    @Override
    public Class<AuthMechanism> getClassT() {
        return AuthMechanism.class;
    }
}
