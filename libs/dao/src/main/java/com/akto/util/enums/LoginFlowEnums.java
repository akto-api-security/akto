package com.akto.util.enums;

public class LoginFlowEnums {

    public enum AuthMechanismTypes {
        HARDCODED,
        LOGIN_REQUEST
    }

    public enum LoginStepTypesEnums {
        LOGIN_FORM,
        MOBILE_CODE_VERIFICATION,
        EMAIL_CODE_VERIFICATION,
        OTP_VERIFICATION,
        RECORDED_FLOW
    }
}
