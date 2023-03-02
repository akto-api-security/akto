package com.akto.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;

import com.akto.action.testing.AuthMechanismAction;
import com.akto.action.testing.StartTestAction;
import com.akto.dto.testing.AuthParamData;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.util.enums.LoginFlowEnums;

public class OnboardingAction extends UserAction {


    private ArrayList<AuthParamData> authParamData;
    private int collectionId;
    private String testSuite;
    private String testingRunHexId;

    
    public String runTestOnboarding() {

        List<String> selectedTests = new ArrayList<>();
        switch (testSuite) {
            case "BUSINESS_LOGIC_SCAN":
                selectedTests = Arrays.asList("REPLACE_AUTH_TOKEN", "ADD_USER_ID", "PARAMETER_POLLUTION", "REPLACE_AUTH_TOKEN_OLD_VERSION");
                break;
            case "PASSIVE_SCAN":
                selectedTests = Arrays.asList("REPLACE_AUTH_TOKEN", "ADD_USER_ID");
                break;
            case "DEEP_SCAN":
                selectedTests = Arrays.asList("REPLACE_AUTH_TOKEN", "ADD_USER_ID", "PARAMETER_POLLUTION", "REPLACE_AUTH_TOKEN_OLD_VERSION", "https://raw.githubusercontent.com/akto-api-security/testing_sources/master/Misconfiguration/swagger-detection/swagger_file_basic.yml");
                break;
        
            default:
                break;
        }

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
        startTestAction.setTestName("Onboarding demo test");
        startTestAction.setTestRunTime(-1);
        startTestAction.setMaxConcurrentRequests(100);
        startTestAction.startTest();

        testingRunHexId = startTestAction.getTestingRunHexId();


        return SUCCESS.toUpperCase();
    }
    public void setAuthParamData(ArrayList<AuthParamData> authParamData) {
        this.authParamData = authParamData;
    }
    public void setCollectionId(int collectionId) {
        this.collectionId = collectionId;
    }
    public void setTestSuite(String testSuite) {
        this.testSuite = testSuite;
    }
    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    
    
}
