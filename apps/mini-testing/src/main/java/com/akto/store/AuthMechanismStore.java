package com.akto.store;

import com.akto.dao.testing.TestRolesDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;

public class AuthMechanismStore {
    private AuthMechanism authMechanism;
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private AuthMechanismStore() {}

    public static AuthMechanismStore create() {
        AuthMechanismStore ret = new AuthMechanismStore();
        TestRoles testRoles = dataActor.fetchTestRole("ATTACKER_TOKEN_ALL");
        ret.authMechanism = TestRolesDao.instance.fetchAttackerToken(0, testRoles);
        return ret;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }
}
