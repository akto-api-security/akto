import api from '../api';
import observeApi from '../../observe/api';
import settingsModule from '../../settings/module';
import func from '../../../../../util/func';

export async function fetchActionItemsData() {
    const endTimestamp = func.timeNow();
    const startTimestamp = endTimestamp - 3600 * 24 * 7;

    const results = await Promise.allSettled([
        api.fetchApiStats(startTimestamp, endTimestamp),
        observeApi.fetchCountMapOfApis(),
        api.fetchSensitiveAndUnauthenticatedValue(false),
        api.fetchHighRiskThirdPartyValue(false),
        api.fetchShadowApisValue(false),
        settingsModule.fetchAdminInfo(),
        api.fetchUnauthenticatedApis(false)
    ]);

    const [
        apiStatsResult,
        countMapRespResult,
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        adminSettingsResult,
        unauthenticatedApisResult 
    ] = results;

    const apiStats = apiStatsResult.status === 'fulfilled' ? apiStatsResult.value : null;
    const countMapResp = countMapRespResult.status === 'fulfilled' ? countMapRespResult.value : null;
    const sensitiveAndUnauthenticatedCount = sensitiveAndUnauthenticatedValueResult.status === 'fulfilled' ? sensitiveAndUnauthenticatedValueResult.value.sensitiveUnauthenticatedEndpointsCount || 0 : 0;
    const highRiskThirdPartyCount = highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult.value.highRiskThirdPartyEndpointsCount || 0 : 0;
    const shadowApisCount = shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult.value.shadowApisCount || 0 : 0;
    const adminSettings = adminSettingsResult.status === 'fulfilled' ? adminSettingsResult.value.resp : {};
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult.value.unauthenticatedApis || 0 : 0;

    const jiraTicketUrlMap = adminSettings?.jiraTicketUrlMap || {};

    let highRiskCount = 0;
    let unauthenticatedCount = unauthenticatedApis;
    let thirdPartyDiff = 0;
    let sensitiveDataCount = countMapResp?.totalApisCount || 0;

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
        jiraTicketUrlMap
    };
}


export async function fetchAllActionItemsApiInfo() {

    const results = await Promise.allSettled([
        api.fetchSensitiveAndUnauthenticatedValue(true),
        api.fetchHighRiskThirdPartyValue(true),
        api.fetchShadowApisValue(true),
        api.fetchUnauthenticatedApis(true),
        api.fetchActionItemsApiInfo('HIGH_RISK'),
        api.fetchActionItemsApiInfo('SENSITIVE'),
        api.fetchActionItemsApiInfo('THIRD_PARTY')
    ]);

    const [
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        unauthenticatedApisResult,
        highRiskResult,
        sensitiveResult,
        thirdPartyResult
    ] = results;

    const sensitiveAndUnauthenticatedApis = sensitiveAndUnauthenticatedValueResult.status === 'fulfilled' ? sensitiveAndUnauthenticatedValueResult?.value?.sensitiveUnauthenticatedEndpointsApiInfo || [] : [];
    const highRiskThirdPartyApis = highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult?.value?.highRiskThirdPartyEndpointsApiInfo || [] : [];
    const shadowApis = shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult?.value?.shadowApisCount || [] : [];
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult?.value?.unauthenticatedApiList || [] : [];
    const highRiskApis = highRiskResult.status === 'fulfilled' ? highRiskResult?.value?.response?.apiInfos || [] : [];
    const sensitiveDataEndpoints = sensitiveResult.status === 'fulfilled' ? sensitiveResult?.value?.response?.apiInfos || [] : [];
    const thirdPartyApis = thirdPartyResult.status === 'fulfilled' ? thirdPartyResult?.value?.response?.apiInfos || [] : [];

    return {
        highRiskApis: highRiskApis,
        sensitiveDataEndpoints: sensitiveDataEndpoints,
        unauthenticatedApis: unauthenticatedApis,
        thirdPartyApis: thirdPartyApis,
        highRiskThirdParty: highRiskThirdPartyApis,
        shadowApis: shadowApis,
        sensitiveAndUnauthenticated: sensitiveAndUnauthenticatedApis,
    };
}