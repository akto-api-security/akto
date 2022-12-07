package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestRoles;

public class TestRolesDao extends AccountsContextDao<TestRoles> {
    @Override
    public String getCollName() {
        return null;
    }
    public static final TestRolesDao instance = new TestRolesDao();
    private TestRolesDao(){}
    @Override
    public Class getClassT() {
        return TestRoles.class;
    }
}
