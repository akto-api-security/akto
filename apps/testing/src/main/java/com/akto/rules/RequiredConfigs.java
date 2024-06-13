package com.akto.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.testing.TestRoles;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class RequiredConfigs {

    private static final RequiredConfigs requiredConfigs = new RequiredConfigs();

    public static RequiredConfigs getRequiredConfigs() {
        return requiredConfigs;
    }

    private static List<TestRoles> testRolesList = TestRolesDao.instance.findAll(
        Filters.empty(),
        Projections.fields(
            Projections.include(TestRoles.NAME, TestRoles.AUTH_WITH_COND_LIST)
        )
    );

    private static final Map<String,Boolean> validRolesExist = new HashMap<>();

    public static void initiate () {
        validRolesExist.clear();
        for(TestRoles role: testRolesList){
            if(role.getAuthWithCondList() != null && role.getAuthWithCondList().size() > 0){
                validRolesExist.put(role.getName(), true);
            }
        }
    }

    public static Map<String,Boolean> getCurrentConfigsMap(){
        return validRolesExist;
    }

}
