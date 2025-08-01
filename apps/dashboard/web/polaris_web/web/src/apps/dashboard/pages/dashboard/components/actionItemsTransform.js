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
        api.fetchSensitiveAndUnauthenticatedValue(true),
        api.fetchHighRiskThirdPartyValue(true),
        api.fetchShadowApisValue(true),
        settingsModule.fetchAdminInfo(),
        api.fetchApiInfosWithCustomFilter('AUTH_TYPES', 0, 0, '')
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
    const unauthenticatedApis = unauthenticatedApisResult.status === 'fulfilled' ? unauthenticatedApisResult.value : { apiInfos: [] };

    const jiraTicketUrlMap = adminSettings?.jiraTicketUrlMap || {};

    let highRiskCount = 0;
    let unauthenticatedCount = 0;
    let thirdPartyDiff = 0;
    let sensitiveDataCount = countMapResp?.totalApisCount || 0;

    if (apiStats?.apiStatsEnd && apiStats?.apiStatsStart) {
        const { apiStatsEnd, apiStatsStart } = apiStats;

        highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
            .filter(([score]) => parseInt(score) > 3)
            .reduce((total, [, count]) => total + count, 0);

        unauthenticatedCount = Array.isArray(unauthenticatedApis.apiInfos)
            ? unauthenticatedApis.apiInfos.length
            : 0;

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
    const highRiskApis = await api.fetchApiInfosWithCustomFilter('RISK_SCORE', 3, 0, 'riskScore');
    const sensitiveDataEndpoints = await api.fetchApiInfosWithCustomFilter('SENSITIVE', 0, 0, '');
    const unauthenticatedApis = await api.fetchApiInfosWithCustomFilter('AUTH_TYPES', 0, 0, '');
    const thirdPartyApis = await api.fetchApiInfosWithCustomFilter('THIRD_PARTY', 0, 0, 'discoveredTimestamp');
    const highRiskThirdParty = await api.fetchHighRiskThirdPartyValue(true);
    const shadowApis = await api.fetchShadowApisValue(true);
    const sensitiveAndUnauthenticated = await api.fetchSensitiveAndUnauthenticatedValue(true);

    return {
        highRiskApis: highRiskApis || [],
        sensitiveDataEndpoints: sensitiveDataEndpoints || [],
        unauthenticatedApis: unauthenticatedApis || [],
        thirdPartyApis: thirdPartyApis || [],
        highRiskThirdParty: highRiskThirdParty?.highRiskThirdPartyEndpointsApiInfo || [],
        shadowApis: shadowApis?.shadowApiInfos || [],
        sensitiveAndUnauthenticated: sensitiveAndUnauthenticated?.sensitiveUnauthenticatedEndpointsApiInfo || [],
    };
}