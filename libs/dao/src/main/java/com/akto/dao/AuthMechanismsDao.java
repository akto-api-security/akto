package com.akto.dao;

import com.akto.dto.testing.AuthMechanism;

public class AuthMechanismsDao extends AccountsContextDao<AuthMechanism> {

    public static AuthMechanismsDao instance = new AuthMechanismsDao();

    @Override
    public String getCollName() {
        return "auth_mechanisms";
    }

    @Override
    public Class<AuthMechanism> getClassT() {
        return AuthMechanism.class;
    }
}
