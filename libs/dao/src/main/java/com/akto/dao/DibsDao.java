package com.akto.dao;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.Dibs;

public class DibsDao extends AccountsContextDao<Dibs> {

    public static final DibsDao instance = new DibsDao();

    private DibsDao() {}

    @Override
    public String getCollName() {
        return "dibs";
    }

    @Override
    public Class<Dibs> getClassT() {
        return Dibs.class;
    }
    
}
