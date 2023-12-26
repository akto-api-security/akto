package com.akto.interceptor;

import com.akto.action.ApiTokenAction;
import com.akto.action.testing.AuthMechanismAction;
import com.akto.dto.ApiToken.Utility;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.opensymphony.xwork2.ActionInvocation;

public class UsageInterceptorUtil {

    /*
     * this function checks if an API call is 
     * linked to a feature based on it's request params.
     */

    private static boolean apiTokenCheck(Object actionObject, Utility utility) {
        boolean ret = false;
        if (actionObject instanceof ApiTokenAction) {
            ApiTokenAction action = (ApiTokenAction) actionObject;
            Utility type = action.getTokenUtility();
            if (type!=null && utility.equals(type)) {
                ret = true;
            }
        }
        return ret;
    }
    
    public static boolean checkContextSpecificFeatureAccess(ActionInvocation invocation, String featureLabel) {

        boolean ret = true;
        if (featureLabel == null) {
            return ret;
        }

        try {
            Object actionObject = invocation.getInvocationContext().getActionInvocation().getAction();

            switch (featureLabel) {
                case "AUTOMATED_AUTH_TOKEN":
                    ret = false;

                    if (actionObject instanceof AuthMechanismAction) {
                        AuthMechanismAction action = (AuthMechanismAction) actionObject;
                        String type = action.getType();
                        if (AuthMechanismTypes.LOGIN_REQUEST.toString().equals(type)) {
                            ret = true;
                        }
                    }

                    return ret;
                case "AKTO_EXTERNAL_API":
                    ret = false;
                    ret = apiTokenCheck(actionObject, Utility.EXTERNAL_API);
                    return ret;
                case "CI_CD_INTEGRATION":
                    ret = false;
                    ret = apiTokenCheck(actionObject, Utility.CICD);
                    return ret;
                default:
                    return ret;
            }
        } catch (Exception e) {
            return ret;
        }
    }
}
