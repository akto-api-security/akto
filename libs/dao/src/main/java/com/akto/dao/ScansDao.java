package com.akto.dao;

import com.akto.dto.Scan;

public class ScansDao extends AccountsContextDao<Scan> {

    @Override
    public String getCollName() {
        return "scans";
    }

    @Override
    public Class<Scan> getClassT() {
        return Scan.class;
    }

}
