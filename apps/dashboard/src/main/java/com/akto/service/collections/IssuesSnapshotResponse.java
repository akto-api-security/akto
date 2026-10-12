package com.akto.service.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/** The issue and test based numbers of the home dashboard, from one call. */
@Getter
@AllArgsConstructor
public class IssuesSnapshotResponse {

    private final long totalIssuesCount;
    /** open and fixed issues at the start of the range, to show the change */
    private final long oldOpenCount;
    /** open issues by severity */
    private final Map<String, Long> severityCounts;
    /** apis with an open critical issue */
    private final long vulnerableApis;
    /** apis with an open issue of any known test (the "broken authentication" tile) */
    private final long brokenAuthApis;
    /** apis with two or more open issues */
    private final long highValueIssuesApis;
    private final long vemVulnerableApis;
    private final long mhhVulnerableApis;
    /** test sub categories that more than one api has an open issue for */
    private final long remediationPlaybooks;
    private final long misConfiguredTests;
    /** open issues found within the range, by test sub category (the critical findings chart) */
    private final Map<String, Long> subCategoryCounts;
}
