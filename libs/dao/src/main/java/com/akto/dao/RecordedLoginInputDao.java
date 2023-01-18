package com.akto.dao;

import com.akto.dto.RecordedLoginFlowInput;

public class RecordedLoginInputDao extends AccountsContextDao<RecordedLoginFlowInput> {
    
    public static final RecordedLoginInputDao instance = new RecordedLoginInputDao();
    @Override
    public String getCollName() {
        return "recorded_login_flow_input";
    }

    @Override
    public Class<RecordedLoginFlowInput> getClassT() {
        return RecordedLoginFlowInput.class;
    }   
}
