package com.akto.store;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dto.testing.AuthMechanism;
import com.mongodb.BasicDBObject;

public class AuthMechanismStore {
    private static AuthMechanism authMechanism;

    public static void fetchAuthMechanism() {
        authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
    }

    public static AuthMechanism getAuthMechanism() {
        return authMechanism;
    }
}
