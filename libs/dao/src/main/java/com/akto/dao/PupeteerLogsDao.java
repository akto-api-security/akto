package com.akto.dao;

public class PupeteerLogsDao extends LogsDao {

    public static final PupeteerLogsDao instance = new PupeteerLogsDao();

    @Override
    public String getCollName() {
        return "logs_puppeteer";
    }
    
}
