package com.akto.dao;

import com.akto.dto.dependency_flow.ReplaceDetail;

public class ReplaceDetailsDao extends AccountsContextDao<ReplaceDetail> {

    public static final ReplaceDetailsDao instance = new ReplaceDetailsDao();

    @Override
    public String getCollName() {
        return "replace_details";
    }

    @Override
    public Class<ReplaceDetail> getClassT() {
        return ReplaceDetail.class;
    }
}
