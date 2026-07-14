package com.akto.dao;

public class SmartTestingLogsDao extends LogsDao {

    public static final SmartTestingLogsDao instance = new SmartTestingLogsDao();

    @Override
    public String getCollName() {
        return "logs_smart_testing";
    }

}
