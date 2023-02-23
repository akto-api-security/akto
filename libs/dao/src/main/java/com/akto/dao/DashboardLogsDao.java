package com.akto.dao;

public class DashboardLogsDao extends LogsDao {
    
    public static final DashboardLogsDao instance = new DashboardLogsDao();

    @Override
    public String getCollName() {
        return "logs_dashboard";
    }
    
    @Override
    public LogsDao getInstance(){
        return instance;
    }
    
}
