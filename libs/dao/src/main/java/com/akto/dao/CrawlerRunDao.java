package com.akto.dao;

import com.akto.dto.CrawlerRun;

public class CrawlerRunDao extends AccountsContextDao<CrawlerRun> {

    public static final CrawlerRunDao instance = new CrawlerRunDao();

    private CrawlerRunDao() {}

    @Override
    public String getCollName() {
        return "crawler_runs";
    }

    @Override
    public Class<CrawlerRun> getClassT() {
        return CrawlerRun.class;
    }
}
