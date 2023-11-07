package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dto.User;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.testing.AuthParamData;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.AuthParam.Location;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestRolesActionTest extends MongoBasedTest {

    /*
    *
    * */
    @Test
    public void testRoleCreationFlow () {
        TestRolesAction action = new TestRolesAction();
        action.setRoleName("admin");


        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        action.setSession(session);

        TestRolesAction.RolesConditionUtils orConditionUtils = new TestRolesAction.RolesConditionUtils();
        orConditionUtils.setOperator(Conditions.Operator.OR);
        List<BasicDBObject> list = new ArrayList<>();
        list.add(new BasicDBObject()
                .append(Predicate.TYPE, Predicate.Type.CONTAINS.name())
                .append(Predicate.VALUE, "contains"));
        orConditionUtils.setPredicates(list);
        TestRolesAction.RolesConditionUtils andConditionUtils = new TestRolesAction.RolesConditionUtils();
        andConditionUtils.setOperator(Conditions.Operator.AND);
        list.clear();
        list.add(new BasicDBObject()
                .append(Predicate.TYPE, Predicate.Type.CONTAINS.name())
                .append(Predicate.VALUE, "contains"));
        andConditionUtils.setPredicates(list);

        action.setOrConditions(orConditionUtils);
        action.setAndConditions(andConditionUtils);
        action.setAuthParamData(Arrays.asList(new AuthParamData(Location.HEADER, "Authorization", "value1", null)));

        if (TestRolesAction.SUCCESS.toUpperCase().equals(action.createTestRole())) {
            assertEquals("admin", action.getSelectedRole().getName());
        } else {
            fail();
        }

        assertEquals(TestRolesAction.SUCCESS.toUpperCase(), action.fetchAllRolesAndLogicalGroups());
        TestRoles role = action.getTestRoles().get(0);
        assertEquals("admin", role.getName());

        action.setAuthParamData(Arrays.asList(new AuthParamData(Location.HEADER, "Authorization", "value2", null)));
        action.setAuthAutomationType("HARDCODED");
        action.addAuthMechanism(role);
        list.clear();
        list.add(new BasicDBObject()
                .append(Predicate.TYPE, Predicate.Type.CONTAINS.name())
                .append(Predicate.VALUE, "containsAnd"));
        andConditionUtils.setPredicates(list);

        assertEquals(TestRolesAction.SUCCESS.toUpperCase(), action.updateTestRoles());


    }

}