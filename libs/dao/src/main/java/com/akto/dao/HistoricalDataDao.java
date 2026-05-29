package com.akto.dao;

import com.akto.dto.HistoricalData;

public class HistoricalDataDao extends AccountsContextDao<HistoricalData> {

    public static final HistoricalDataDao instance = new HistoricalDataDao();

    public void createIndicesIfAbsent() {
        String[] fieldNames = new String[] { HistoricalData.TIME, HistoricalData.API_COLLECTION_ID };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    @Override
    public String getCollName() {
        return "historical_data";
    }

    @Override
    public Class<HistoricalData> getClassT() {
        return HistoricalData.class;
    }
}
