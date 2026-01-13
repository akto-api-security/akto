package com.akto.dao;

public class DastLogsDao extends LogsDao {
    public static final DastLogsDao instance = new DastLogsDao();

    @Override
    public String getCollName() {
        return "dast_logs";
    }
}
