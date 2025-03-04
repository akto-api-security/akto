package com.akto.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.akto.dto.testing.TestSuite;

import com.akto.action.testing.AuthMechanismAction;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dto.testing.AuthParamData;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;

public class OnboardingAction extends UserAction {


    private TestSuite[] testSuites;
    public String fetchTestSuites() {
        this.testSuites = TestSuite.values();
        return SUCCESS.toUpperCase();
    }

    public String skipOnboarding() {
        AccountSettingsDao.instance.updateOnboardingFlag(false);
        return SUCCESS.toUpperCase();
    }


    private ArrayList<AuthParamData> authParamData;
    private int collectionId;
    private TestSuite testSuite;
    private String testingRunHexId;

    
    public String runTestOnboarding() {

        if (testSuite == null) {
            addActionError("Test suite can't be null");
            return ERROR.toUpperCase();
        }

        List<String> selectedTests = testSuite.tests;

        AuthMechanismAction authMechanismAction = new AuthMechanismAction();
        authMechanismAction.setSession(getSession());
        authMechanismAction.setType(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString());
        authMechanismAction.setAuthParamData(authParamData);
        authMechanismAction.addAuthMechanism();

        StartTestAction startTestAction = new StartTestAction();
        startTestAction.setSession(getSession());
        startTestAction.setRecurringDaily(false);
        startTestAction.setApiCollectionId(collectionId);
        startTestAction.setTestIdConfig(0);
        startTestAction.setType(TestingEndpoints.Type.COLLECTION_WISE);
        startTestAction.setSelectedTests(selectedTests);
        startTestAction.setTestName(Constants.ONBOARDING_DEMO_TEST);
        startTestAction.setTestRunTime(-1);
        startTestAction.setMaxConcurrentRequests(100);
        startTestAction.startTest();

        testingRunHexId = startTestAction.getTestingRunHexId();

        AccountSettingsDao.instance.updateOnboardingFlag(false);

        return SUCCESS.toUpperCase();
    }
    public void setAuthParamData(ArrayList<AuthParamData> authParamData) {
        this.authParamData = authParamData;
    }

    public void setCollectionId(int collectionId) {
        this.collectionId = collectionId;
    }

    public void setTestSuite(TestSuite testSuite) {
        this.testSuite = testSuite;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public TestSuite[] getTestSuites() {
        return testSuites;
    }
}
