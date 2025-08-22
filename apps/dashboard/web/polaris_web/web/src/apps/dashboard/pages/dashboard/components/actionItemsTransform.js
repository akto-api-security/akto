import api from '../api';
import observeApi from '../../observe/api';
import settingsModule from '../../settings/module';
import func from '../../../../../util/func';

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
    // Run all batches in parallel
    const batchResults = await Promise.allSettled(batchPromises);
    // Combine results as needed (example: sum counts, merge arrays, etc.)
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

    // Fetch total count for batching (High Risk Third Party)
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

    // Fetch total count for batching (Shadow APIs)
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

    // Fetch total count for batching (Not Tested APIs)
    const notTestedInitialResp = await api.getNotTestedAPICount(false, 0, 1);
    const notTestedTotalCount = notTestedInitialResp?.notTestedEndpointsCount || 0;
    const notTestedNumBatches = Math.ceil(notTestedTotalCount / limit);
    let notTestedBatchPromises = [];
    for (let i = 0; i < notTestedNumBatches; i++) {
        notTestedBatchPromises.push(api.getNotTestedAPICount(false, i * limit, limit));
    }
    const notTestedBatchResults = await Promise.allSettled(notTestedBatchPromises);
    let notTestedApiCount = 0;
    let allNotTestedEndpointsApiInfo = [];
    for (const result of notTestedBatchResults) {
        if (result.status === 'fulfilled') {
            notTestedApiCount += result.value?.notTestedEndpointsCount || 0;
            if (result.value?.notTestedEndpointsApiInfo) {
                allNotTestedEndpointsApiInfo.push(...result.value.notTestedEndpointsApiInfo);
            }
        }
    }

    const results = await Promise.allSettled([
        api.fetchApiStats(startTimestamp, endTimestamp),
        observeApi.fetchCountMapOfApis(),
        // Remove old single call, use new count from batches
        settingsModule.fetchAdminInfo(),
        api.fetchUnauthenticatedApis(false),
        api.getNotTestedAPICount(false), 
        api.getOnlyOnceTestedAPICount(false), 
        api.getVulnerableApiCount(false), 
        api.getMisConfiguredTestsCount() 
    ]);

    const [
        apiStatsResult,
        countMapRespResult,
        // highRiskThirdPartyValueResult, // removed
        // shadowApisValueResult, // removed
        adminSettingsResult,
        unauthenticatedApisResult,
        notTestedApiCountResult, 
        onlyOnceTestedApiCountResult, 
        vulnerableApiCountResult, 
        misConfiguredTestsCountResult 
    ] = results;

    const apiStats = apiStatsResult.status === 'fulfilled' ? apiStatsResult.value : null;
    const countMapResp = countMapRespResult.status === 'fulfilled' ? countMapRespResult.value : null;
    // const highRiskThirdPartyCount = highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult.value.highRiskThirdPartyEndpointsCount || 0 : 0;
    // const shadowApisCount = shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult.value.shadowApisCount || 0 : 0;
    const adminSettings = adminSettingsResult.status === 'fulfilled' ? adminSettingsResult.value.resp : {};
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult.value.unauthenticatedApis || 0 : 0;
    const jiraTicketUrlMap = adminSettings?.jiraTicketUrlMap || {};

    let highRiskCount = 0;
    let unauthenticatedCount = unauthenticatedApis;
    let thirdPartyDiff = 0;
    let sensitiveDataCount = countMapResp?.totalApisCount || 0;
    let notTestedApiCount = notTestedApiCountResult.status === 'fulfilled' ? notTestedApiCountResult.value?.notTestedEndpointsCount || 0 : 0;
    let onlyOnceTestedApiCount = onlyOnceTestedApiCountResult.status === 'fulfilled' ? onlyOnceTestedApiCountResult.value?.onlyOnceTestedEndpointsCount || 0 : 0;
    let vulnerableApiCount = vulnerableApiCountResult.status === 'fulfilled' ? vulnerableApiCountResult.value?.buaCategoryCount || 0 : 0;
    let misConfiguredTestsCount = misConfiguredTestsCountResult.status === 'fulfilled' ? misConfiguredTestsCountResult.value?.misConfiguredTestsCount || 0 : 0;

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
        highRiskThirdPartyCount, // from batches
        shadowApisCount,
        sensitiveAndUnauthenticatedCount, // from batches
        jiraTicketUrlMap,
        notTestedApiCount,
        onlyOnceTestedApiCount,
        vulnerableApiCount,
        misConfiguredTestsCount,
        numBatches, // pass batch count if needed
        allApiInfo, // pass combined API info if needed
        highRiskNumBatches,
        allHighRiskThirdPartyApiInfo,
        shadowNumBatches,
        allShadowApisInfo,
        notTestedNumBatches,
        allNotTestedEndpointsApiInfo
    };
}


export async function fetchAllActionItemsApiInfo() {
    const limit = 500;
    const types = ['HIGH_RISK', 'SENSITIVE', 'THIRD_PARTY'];
    let allResults = {};

    for (const type of types) {
        // Initial call to get total count for this type
        const initialResp = await api.fetchActionItemsApiInfo(type, 0, 1);
        const totalCount = initialResp?.response?.totalCount || 0;
        const numBatches = Math.ceil(totalCount / limit);
        let batchPromises = [];
        for (let i = 0; i < numBatches; i++) {
            batchPromises.push(api.fetchActionItemsApiInfo(type, i * limit, limit));
        }
        // Run all batches in parallel
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

    // Fetch other data in parallel
    const results = await Promise.allSettled([
        api.fetchSensitiveAndUnauthenticatedValue(true),
        api.fetchHighRiskThirdPartyValue(true),
        api.fetchShadowApisValue(true),
        api.fetchUnauthenticatedApis(true),
        api.getNotTestedAPICount(true),
        api.getOnlyOnceTestedAPICount(true), 
        api.getMisConfiguredTestsCount(true), 
        api.getVulnerableApiCount(true) 
    ]);

    const [
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        unauthenticatedApisResult,
        notTestedApiInfoResult,
        onlyOnceTestedApiInfoResult, 
        misConfiguredTestsApiInfoResult, 
        vulnerableApiCountResult 
    ] = results;

    return {
        highRiskApis: allResults['HIGH_RISK'].apiInfos,
        sensitiveApis: allResults['SENSITIVE'].apiInfos,
        thirdPartyApis: allResults['THIRD_PARTY'].apiInfos,
        highRiskNumBatches: allResults['HIGH_RISK'].numBatches,
        sensitiveNumBatches: allResults['SENSITIVE'].numBatches,
        thirdPartyNumBatches: allResults['THIRD_PARTY'].numBatches,
        highRiskTotalCount: allResults['HIGH_RISK'].totalCount,
        sensitiveTotalCount: allResults['SENSITIVE'].totalCount,
        thirdPartyTotalCount: allResults['THIRD_PARTY'].totalCount,
        sensitiveAndUnauthenticated: sensitiveAndUnauthenticatedValueResult.status === 'fulfilled' ? sensitiveAndUnauthenticatedValueResult?.value?.sensitiveUnauthenticatedEndpointsApiInfo || [] : [],
        highRiskThirdParty: highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult?.value?.highRiskThirdPartyEndpointsApiInfo || [] : [],
        shadowApis: shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult?.value?.shadowApisCount || [] : [],
        unauthenticatedApis: unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult?.value?.unauthenticatedApiList || [] : [],
        notTestedEndpointsApiInfo: notTestedApiInfoResult.status === 'fulfilled' ? notTestedApiInfoResult.value?.notTestedEndpointsApiInfo || [] : [],
        onlyOnceTestedEndpointsApiInfo: onlyOnceTestedApiInfoResult.status === 'fulfilled' ? onlyOnceTestedApiInfoResult.value?.onlyOnceTestedEndpointsApiInfo || [] : [],
        misConfiguredTestsApiInfo: misConfiguredTestsApiInfoResult.status === 'fulfilled' ? misConfiguredTestsApiInfoResult.value?.misConfiguredTestsApiInfo || [] : [],
        vulnerableApiCountApiInfo: vulnerableApiCountResult.status === 'fulfilled' ? vulnerableApiCountResult.value?.buaCategoryApiInfo || [] : []
    };
}
