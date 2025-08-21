import api from '../api';
import observeApi from '../../observe/api';
import settingsModule from '../../settings/module';
import func from '../../../../../util/func';
import LocalStore from '../../../../main/LocalStorageStore';

export async function fetchActionItemsData() {
    const endTimestamp = func.timeNow();
    const startTimestamp = endTimestamp - 3600 * 24 * 7;

    const subCategoryMap = LocalStore.getState().subCategoryMap || {};
    const allSubCategories = Object.keys(subCategoryMap);

    const results = await Promise.allSettled([
        api.fetchApiStats(startTimestamp, endTimestamp),
        observeApi.fetchCountMapOfApis(),
        api.fetchSensitiveAndUnauthenticatedValue(false),
        api.fetchHighRiskThirdPartyValue(false),
        api.fetchShadowApisValue(false),
        settingsModule.fetchAdminInfo(),
        api.fetchUnauthenticatedApis(false),
        api.getNotTestedAPICount(false),
        api.getOnlyOnceTestedAPICount(false),
        api.getVulnerableApiCount(false),
        api.getMisConfiguredTestsCount(),
        api.fetchIssuesByApis(),
        api.fetchUrlsByIssues(false),
        api.fetchBrokenAuthenticationIssues(allSubCategories, false),
        api.fetchVulnerableApisByCategory("VEM", false),
        api.fetchVulnerableApisByCategory("MHH", false)
    ]);

    const [
        apiStatsResult,
        countMapRespResult,
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        adminSettingsResult,
        unauthenticatedApisResult,
        notTestedApiCountResult,
        onlyOnceTestedApiCountResult,
        vulnerableApiCountResult,
        misConfiguredTestsCountResult,
        issuesByApisResult,
        urlsByIssuesResult,
        brokenAuthIssuesResult,
        vemVulnerableApisResult,
        mhhVulnerableApisResult
    ] = results;

    const apiStats = apiStatsResult.status === 'fulfilled' ? apiStatsResult.value : null;
    const countMapResp = countMapRespResult.status === 'fulfilled' ? countMapRespResult.value : null;
    const sensitiveAndUnauthenticatedCount = sensitiveAndUnauthenticatedValueResult.status === 'fulfilled' ? sensitiveAndUnauthenticatedValueResult.value.sensitiveUnauthenticatedEndpointsCount || 0 : 0;
    const highRiskThirdPartyCount = highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult.value.highRiskThirdPartyEndpointsCount || 0 : 0;
    const shadowApisCount = shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult.value.shadowApisCount || 0 : 0;
    const adminSettings = adminSettingsResult.status === 'fulfilled' ? adminSettingsResult.value.resp : {};
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult.value.unauthenticatedApis || 0 : 0;
    const jiraTicketUrlMap = adminSettings?.jiraTicketUrlMap || {};
    const issuesByApis = issuesByApisResult.status === 'fulfilled' ? issuesByApisResult.value : null;
    const urlsByIssues = urlsByIssuesResult.status === 'fulfilled' ? urlsByIssuesResult.value : null;
    const brokenAuthIssuesResp = brokenAuthIssuesResult.status === 'fulfilled' ? brokenAuthIssuesResult.value : null;
    const urlsByIssuesTotalCount = urlsByIssues && typeof urlsByIssues.totalCount === 'number' ? urlsByIssues.totalCount : 0;
    // Count URLs where value is >= 2
    let highValueIssuesCount = 0;
    if (issuesByApis && issuesByApis.countByAPIs && typeof issuesByApis.countByAPIs === 'object') {
        highValueIssuesCount = Object.values(issuesByApis.countByAPIs).filter(value => value >= 2).length;
    }

    let highRiskCount = 0;
    let unauthenticatedCount = unauthenticatedApis;
    let thirdPartyDiff = 0;
    let sensitiveDataCount = countMapResp?.totalApisCount || 0;
    let notTestedApiCount = notTestedApiCountResult.status === 'fulfilled' ? notTestedApiCountResult.value?.notTestedEndpointsCount || 0 : 0;
    let onlyOnceTestedApiCount = onlyOnceTestedApiCountResult.status === 'fulfilled' ? onlyOnceTestedApiCountResult.value?.onlyOnceTestedEndpointsCount || 0 : 0;
    let vulnerableApiCount = vulnerableApiCountResult.status === 'fulfilled' ? vulnerableApiCountResult.value?.buaCategoryCount || 0 : 0;
    let misConfiguredTestsCount = misConfiguredTestsCountResult.status === 'fulfilled' ? misConfiguredTestsCountResult.value?.misConfiguredTestsCount || 0 : 0;
    let brokenAuthIssuesCount = brokenAuthIssuesResp ? brokenAuthIssuesResp.buaCategoryCount || 0 : 0;
    let vemVulnerableApisCount = vemVulnerableApisResult.status === 'fulfilled' ? vemVulnerableApisResult.value?.endpointsCount || 0 : 0;
    let mhhVulnerableApisCount = mhhVulnerableApisResult.status === 'fulfilled' ? mhhVulnerableApisResult.value?.endpointsCount || 0 : 0;

    if (apiStats?.apiStatsEnd && apiStats?.apiStatsStart) {
        const { apiStatsEnd, apiStatsStart } = apiStats;

        highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
            .filter(([score]) => parseInt(score) > 3)
            .reduce((total, [, count]) => total + count, 0);
        thirdPartyDiff = (apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0) - (apiStatsStart.accessTypeMap?.THIRD_PARTY || 0);
    }

    return {
        highRiskCount,
        sensitiveDataCount,
        unauthenticatedCount,
        thirdPartyDiff,
        highRiskThirdPartyCount,
        shadowApisCount,
        sensitiveAndUnauthenticatedCount,
        jiraTicketUrlMap,
        notTestedApiCount,
        onlyOnceTestedApiCount,
        vulnerableApiCount,
        misConfiguredTestsCount,
        brokenAuthIssuesCount,
        highValueIssuesCount, 
        issuesByApis,
        urlsByIssues,
        urlsByIssuesTotalCount,
        vemVulnerableApisCount,
        mhhVulnerableApisCount
    };
}


export async function fetchAllActionItemsApiInfo() {
    const subCategoryMap = LocalStore.getState().subCategoryMap || {};
    const allSubCategories = Object.keys(subCategoryMap);

    const results = await Promise.allSettled([
        api.fetchSensitiveAndUnauthenticatedValue(true),
        api.fetchHighRiskThirdPartyValue(true),
        api.fetchShadowApisValue(true),
        api.fetchUnauthenticatedApis(true),
        api.fetchActionItemsApiInfo('HIGH_RISK'),
        api.fetchActionItemsApiInfo('SENSITIVE'),
        api.fetchActionItemsApiInfo('THIRD_PARTY'),
        api.getNotTestedAPICount(true),
        api.getOnlyOnceTestedAPICount(true),
        api.getMisConfiguredTestsCount(true),
        api.getVulnerableApiCount(true),
        api.fetchBrokenAuthenticationIssues(allSubCategories, true),
        api.fetchIssuesByApis(true),
        api.fetchUrlsByIssues(true),
        api.fetchVulnerableApisByCategory("VEM", true),
        api.fetchVulnerableApisByCategory("MHH", true),
        observeApi.getSensitiveInfoForCollections('topSensitive')
    ]);

    const [
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        unauthenticatedApisResult,
        highRiskResult,
        sensitiveResult,
        thirdPartyResult,
        notTestedApiInfoResult,
        onlyOnceTestedApiInfoResult,
        misConfiguredTestsApiInfoResult,
        vulnerableApiCountResult,
        brokenAuthIssuesApiInfoResult,
        issuesByApisResult,
        urlsByIssuesResult,
        vemVulnerableApisApiInfoResult,
        mhhVulnerableApisApiInfoResult,
        topSensitiveResult
    ] = results;

    const sensitiveAndUnauthenticatedApis = sensitiveAndUnauthenticatedValueResult.status === 'fulfilled' ? sensitiveAndUnauthenticatedValueResult?.value?.sensitiveUnauthenticatedEndpointsApiInfo || [] : [];
    const highRiskThirdPartyApis = highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult?.value?.highRiskThirdPartyEndpointsApiInfo || [] : [];
    const shadowApis = shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult?.value?.shadowApisCount || [] : [];
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult?.value?.unauthenticatedApiList || [] : [];
    const highRiskApis = highRiskResult.status === 'fulfilled' ? highRiskResult?.value?.response?.apiInfos || [] : [];
    const sensitiveDataEndpoints = sensitiveResult.status === 'fulfilled' ? sensitiveResult?.value?.response?.apiInfos || [] : [];
    const thirdPartyApis = thirdPartyResult.status === 'fulfilled' ? thirdPartyResult?.value?.response?.apiInfos || [] : [];
    const notTestedEndpointsApiInfo = notTestedApiInfoResult.status === 'fulfilled' ? notTestedApiInfoResult.value?.notTestedEndpointsApiInfo || [] : [];
    const onlyOnceTestedEndpointsApiInfo = onlyOnceTestedApiInfoResult.status === 'fulfilled' ? onlyOnceTestedApiInfoResult.value?.onlyOnceTestedEndpointsApiInfo || [] : [];
    const misConfiguredTestsApiInfo = misConfiguredTestsApiInfoResult.status === 'fulfilled' ? misConfiguredTestsApiInfoResult.value?.misConfiguredTestsApiInfo || [] : [];
    const vulnerableApiCountApiInfo = vulnerableApiCountResult.status === 'fulfilled' ? vulnerableApiCountResult.value?.buaCategoryApiInfo || [] : [];
    const brokenAuthIssuesApiInfo = brokenAuthIssuesApiInfoResult.status === 'fulfilled' ? brokenAuthIssuesApiInfoResult.value?.buaCategoryApiInfo || [] : [];
    const issuesByApisForAllActionItems = issuesByApisResult.status === 'fulfilled' ? issuesByApisResult.value : null;
    const urlsByIssuesForAllActionItems = urlsByIssuesResult.status === 'fulfilled' ? urlsByIssuesResult.value : null;
    const vemVulnerableApisApiInfo = vemVulnerableApisApiInfoResult.status === 'fulfilled' ? vemVulnerableApisApiInfoResult.value?.vulnerableApisApiInfo || [] : [];
    const mhhVulnerableApisApiInfo = mhhVulnerableApisApiInfoResult.status === 'fulfilled' ? mhhVulnerableApisApiInfoResult.value?.vulnerableApisApiInfo || [] : [];
    const sensitiveSubtypesInUrl = topSensitiveResult.status === 'fulfilled' ? topSensitiveResult.value?.sensitiveSubtypesInUrl || [] : [];


    const multipleIssuesApiInfo = Array.isArray(issuesByApisForAllActionItems?.issueNamesByAPIs)
        ? issuesByApisForAllActionItems.issueNamesByAPIs
            .map(item => {
                const apiInfo = item?.apiInfo;
                const issueNames = Array.isArray(item?.issueNames) ? item.issueNames : [];
                const labels = issueNames.map(n => (subCategoryMap?.[n]?.testName) || n).filter(Boolean);
                return apiInfo ? { ...apiInfo, issueLabels: Array.from(new Set(labels)) } : null;
            })
            .filter(Boolean)
        : [];
        
    return {
        highRiskApis: highRiskApis,
        sensitiveDataEndpoints: sensitiveDataEndpoints,
        unauthenticatedApis: unauthenticatedApis,
        thirdPartyApis: thirdPartyApis,
        highRiskThirdParty: highRiskThirdPartyApis,
        shadowApis: shadowApis,
        sensitiveAndUnauthenticated: sensitiveAndUnauthenticatedApis,
        notTestedEndpointsApiInfo: notTestedEndpointsApiInfo,
        onlyOnceTestedEndpointsApiInfo: onlyOnceTestedEndpointsApiInfo,
        misConfiguredTestsApiInfo: misConfiguredTestsApiInfo,
        vulnerableApiCountApiInfo: vulnerableApiCountApiInfo,
        brokenAuthIssuesApiInfo: brokenAuthIssuesApiInfo,
        multipleIssuesApiInfo: multipleIssuesApiInfo,
        urlsByIssues: urlsByIssuesForAllActionItems,
        vemVulnerableApisApiInfo: vemVulnerableApisApiInfo,
        mhhVulnerableApisApiInfo: mhhVulnerableApisApiInfo,
        sensitiveSubtypesInUrl: sensitiveSubtypesInUrl,
    };
}