package com.akto.action;

import com.akto.dao.CrawlerRunDao;
import com.akto.dao.CrawlerUrlDao;
import com.akto.dto.CrawlerRun;
import com.akto.dto.CrawlerUrl;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Getter
@Setter
public class DastAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DastAction.class, LogDb.DASHBOARD);

    private String crawlId;
    private List<CrawlerRun> crawlerRuns;
    private List<CrawlerUrl> crawlerUrls;
    private BasicDBObject response;
    private int skip;
    private int limit;
    private Map<String, List> filters;
    private String searchString;
    private String sortKey;
    private int sortOrder;

    public String fetchAllDastScans() {
        try {
            loggerMaker.infoAndAddToDb("Fetching all DAST scans");
            crawlerRuns = CrawlerRunDao.instance.findAll(
                Filters.or(
                    Filters.exists(CrawlerRun.STATUS, false), // for backward compatibility
                    Filters.eq(CrawlerRun.STATUS, CrawlerRun.CrawlerRunStatus.RUNNING.name()),
                    Filters.eq(CrawlerRun.STATUS, CrawlerRun.CrawlerRunStatus.COMPLETED.name())
                )
            );
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

            if (skip < 0) {
                skip *= -1;
            }
            if (limit < 0) {
                limit *= -1;
            }
            int pageLimit = Math.min(limit == 0 ? 50 : limit, 200);

            List<Bson> filterList = new ArrayList<>();
            filterList.add(Filters.eq(CrawlerUrl.CRAWL_ID, crawlId));

            if (searchString != null && searchString.length() >= 3) {
                String regexPattern = ".*" + Pattern.quote(searchString) + ".*";
                filterList.add(Filters.or(
                    Filters.regex(CrawlerUrl.URL, regexPattern, "i"),
                    Filters.regex(CrawlerUrl.SOURCE_URL, regexPattern, "i"),
                    Filters.regex(CrawlerUrl.BUTTON_TEXT, regexPattern, "i")
                ));
            }

            if (filters != null) {
                for (Map.Entry<String, List> entry : filters.entrySet()) {
                    String key = entry.getKey();
                    List value = entry.getValue();
                    if (value == null || value.isEmpty()) continue;

                    switch (key) {
                        case "url":
                            if (!value.isEmpty()) {
                                filterList.add(Filters.regex(CrawlerUrl.URL, ".*" + Pattern.quote(value.get(0).toString()) + ".*", "i"));
                            }
                            break;
                        case "sourceUrl":
                            if (!value.isEmpty()) {
                                filterList.add(Filters.regex(CrawlerUrl.SOURCE_URL, ".*" + Pattern.quote(value.get(0).toString()) + ".*", "i"));
                            }
                            break;
                        case "buttonText":
                            if (!value.isEmpty()) {
                                filterList.add(Filters.regex(CrawlerUrl.BUTTON_TEXT, ".*" + Pattern.quote(value.get(0).toString()) + ".*", "i"));
                            }
                            break;
                        case "status":
                            List<Bson> statusFilters = new ArrayList<>();
                            for (Object statusVal : value) {
                                String status = statusVal.toString();
                                if ("Non-terminal".equalsIgnoreCase(status)) {
                                    statusFilters.add(Filters.eq(CrawlerUrl.ACCEPTED, true));
                                } else if ("Terminal".equalsIgnoreCase(status)) {
                                    statusFilters.add(Filters.eq(CrawlerUrl.ACCEPTED, false));
                                }
                            }
                            if (!statusFilters.isEmpty()) {
                                filterList.add(Filters.or(statusFilters));
                            }
                            break;
                        default:
                            break;
                    }
                }
            }

            Bson combinedFilter = Filters.and(filterList);
            long totalCount = CrawlerUrlDao.instance.getMCollection().countDocuments(combinedFilter);

            Bson sort;
            String sortField = (sortKey != null && !sortKey.isEmpty()) ? sortKey : CrawlerUrl.TIMESTAMP;
            if (sortOrder == -1) {
                sort = Sorts.ascending(sortField);
            } else {
                sort = Sorts.descending(sortField);
            }
            crawlerUrls = CrawlerUrlDao.instance.findAll(combinedFilter, skip, pageLimit, sort);

            response = new BasicDBObject();
            response.put("crawlerUrls", crawlerUrls);
            response.put("totalCount", totalCount);

            loggerMaker.infoAndAddToDb("Fetched " + crawlerUrls.size() + " crawler URLs, total: " + totalCount);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while fetching DAST scan: " + e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchDastScanCounts() {
        try {
            loggerMaker.infoAndAddToDb("Fetching DAST scan counts for crawl ID: " + crawlId);
            if (StringUtils.isEmpty(crawlId)) {
                addActionError("Crawl ID is required");
                return Action.ERROR.toUpperCase();
            }

            Bson filter = Filters.eq(CrawlerUrl.CRAWL_ID, crawlId);
            long totalCount = CrawlerUrlDao.instance.getMCollection().countDocuments(filter);
            long acceptedCount = CrawlerUrlDao.instance.getMCollection().countDocuments(
                Filters.and(filter, Filters.eq(CrawlerUrl.ACCEPTED, true))
            );
            long rejectedCount = CrawlerUrlDao.instance.getMCollection().countDocuments(
                Filters.and(filter, Filters.eq(CrawlerUrl.ACCEPTED, false))
            );

            response = new BasicDBObject();
            response.put("totalCount", totalCount);
            response.put("acceptedCount", acceptedCount);
            response.put("rejectedCount", rejectedCount);

            loggerMaker.infoAndAddToDb("Fetched counts - Total: " + totalCount + ", Accepted: " + acceptedCount + ", Rejected: " + rejectedCount);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while fetching DAST scan counts: " + e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

}
