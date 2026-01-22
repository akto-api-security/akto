package com.akto.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerRunDTO {

    private String startedBy;
    private int startTimestamp;
    private int endTimestamp;
    private String crawlId;
    private String hostname;
    private String outScopeUrls;
    private CrawlerRun.CrawlerRunStatus status;
    private String moduleName;
    private String username;
    private String password;
    private String apiKey;
    private String dashboardUrl;
    private Integer collectionId;
    private Integer accountId;
    private String errorMessage;
    private Object cookies;
    private Integer crawlingTime;
    private Map<String, String> customHeaders;

    public static CrawlerRunDTO fromCrawlerRun(CrawlerRun crawlerRun) {
        if (crawlerRun == null) {
            return null;
        }

        CrawlerRunDTO dto = new CrawlerRunDTO();

        // Handle null and empty strings properly
        dto.setStartedBy(crawlerRun.getStartedBy());
        dto.setStartTimestamp(crawlerRun.getStartTimestamp());
        dto.setEndTimestamp(crawlerRun.getEndTimestamp());
        dto.setCrawlId(crawlerRun.getCrawlId());
        dto.setHostname(crawlerRun.getHostname());
        dto.setOutScopeUrls(crawlerRun.getOutScopeUrls());
        dto.setStatus(crawlerRun.getStatus());
        dto.setModuleName(crawlerRun.getModuleName());
        dto.setUsername(crawlerRun.getUsername());
        dto.setPassword(crawlerRun.getPassword());
        dto.setApiKey(crawlerRun.getApiKey());
        dto.setDashboardUrl(crawlerRun.getDashboardUrl());
        dto.setCollectionId(crawlerRun.getCollectionId());
        dto.setAccountId(crawlerRun.getAccountId());
        dto.setErrorMessage(crawlerRun.getErrorMessage());
        dto.setCookies(crawlerRun.getCookies());
        dto.setCrawlingTime(crawlerRun.getCrawlingTime());
        dto.setCustomHeaders(crawlerRun.getCustomHeaders());

        return dto;
    }
}