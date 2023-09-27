package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.AccessMatrixUrlToRole;

public class AccessMatrixUrlToRolesDao extends AccountsContextDao<AccessMatrixUrlToRole> {

    public static final AccessMatrixUrlToRolesDao instance = new AccessMatrixUrlToRolesDao();

    private AccessMatrixUrlToRolesDao() {}

    @Override
    public String getCollName() {
        return "access_matrix_url_to_roles";
    }

    @Override
    public Class<AccessMatrixUrlToRole> getClassT() {
        return AccessMatrixUrlToRole.class;
    }

}
