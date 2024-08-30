package com.akto.dao;

import com.akto.dto.traffic.SusSampleData;

public class SusSampleDataDao extends AccountsContextDao<SusSampleData> {
    public static final SusSampleDataDao instance = new SusSampleDataDao();

    @Override
    public String getCollName() {
        return "sus_sample_data";
    }

    @Override
    public Class<SusSampleData> getClassT() {
        return SusSampleData.class;
    }
}
