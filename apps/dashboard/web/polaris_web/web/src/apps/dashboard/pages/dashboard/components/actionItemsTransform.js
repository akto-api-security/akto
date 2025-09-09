import api from '../api';
import observeApi from '../../observe/api';
import settingsModule from '../../settings/module';
import func from '../../../../../util/func';
import LocalStore from '../../../../main/LocalStorageStore';

export async function fetchActionItemsData() {
    const endTimestamp = func.timeNow();
    const startTimestamp = endTimestamp - 3600 * 24 * 7;

    // Fetch total count for batching (Sensitive & Unauthenticated)
    const initialResp = await api.fetchSensitiveAndUnauthenticatedValue(false, 0, 1);
    const totalCount = initialResp?.sensitiveUnauthenticatedEndpointsCount || 0;
    const limit = 500;
    const numBatches = Math.ceil(totalCount / limit);
    let batchPromises = [];
    for (let i = 0; i < numBatches; i++) {
        batchPromises.push(api.fetchSensitiveAndUnauthenticatedValue(false, i * limit, limit));
    }
    const batchResults = await Promise.allSettled(batchPromises);
    let sensitiveAndUnauthenticatedCount = 0;
    let allApiInfo = [];
    for (const result of batchResults) {
        if (result.status === 'fulfilled') {
            sensitiveAndUnauthenticatedCount += result.value?.sensitiveUnauthenticatedEndpointsCount || 0;
            if (result.value?.sensitiveUnauthenticatedEndpointsApiInfo) {
                allApiInfo.push(...result.value.sensitiveUnauthenticatedEndpointsApiInfo);
            }
        }
    }

    // Fetch High Risk Third Party
    const highRiskInitialResp = await api.fetchHighRiskThirdPartyValue(false, 0, 1);
    const highRiskTotalCount = highRiskInitialResp?.highRiskThirdPartyEndpointsCount || 0;
    const highRiskNumBatches = Math.ceil(highRiskTotalCount / limit);
    let highRiskBatchPromises = [];
    for (let i = 0; i < highRiskNumBatches; i++) {
        highRiskBatchPromises.push(api.fetchHighRiskThirdPartyValue(false, i * limit, limit));
    }
    const highRiskBatchResults = await Promise.allSettled(highRiskBatchPromises);
    let highRiskThirdPartyCount = 0;
    let allHighRiskThirdPartyApiInfo = [];
    for (const result of highRiskBatchResults) {
        if (result.status === 'fulfilled') {
            highRiskThirdPartyCount += result.value?.highRiskThirdPartyEndpointsCount || 0;
            if (result.value?.highRiskThirdPartyEndpointsApiInfo) {
                allHighRiskThirdPartyApiInfo.push(...result.value.highRiskThirdPartyEndpointsApiInfo);
            }
        }
    }

    // Fetch Shadow APIs
    const shadowInitialResp = await api.fetchShadowApisValue(false, 0, 1);
    const shadowTotalCount = shadowInitialResp?.shadowApisCount || 0;
    const shadowNumBatches = Math.ceil(shadowTotalCount / limit);
    let shadowBatchPromises = [];
    for (let i = 0; i < shadowNumBatches; i++) {
        shadowBatchPromises.push(api.fetchShadowApisValue(false, i * limit, limit));
    }
    const shadowBatchResults = await Promise.allSettled(shadowBatchPromises);
    let shadowApisCount = 0;
    let allShadowApisInfo = [];
    for (const result of shadowBatchResults) {
        if (result.status === 'fulfilled') {
            shadowApisCount += result.value?.shadowApisCount || 0;
            if (result.value?.shadowApisInfo) {
                allShadowApisInfo.push(...result.value.shadowApisInfo);
            }
        }
    }

    // Fetch Not Tested APIs
    const notTestedInitialResp = await api.getNotTestedAPICount(false, 0, 1);
    const notTestedTotalCount = notTestedInitialResp?.notTestedEndpointsCount || 0;
    const notTestedNumBatches = Math.ceil(notTestedTotalCount / limit);
    let notTestedBatchPromises = [];
    for (let i = 0; i < notTestedNumBatches; i++) {
        notTestedBatchPromises.push(api.getNotTestedAPICount(false, i * limit, limit));
    }
    const notTestedBatchResults = await Promise.allSettled(notTestedBatchPromises);
    let notTestedApiCountCombined = 0;
    let allNotTestedEndpointsApiInfo = [];
    for (const result of notTestedBatchResults) {
        if (result.status === 'fulfilled') {
            notTestedApiCountCombined += result.value?.notTestedEndpointsCount || 0;
            if (result.value?.notTestedEndpointsApiInfo) {
                allNotTestedEndpointsApiInfo.push(...result.value.notTestedEndpointsApiInfo);
            }
        }
    }

    const subCategoryMap = LocalStore.getState().subCategoryMap || {};
    const allSubCategories = Object.keys(subCategoryMap);

    const results = await Promise.allSettled([
        api.fetchApiStats(startTimestamp, endTimestamp),
        observeApi.fetchCountMapOfApis(),
        settingsModule.fetchAdminInfo(),
        api.fetchUnauthenticatedApis(false),
        api.getNotTestedAPICount(false),
        api.getOnlyOnceTestedAPICount(false),
        api.getVulnerableApiCount(false),
        api.getMisConfiguredTestsCount(),
        api.fetchIssuesByApis(),
        api.fetchUrlsByIssues(false),
        api.fetchBrokenAuthenticationIssues(allSubCategories, false),
        api.fetchIssuesByApis({ categoryTypes: ["VEM", "MHH"], showIssues: false })
    ]);

    const [
        apiStatsResult,
        countMapRespResult,
        adminSettingsResult,
        unauthenticatedApisResult,
        notTestedApiCountResult,
        onlyOnceTestedApiCountResult,
        vulnerableApiCountResult,
        misConfiguredTestsCountResult,
        issuesByApisResult,
        urlsByIssuesResult,
        brokenAuthIssuesResult,
        categorizedVulnerableApisResult
    ] = results;

    const categorizedData = categorizedVulnerableApisResult.status === 'fulfilled' ? categorizedVulnerableApisResult.value : {};
    const vemVulnerableApisResult = {
        status: categorizedVulnerableApisResult.status,
        value: categorizedData?.VEM || null
    };
    const mhhVulnerableApisResult = {
        status: categorizedVulnerableApisResult.status,
        value: categorizedData?.MHH || null
    };

    const apiStats = apiStatsResult.status === 'fulfilled' ? apiStatsResult.value : null;
    const countMapResp = countMapRespResult.status === 'fulfilled' ? countMapRespResult.value : null;
    const adminSettings = adminSettingsResult.status === 'fulfilled' ? adminSettingsResult.value.resp : {};
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult.value.unauthenticatedApis || 0 : 0;
    const jiraTicketUrlMap = adminSettings?.jiraTicketUrlMap || {};
    const issuesByApis = issuesByApisResult.status === 'fulfilled' ? issuesByApisResult.value : null;
    const urlsByIssues = urlsByIssuesResult.status === 'fulfilled' ? urlsByIssuesResult.value : null;
    const brokenAuthIssuesResp = brokenAuthIssuesResult.status === 'fulfilled' ? brokenAuthIssuesResult.value : null;
    const urlsByIssuesTotalCount = urlsByIssues && typeof urlsByIssues.totalCount === 'number' ? urlsByIssues.totalCount : 0;

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
    let vemVulnerableApisCount = 0;
    let mhhVulnerableApisCount = 0;

    if (vemVulnerableApisResult.status === 'fulfilled' && vemVulnerableApisResult.value?.countByAPIs) {
        vemVulnerableApisCount = Object.keys(vemVulnerableApisResult.value.countByAPIs).length;
    }
    if (mhhVulnerableApisResult.status === 'fulfilled' && mhhVulnerableApisResult.value?.countByAPIs) {
        mhhVulnerableApisCount = Object.keys(mhhVulnerableApisResult.value.countByAPIs).length;
    }

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
        numBatches,
        allApiInfo,
        highRiskNumBatches,
        allHighRiskThirdPartyApiInfo,
        shadowNumBatches,
        allShadowApisInfo,
        notTestedNumBatches,
        allNotTestedEndpointsApiInfo,
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
    const limit = 500;
    const types = ['HIGH_RISK', 'SENSITIVE', 'THIRD_PARTY'];
    let allResults = {};
    const subCategoryMap = LocalStore.getState().subCategoryMap || {};
    const allSubCategories = Object.keys(subCategoryMap);

    for (const type of types) {
        const initialResp = await api.fetchActionItemsApiInfo(type, 0, 1);
        const totalCount = initialResp?.response?.totalCount || 0;
        const numBatches = Math.ceil(totalCount / limit);
        let batchPromises = [];
        for (let i = 0; i < numBatches; i++) {
            batchPromises.push(api.fetchActionItemsApiInfo(type, i * limit, limit));
        }
        const batchResults = await Promise.allSettled(batchPromises);
        let allApiInfos = [];
        for (const result of batchResults) {
            if (result.status === 'fulfilled' && result.value?.response?.apiInfos) {
                allApiInfos.push(...result.value.response.apiInfos);
            }
        }
        allResults[type] = {
            apiInfos: allApiInfos,
            numBatches,
            totalCount
        };
    }

    const results = await Promise.allSettled([
        api.fetchSensitiveAndUnauthenticatedValue(true),
        api.fetchHighRiskThirdPartyValue(true),
        api.fetchShadowApisValue(true),
        api.fetchUnauthenticatedApis(true),
        api.getNotTestedAPICount(true),
        api.getOnlyOnceTestedAPICount(true),
        api.getMisConfiguredTestsCount(true),
        api.getVulnerableApiCount(true),
        api.fetchActionItemsApiInfo('HIGH_RISK'),
        api.fetchActionItemsApiInfo('SENSITIVE'),
        api.fetchActionItemsApiInfo('THIRD_PARTY'),
        api.fetchBrokenAuthenticationIssues(allSubCategories, true),
        api.fetchIssuesByApis(true),
        api.fetchUrlsByIssues(true),
        api.fetchIssuesByApis({ categoryTypes: ["VEM", "MHH"], showIssues: true }),
        observeApi.getSensitiveInfoForCollections('topSensitive')
    ]);

    const [
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        unauthenticatedApisResult,
        notTestedApiInfoResult,
        onlyOnceTestedApiInfoResult,
        misConfiguredTestsApiInfoResult,
        vulnerableApiCountResult,
        highRiskResult,
        sensitiveResult,
        thirdPartyResult,
        brokenAuthIssuesApiInfoResult,
        issuesByApisResult,
        urlsByIssuesResult,
        categorizedVulnerableApisApiInfoResult,
        topSensitiveResult
    ] = results;

    const categorizedApiInfoData = categorizedVulnerableApisApiInfoResult.status === 'fulfilled' ? categorizedVulnerableApisApiInfoResult.value : {};
    const vemVulnerableApisApiInfoResult = {
        status: categorizedVulnerableApisApiInfoResult.status,
        value: categorizedApiInfoData?.VEM || null
    };
    const mhhVulnerableApisApiInfoResult = {
        status: categorizedVulnerableApisApiInfoResult.status,
        value: categorizedApiInfoData?.MHH || null
    };

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
    const vemVulnerableApisApiInfo = vemVulnerableApisApiInfoResult.status === 'fulfilled' ? vemVulnerableApisApiInfoResult.value?.issueNamesByAPIs?.map(item => item.apiInfo) || [] : [];
    const mhhVulnerableApisApiInfo = mhhVulnerableApisApiInfoResult.status === 'fulfilled' ? mhhVulnerableApisApiInfoResult.value?.issueNamesByAPIs?.map(item => item.apiInfo) || [] : [];
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