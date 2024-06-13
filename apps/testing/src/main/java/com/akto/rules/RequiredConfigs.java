package com.akto.rules;

import java.util.ArrayList;
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

    private static List<TestRoles> testRolesList = new ArrayList<>();

    private static final Map<String,Boolean> validRolesExist = new HashMap<>();

    public static void initiate() {
        validRolesExist.clear();
        testRolesList = TestRolesDao.instance.findAll(
            Filters.empty(),
            Projections.fields(
                Projections.include(TestRoles.NAME)
            )
        );
        for(TestRoles role: testRolesList){
            validRolesExist.put(role.getName(), true);
        }
    }

    public static Map<String,Boolean> getCurrentConfigsMap(){
        return validRolesExist;
    }

}

