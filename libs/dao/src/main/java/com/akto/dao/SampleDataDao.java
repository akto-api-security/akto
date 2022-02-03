package com.akto.dao;

import com.akto.dto.traffic.SampleData;

public class SampleDataDao extends AccountsContextDao<SampleData> {

    public static final SampleDataDao instance = new SampleDataDao();

    @Override
    public String getCollName() {
        return "sample_data";
    }

    @Override
    public Class<SampleData> getClassT() {
        return SampleData.class;
    }
    
}
