package com.akto.dao;

public class RuntimeLogsDao extends LogsDao {

    public static final RuntimeLogsDao instance = new RuntimeLogsDao();

    @Override
    public String getCollName() {
        return "logs_runtime";
    }

    @Override
    public LogsDao getInstance(){
        return instance;
    }

}
