// The counts behind the action item cards, worked out from the two snapshot calls the page already made
// (the lists behind them are fetched a page at a time when a flyout opens, see FlyoutTable). A count the
// backend could not work out comes as -1 and so never shows as an action item.
export function buildActionItemsData(posture, issues, jiraTicketUrlMap) {
    const counts = posture?.actionItemCounts || {};

    // third party apis that appeared within the range; unknown when the range's start could not be worked out
    const thirdPartyAt = (stats) => stats?.accessTypeMap?.THIRD_PARTY || 0;
    const thirdPartyDiff = posture && !posture.startUnavailable
        ? thirdPartyAt(posture.apiStatsEnd) - thirdPartyAt(posture.apiStatsStart)
        : 0;

    return {
        highRiskCount: counts.highRisk || 0,
        sensitiveDataCount: counts.sensitiveApis || 0,
        unauthenticatedCount: counts.unauthenticated || 0,
        thirdPartyDiff,
        highRiskThirdPartyCount: counts.highRiskThirdParty || 0,
        shadowApisCount: counts.shadowApis || 0,
        sensitiveAndUnauthenticatedCount: counts.sensitiveAndUnauthenticated || 0,
        jiraTicketUrlMap: jiraTicketUrlMap || {},
        notTestedApiCount: counts.notTested || 0,
        onlyOnceTestedApiCount: counts.onlyOnceTested || 0,
        vulnerableApiCount: issues?.vulnerableApis || 0,
        misConfiguredTestsCount: issues?.misConfiguredTests || 0,
        brokenAuthIssuesCount: issues?.brokenAuthApis || 0,
        highValueIssuesCount: issues?.highValueIssuesApis || 0,
        urlsByIssuesTotalCount: issues?.remediationPlaybooks || 0,
        vemVulnerableApisCount: issues?.vemVulnerableApis || 0,
        mhhVulnerableApisCount: issues?.mhhVulnerableApis || 0,
    };
}
