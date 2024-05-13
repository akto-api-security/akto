package com.akto.store;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.testing.AuthMechanism;
import com.mongodb.BasicDBObject;

public class AuthMechanismStore {
    private AuthMechanism authMechanism;

    private AuthMechanismStore() {}

    public static AuthMechanismStore create() {
        AuthMechanismStore ret = new AuthMechanismStore();
        ret.authMechanism = TestRolesDao.instance.fetchAttackerToken(0);
        return ret;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }
}
