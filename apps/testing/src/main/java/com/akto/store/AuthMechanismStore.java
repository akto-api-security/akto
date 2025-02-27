package com.akto.store;

import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;

public class AuthMechanismStore {
    private AuthMechanism authMechanism;

    private AuthMechanismStore() {}

    public static AuthMechanismStore create(RawApi rawApi) {
        AuthMechanismStore ret = new AuthMechanismStore();
        ret.authMechanism = TestRolesDao.instance.fetchAttackerToken(0, rawApi);
        return ret;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }
}
