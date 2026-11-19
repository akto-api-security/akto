package com.akto.dao;

public class MiniTestingOrchestratorLogsDao extends LogsDao {
    public static final MiniTestingOrchestratorLogsDao instance = new MiniTestingOrchestratorLogsDao();

    @Override
    public String getCollName() {
        return "logs_mini_testing_orchestrator";
    }
}
