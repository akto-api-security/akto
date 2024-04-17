package com.akto.dao;

import com.akto.dto.dependency_flow.ModifyHostDetail;

public class ModifyHostDetailsDao extends AccountsContextDao<ModifyHostDetail>{

    public static ModifyHostDetailsDao instance = new ModifyHostDetailsDao();

    @Override
    public Class<ModifyHostDetail> getClassT() {
        return ModifyHostDetail.class;
    }

    @Override
    public String getCollName() {
        return "modify_host_details";
    }
    
}
