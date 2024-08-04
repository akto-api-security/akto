package com.akto.store;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.mongodb.BasicDBObject;

public class AuthMechanismStore {
    private AuthMechanism authMechanism;

    private AuthMechanismStore() {}

    public static AuthMechanismStore create() {
        AuthMechanismStore ret = new AuthMechanismStore();
        TestRoles testRoles = DataActorFactory.fetchInstance().fetchTestRole("ATTACKER_TOKEN_ALL");
        ret.authMechanism = TestRolesDao.instance.fetchAttackerToken(0, testRoles);
        return ret;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }
}
