package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.Predicate.Type;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.AuthParamData;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.ArrayList;
import java.util.List;

public class TestRolesAction extends UserAction {
    private List<TestRoles> testRoles;
    private TestRoles selectedRole;
    private RolesConditionUtils andConditions;
    private RolesConditionUtils orConditions;
    private String roleName;
    private List<AuthParamData> authParamData;

    public static class RolesConditionUtils {
        private Operator operator;
        private List<BasicDBObject> predicates;
        public RolesConditionUtils(){}
        /*
        *This constructor is empty because struts 2 needs empty constructors to make objects and assign values via getters and setters
        * */
        public Operator getOperator() {
            return operator;
        }

        public void setOperator(Operator operator) {
            this.operator = operator;
        }

        public List<BasicDBObject> getPredicates() {
            return predicates;
        }

        public void setPredicates(List<BasicDBObject> predicates) {
            this.predicates = predicates;
        }
    }

    public String fetchAllRolesAndLogicalGroups() {
        testRoles = TestRolesDao.instance.findAll(Filters.empty());
        List<EndpointLogicalGroup> endpointLogicalGroups = EndpointLogicalGroupDao.instance.findAll(Filters.empty());

        testRoles.forEach((item) -> {
            for (int index = 0; index < endpointLogicalGroups.size(); index++) {
                if (endpointLogicalGroups.get(index).getId().equals(item.getEndpointLogicalGroupId())) {
                    item.setEndpointLogicalGroup(endpointLogicalGroups.get(index));
                    break;
                }
            }
        });
        return SUCCESS.toUpperCase();
    }

    public void addAuthMechanism(TestRoles role){
        if (authParamData != null) {
            AuthParam param = new HardcodedAuthParam(authParamData.get(0).getWhere(), authParamData.get(0).getKey(), authParamData.get(0).getValue(), true);
            List<AuthParam> authParams = new ArrayList<>();
            authParams.add(param);
            AuthMechanism authM = new AuthMechanism(authParams, null, LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString());
            TestRolesDao.instance.updateOne(Filters.eq(Constants.ID, role.getId()), Updates.set(TestRoles.AUTH_MECHANISM, authM));
            role.setAuthMechanism(authM);
        }
    }

    public String updateTestRoles() {
        if (roleName == null) {
            addActionError("Test role id is empty");
            return ERROR.toUpperCase();
        }

        TestRoles role = TestRolesDao.instance.findOne(Filters.eq(TestRoles.NAME, roleName));
        if (role == null) {//Role doesn't exists
            addActionError("Role doesn't exists");
            return ERROR.toUpperCase();
        }

        Conditions orConditions = null;
        if (this.orConditions != null) {
            orConditions = new Conditions();
            orConditions.setOperator(this.orConditions.getOperator());
            orConditions.setPredicates(getPredicatesFromPredicatesObject(this.orConditions.getPredicates()));
        }
        Conditions andConditions = null;
        if (this.andConditions != null) {
            andConditions = new Conditions();
            andConditions.setOperator(this.andConditions.getOperator());
            andConditions.setPredicates(getPredicatesFromPredicatesObject(this.andConditions.getPredicates()));
        }
        //Valid role name and regex
        EndpointLogicalGroup logicalGroup = EndpointLogicalGroupDao.instance.findOne(Filters.eq(Constants.ID, role.getEndpointLogicalGroupId()));

        if (logicalGroup != null) {
            EndpointLogicalGroupDao.instance.updateLogicalGroup(logicalGroup, andConditions, orConditions);
        }
        role.setLastUpdatedTs(Context.now());
        addAuthMechanism(role);
        this.selectedRole = role;
        this.selectedRole.setEndpointLogicalGroup(logicalGroup);
        TestRolesDao.instance.updateOne(Filters.eq(Constants.ID, role.getId()), Updates.set(TestRoles.LAST_UPDATED_TS, Context.now()));
        return SUCCESS.toUpperCase();
    }

    public String createTestRole () {
        if (roleName == null || roleName.isEmpty()) {
            addActionError("Test role name is empty");
            return ERROR.toUpperCase();
        }

        if (TestRolesDao.instance.getMCollection().countDocuments(Filters.eq(TestRoles.NAME, roleName)) > 0) {//Role exists
            addActionError("Role already exists");
            return ERROR.toUpperCase();
        }

        Conditions orConditions = null;
        if (this.orConditions != null) {
            orConditions = new Conditions();
            orConditions.setOperator(this.orConditions.getOperator());
            orConditions.setPredicates(getPredicatesFromPredicatesObject(this.orConditions.getPredicates()));
        }
        Conditions andConditions = null;
        if (this.andConditions != null) {
            andConditions = new Conditions();
            andConditions.setOperator(this.andConditions.getOperator());
            andConditions.setPredicates(getPredicatesFromPredicatesObject(this.andConditions.getPredicates()));
        }
        //Valid role name and regex
        String logicalGroupName = roleName + EndpointLogicalGroup.GROUP_NAME_SUFFIX;
        EndpointLogicalGroup logicalGroup = EndpointLogicalGroupDao.instance.
                createLogicalGroup(logicalGroupName, andConditions,orConditions,this.getSUser().getLogin());
        selectedRole = TestRolesDao.instance.createTestRole(roleName, logicalGroup.getId(), this.getSUser().getLogin());
        selectedRole.setEndpointLogicalGroup(logicalGroup);
        addAuthMechanism(selectedRole);
        return SUCCESS.toUpperCase();
    }

    private List<Predicate> getPredicatesFromPredicatesObject(List<BasicDBObject> predicates) {
        List<Predicate> arrayList = new ArrayList<>();
        for (int index = 0; index < predicates.size(); index++) {
            Type type = Type.valueOf(predicates.get(index).getString(Predicate.TYPE));
            Predicate predicate = Predicate.generatePredicate(type, predicates.get(index));
            arrayList.add(predicate);
        }
        return arrayList;
    }

    public List<TestRoles> getTestRoles() {
        return testRoles;
    }

    public void setTestRoles(List<TestRoles> testRoles) {
        this.testRoles = testRoles;
    }
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
    public RolesConditionUtils getAndConditions() {
        return andConditions;
    }

    public void setAndConditions(RolesConditionUtils andConditions) {
        this.andConditions = andConditions;
    }

    public RolesConditionUtils getOrConditions() {
        return orConditions;
    }

    public void setOrConditions(RolesConditionUtils orConditions) {
        this.orConditions = orConditions;
    }

    public TestRoles getSelectedRole() {
        return selectedRole;
    }

    public void setSelectedRole(TestRoles selectedRole) {
        this.selectedRole = selectedRole;
    }

    public List<AuthParamData> getAuthParamData() {
        return this.authParamData;
    }

    public void setAuthParamData(List<AuthParamData> authParamData) {
        this.authParamData = authParamData;
    }

}
