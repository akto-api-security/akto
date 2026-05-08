package com.akto.action;

import com.akto.dao.CrawlerRunDao;
import com.akto.dao.CrawlerUrlDao;
import com.akto.dto.CrawlerRun;
import com.akto.dto.CrawlerUrl;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class DastAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DastAction.class, LogDb.DASHBOARD);

    private String crawlId;
    private List<CrawlerRun> crawlerRuns;
    private List<CrawlerUrl> crawlerUrls;

    public String fetchAllDastScans() {
        try {
            loggerMaker.infoAndAddToDb("Fetching all DAST scans");
            crawlerRuns = CrawlerRunDao.instance.findAll(Filters.empty());
            loggerMaker.infoAndAddToDb("Fetched " + crawlerRuns.size() + " DAST scans");
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while fetching DAST scans: " + e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchDastScan() {
        try {
            loggerMaker.infoAndAddToDb("Fetching DAST scan for crawl ID: " + crawlId);
            if (StringUtils.isEmpty(crawlId)) {
                addActionError("Crawl ID is required");
                return Action.ERROR.toUpperCase();
            }
            crawlerUrls = CrawlerUrlDao.instance.findAll(Filters.eq(CrawlerUrl.CRAWL_ID, crawlId));
            loggerMaker.infoAndAddToDb("Fetched " + crawlerUrls.size() + " crawler URLs");
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while fetching DAST scan: " + e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
        this.crawlId = crawlId;
    }

    public List<CrawlerRun> getCrawlerRuns() {
        return crawlerRuns;
    }

    public void setCrawlerRuns(List<CrawlerRun> crawlerRuns) {
        this.crawlerRuns = crawlerRuns;
    }

    public List<CrawlerUrl> getCrawlerUrls() {
        return crawlerUrls;
    }

    public void setCrawlerUrls(List<CrawlerUrl> crawlerUrls) {
        this.crawlerUrls = crawlerUrls;
    }
}
