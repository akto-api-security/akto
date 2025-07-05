package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.config.TestCollectionPropertiesDao;
import com.akto.dto.RBAC;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.User;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.Predicate.Type;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.AuthParamData;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.LoginRequestAuthParam;
import com.akto.dto.testing.RequestData;
import com.akto.dto.testing.SampleDataAuthParam;
import com.akto.dto.testing.TLSAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.config.TestCollectionProperty;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.conversions.Bson;

public class TestRolesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestRolesAction.class, LogDb.DASHBOARD);;

    private List<TestRoles> testRoles;
    private TestRoles selectedRole;
    private RolesConditionUtils andConditions;
    private RolesConditionUtils orConditions;
    private String roleName;
    private List<AuthParamData> authParamData;
    private Map<String, String> apiCond;
    private String authAutomationType;
    private ArrayList<RequestData> reqData;
    private RecordedLoginFlowInput recordedLoginFlowInput;
    private List<String> scopeRoles;

    public List<String> getScopeRoles() {
        return scopeRoles;
    }

    public void setScopeRoles(List<String> scopeRoles) {
        this.scopeRoles = scopeRoles;
    }

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

        User user = getSUser();
        Bson filterRbac = Filters.and(
            Filters.eq(RBAC.USER_ID, user.getId()),
            Filters.eq(RBAC.ACCOUNT_ID, Context.accountId.get()));

        RBAC userRbac = RBACDao.instance.findOne(filterRbac);
        String userRole = (userRbac != null) ? userRbac.getRole().toUpperCase() : RBAC.Role.MEMBER.toString();
        Bson testRoleQ = Filters.or(
            Filters.exists(TestRoles.SCOPE_ROLES, false), // case when scope_roles field does not exist
            Filters.in(TestRoles.SCOPE_ROLES, userRole)   // case when user's role is in the scope_roles array
        );        

        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(Context.accountId.get(),
                "TEST_ROLE_SCOPE_ROLES");
        if (!featureAccess.getIsGranted() || userRole.equals(RBAC.Role.ADMIN.toString())) {
            testRoles = TestRolesDao.instance.findAll(Filters.empty());
        } else {
            testRoles = TestRolesDao.instance.findAll(testRoleQ);
        }

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

    private AuthWithCond makeAuthWithConditionFromParamData(TestRoles role) {
        if (authParamData != null) {
            List<AuthParam> authParams = new ArrayList<>();

            for (AuthParamData authParamDataElem : authParamData) {
                AuthParam param = null;

                switch (AuthMechanismTypes.valueOf(authAutomationType.toUpperCase())) {
                    case HARDCODED:
                        param = new HardcodedAuthParam(authParamDataElem.getWhere(), authParamDataElem.getKey(),
                            authParamDataElem.getValue(), true);
                        break;
                    
                    case LOGIN_REQUEST:
                        param = new LoginRequestAuthParam(authParamDataElem.getWhere(), authParamDataElem.getKey(),
                            authParamDataElem.getValue(), authParamDataElem.getShowHeader());
                        break;    

                    case TLS_AUTH:
                        param = new TLSAuthParam(authParamDataElem.getCertAuthorityCertificate(),
                            authParamDataElem.getCertificateType(), authParamDataElem.getClientCertificate(),
                            authParamDataElem.getClientKey());
                        break;
                    case SAMPLE_DATA:
                        param = new SampleDataAuthParam(authParamDataElem.getWhere(), authParamDataElem.getKey(),
                            authParamDataElem.getValue(), true);
                        break;        
                    default:
                        break;
                }

                authParams.add(param);
            }

            AuthMechanism authM = new AuthMechanism(authParams, this.reqData, authAutomationType, null);
            AuthWithCond authWithCond = new AuthWithCond(authM, apiCond, recordedLoginFlowInput);
            return authWithCond;
        } else {
            return null;
        }
    }

    public void addAuthMechanism(TestRoles role){
        if (authParamData != null) {            
            AuthWithCond authWithCond = makeAuthWithConditionFromParamData(role);
            if(authWithCond != null){
                TestRolesDao.instance.updateOne(Filters.eq(Constants.ID, role.getId()), Updates.push(TestRoles.AUTH_WITH_COND_LIST, authWithCond));
            }
        }
    }

    private TestRoles getRole() {
        if (roleName == null) {
            addActionError("Test role id is empty");
            return null;
        }

        TestRoles role = TestRolesDao.instance.findOne(Filters.eq(TestRoles.NAME, roleName));
        if (role == null) {//Role doesn't exists
            addActionError("Role doesn't exists");
            return null;
        }

        return role;
    }

    public String deleteTestRole() {
        loggerMaker.debugAndAddToDb("Started deleting role: " + roleName, LoggerMaker.LogDb.DASHBOARD);
        TestRoles role = getRole();
        if (role == null) {
            addActionError("Role doesn't exists");
            return ERROR.toUpperCase();
        }

        if(role.getCreatedBy().equals("System")) {
            addActionError("The role cannot be removed.");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if(user == null) {
            addActionError("User not found.");
            return ERROR.toUpperCase();
        }

        boolean noAccess = !user.getLogin().equals(role.getCreatedBy());

        if(noAccess) {
            RBAC.Role currentRoleForUser = RBACDao.getCurrentRoleForUser(user.getId(), Context.accountId.get());
            if (!currentRoleForUser.equals(RBAC.Role.ADMIN)) {
                addActionError("You do not have permission to delete this role.");
                return ERROR.toUpperCase();
            }
        }

        Bson roleFilterQ = Filters.eq(TestRoles.NAME, roleName);
        DeleteResult delete = TestRolesDao.instance.deleteAll(roleFilterQ);
        loggerMaker.debugAndAddToDb("Deleted role: " + roleName + " : " + delete, LoggerMaker.LogDb.DASHBOARD);

        AccessMatrixTaskAction accessMatrixTaskAction = new AccessMatrixTaskAction();
        accessMatrixTaskAction.setRoleName(roleName);
        accessMatrixTaskAction.deleteAccessMatrix();

        return SUCCESS.toUpperCase();
    }

    public String updateTestRoles() {
        TestRoles role = getRole();
        if (role == null) {
            addActionError("Role doesn't exists");
            return ERROR.toUpperCase();
        }

        boolean isAttackerRole = false;
        TestRoles attackerRole = TestCollectionPropertiesDao.fetchTestToleForProp(0,
                TestCollectionProperty.Id.ATTACKER_TOKEN);
        if (attackerRole != null && attackerRole.getId() != null) {
            isAttackerRole = role.getId().equals(attackerRole.getId());
        }
        if (isAttackerRole) {
            this.orConditions = null;
            this.andConditions = null;
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
        this.selectedRole = role;
        this.selectedRole.setEndpointLogicalGroup(logicalGroup);

        User sUser = getSUser();
        if(sUser == null || sUser.getLogin() == null) {
            addActionError("You are not authorized to perform this action.");
            return ERROR.toUpperCase();
        }

        TestRolesDao.instance.updateOne(Filters.eq(Constants.ID, role.getId()), Updates.combine(Updates.set(TestRoles.LAST_UPDATED_TS, Context.now()), Updates.set(TestRoles.LAST_UPDATED_BY, sUser.getLogin())));
        return SUCCESS.toUpperCase();
    }

    public String saveTestRoleMeta() {
        
        if(scopeRoles != null && scopeRoles.size() > 0) {
            TestRoles role = getRole();
            if (role == null) {
                addActionError("Test role does not exist");
                return ERROR.toUpperCase();
            }
            Bson roleFilter = Filters.eq(Constants.ID, role.getId());
            TestRolesDao.instance.updateOne(roleFilter,  Updates.set(TestRoles.SCOPE_ROLES, scopeRoles));
            return SUCCESS.toUpperCase();
        } 
        addActionError("Scope roles are empty");
        return ERROR.toUpperCase();
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

    private int index;
    public String deleteAuthFromRole() {
        TestRoles role = getRole();
        if (role == null) {
            return ERROR.toUpperCase();
        }

        Bson roleFilter = Filters.eq(TestRoles.NAME, roleName);
        Bson removeFromArr = Updates.unset(TestRoles.AUTH_WITH_COND_LIST+"."+index);
        Bson removeNull = Updates.pull(TestRoles.AUTH_WITH_COND_LIST, null);
        TestRolesDao.instance.updateOne(roleFilter, removeFromArr);
        TestRolesDao.instance.updateOne(roleFilter, removeNull);
        this.selectedRole = getRole();
        return SUCCESS.toUpperCase();
    }

    public String updateAuthInRole(){
        TestRoles role = getRole();
        if (role == null) {
            return ERROR.toUpperCase();
        }
        Bson roleFilter = Filters.eq(TestRoles.NAME, roleName);
        AuthWithCond authWithCond = makeAuthWithConditionFromParamData(role);
        TestRolesDao.instance.updateOne(roleFilter,
            Updates.set(TestRoles.AUTH_WITH_COND_LIST+"."+index, authWithCond)
        );
        this.selectedRole = getRole();
        return SUCCESS.toUpperCase();
    }

    public String addAuthToRole() {
        TestRoles role = getRole();
        if (role == null) {
            return ERROR.toUpperCase();
        }

        addAuthMechanism(role);
        this.selectedRole = getRole();
        return SUCCESS.toUpperCase();
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

    public void setIndex(int index) {
        this.index = index;
    }
    public void setApiCond(Map<String, String> apiCond) {
        this.apiCond = apiCond;
    }

    public String getAuthAutomationType() {
        return authAutomationType;
    }

    public ArrayList<RequestData> getReqData() {
        return reqData;
    }

    public void setReqData(ArrayList<RequestData> reqData) {
        this.reqData = reqData;
    }

    public void setAuthAutomationType(String authAutomationType) {
        this.authAutomationType = authAutomationType;
    }

    public RecordedLoginFlowInput getRecordedLoginFlowInput() {
        return recordedLoginFlowInput;
    }

    public void setRecordedLoginFlowInput(RecordedLoginFlowInput recordedLoginFlowInput) {
        this.recordedLoginFlowInput = recordedLoginFlowInput;
    }
}
