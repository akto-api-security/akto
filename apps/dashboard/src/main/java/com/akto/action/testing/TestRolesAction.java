package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.TestRoles;
import com.mongodb.client.model.Filters;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TestRolesAction extends UserAction {
    private List<TestRoles> testRoles;
    private List<ApiInfo.ApiInfoKey> includedApiList;
    private List<ApiInfo.ApiInfoKey> excludedApiList;
    private String regex;
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
        boolean areFieldsEmpty = (regex == null || regex.isEmpty())
                && (excludedApiList == null || excludedApiList.isEmpty())
                && (includedApiList == null || includedApiList.isEmpty());
        if ((roleName == null || roleName.isEmpty()) && areFieldsEmpty) {
            addActionError("Test role is empty with regex : " + (regex == null ? "null" : regex));
            return ERROR.toUpperCase();
        }
        try {
            if (regex != null && !regex.isEmpty()) {
                Pattern.compile(regex);
            }
        } catch (PatternSyntaxException e) {
            addActionError("invalid regex");
            return ERROR.toUpperCase();
        }

        if (TestRolesDao.instance.getMCollection().countDocuments(Filters.eq(TestRoles.NAME, roleName)) > 0) {//Role exists
            addActionError("Role already exists");
            return ERROR.toUpperCase();
        }
        //Valid role name and regex
        String logicalGroupName = roleName + EndpointLogicalGroup.GROUP_NAME_SUFFIX;

        EndpointLogicalGroup logicalGroup = EndpointLogicalGroupDao.instance.
                createUsingRegex(logicalGroupName, regex,this.getSUser().getLogin(), includedApiList, excludedApiList);
        TestRolesDao.instance.createTestRole(roleName, logicalGroup.getId(), this.getSUser().getLogin());
        return SUCCESS.toUpperCase();
    }
    public List<TestRoles> getTestRoles() {
        return testRoles;
    }

    public void setTestRoles(List<TestRoles> testRoles) {
        this.testRoles = testRoles;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public List<ApiInfo.ApiInfoKey> getIncludedApiList() {
        return includedApiList;
    }

    public void setIncludedApiList(List<ApiInfo.ApiInfoKey> includedApiList) {
        this.includedApiList = includedApiList;
    }

    public List<ApiInfo.ApiInfoKey> getExcludedApiList() {
        return excludedApiList;
    }

    public void setExcludedApiList(List<ApiInfo.ApiInfoKey> excludedApiList) {
        this.excludedApiList = excludedApiList;
    }
}
