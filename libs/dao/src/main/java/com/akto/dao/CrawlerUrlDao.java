package com.akto.dao;

import com.akto.dto.CrawlerUrl;

public class CrawlerUrlDao extends AccountsContextDao<CrawlerUrl> {

    public static final CrawlerUrlDao instance = new CrawlerUrlDao();

    private CrawlerUrlDao() {}

    @Override
    public String getCollName() {
        return "crawler_urls";
    }

    @Override
    public Class<CrawlerUrl> getClassT() {
        return CrawlerUrl.class;
    }

    public void createIndicesIfAbsent() {
        String[] crawlIdIndex = {CrawlerUrl.CRAWL_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), crawlIdIndex, false);
    }
}
