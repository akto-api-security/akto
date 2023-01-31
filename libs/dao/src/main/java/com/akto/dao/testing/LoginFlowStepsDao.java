package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.LoginFlowStepsData;

public class LoginFlowStepsDao extends AccountsContextDao<LoginFlowStepsData> {

    public static final LoginFlowStepsDao instance = new LoginFlowStepsDao();

    @Override
    public String getCollName() {
        return "login_flow_steps";
    }

    @Override
    public Class<LoginFlowStepsData> getClassT() {
        return LoginFlowStepsData.class;
    }
}
