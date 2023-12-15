package com.akto.interceptor;

import com.akto.action.testing.AuthMechanismAction;
import com.akto.util.enums.LoginFlowEnums.AuthMechanismTypes;
import com.opensymphony.xwork2.ActionInvocation;

public class UsageInterceptorUtil {

    /*
     * this function checks if an API call is linked to a feature based on it's
     * request params.
     */
    public static boolean checkContextSpecificFeatureAccess(ActionInvocation invocation, String featureLabel) {

        boolean ret = true;
        if (featureLabel == null) {
            return ret;
        }

        Object actionObject = invocation.getInvocationContext().getActionInvocation().getAction();

        switch (featureLabel) {
            case "AUTOMATED_AUTH_TOKEN":
                ret = false;

                if (actionObject instanceof AuthMechanismAction) {
                    AuthMechanismAction action = (AuthMechanismAction) actionObject;
                    if (action.getType() != null &&
                            action.getType().equals(AuthMechanismTypes.LOGIN_REQUEST.toString())) {
                        ret = true;
                    }
                }

                return ret;
            default:
                return ret;
        }
    }
}
