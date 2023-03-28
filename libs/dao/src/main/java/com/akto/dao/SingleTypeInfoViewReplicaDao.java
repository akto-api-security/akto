package com.akto.dao;

import com.akto.dto.testing.SingleTypeInfoView;

public class SingleTypeInfoViewReplicaDao extends AccountsContextDao<SingleTypeInfoView>{
    
    public static final SingleTypeInfoViewReplicaDao instance = new SingleTypeInfoViewReplicaDao();
    @Override
    public String getCollName() {
        return "single_type_info_view_replica";
    }

    @Override
    public Class<SingleTypeInfoView> getClassT() {
        return SingleTypeInfoView.class;
    }

}
