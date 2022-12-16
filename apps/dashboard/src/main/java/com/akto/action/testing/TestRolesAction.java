package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.TestRoles;
import com.mongodb.client.model.Filters;

import java.util.List;
import java.util.Map;

public class TestRolesAction extends UserAction {
    private List<TestRoles> testRoles;
    private TestRoles selectedRole;
    private Map<String, List<ApiInfo.ApiInfoKey>> includeCollectionApiList;
    private Map<String, List<ApiInfo.ApiInfoKey>> excludeCollectionApiList;
    private Conditions andConditions;
    private Conditions orConditions;
    private String roleName;

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

    public String createTestRole () {
        if (roleName == null || roleName.isEmpty()) {
            addActionError("Test role name is empty");
            return ERROR.toUpperCase();
        }

        if (TestRolesDao.instance.getMCollection().countDocuments(Filters.eq(TestRoles.NAME, roleName)) > 0) {//Role exists
            addActionError("Role already exists");
            return ERROR.toUpperCase();
        }
        //Valid role name and regex
        String logicalGroupName = roleName + EndpointLogicalGroup.GROUP_NAME_SUFFIX;
        EndpointLogicalGroup logicalGroup = EndpointLogicalGroupDao.instance.
                createLogicalGroup(logicalGroupName, andConditions,orConditions,this.getSUser().getLogin(), includeCollectionApiList, excludeCollectionApiList);
        selectedRole = TestRolesDao.instance.createTestRole(roleName, logicalGroup.getId(), this.getSUser().getLogin());
        selectedRole.setEndpointLogicalGroup(logicalGroup);
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
    public Conditions getAndConditions() {
        return andConditions;
    }

    public void setAndConditions(Conditions andConditions) {
        this.andConditions = andConditions;
    }

    public Conditions getOrConditions() {
        return orConditions;
    }

    public void setOrConditions(Conditions orConditions) {
        this.orConditions = orConditions;
    }

    public TestRoles getSelectedRole() {
        return selectedRole;
    }

    public void setSelectedRole(TestRoles selectedRole) {
        this.selectedRole = selectedRole;
    }

    public Map<String, List<ApiInfo.ApiInfoKey>> getIncludeCollectionApiList() {
        return includeCollectionApiList;
    }

    public void setIncludeCollectionApiList(Map<String, List<ApiInfo.ApiInfoKey>> includeCollectionApiList) {
        this.includeCollectionApiList = includeCollectionApiList;
    }

    public Map<String, List<ApiInfo.ApiInfoKey>> getExcludeCollectionApiList() {
        return excludeCollectionApiList;
    }

    public void setExcludeCollectionApiList(Map<String, List<ApiInfo.ApiInfoKey>> excludeCollectionApiList) {
        this.excludeCollectionApiList = excludeCollectionApiList;
    }
}
