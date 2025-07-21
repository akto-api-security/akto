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
        api.fetchSensitiveAndUnauthenticatedValue(),
        api.fetchHighRiskThirdPartyValue(),
        api.fetchShadowApisValue(),
        settingsModule.fetchAdminInfo()
    ]);

    const [
        apiStatsResult,
        countMapRespResult,
        sensitiveAndUnauthenticatedValueResult,
        highRiskThirdPartyValueResult,
        shadowApisValueResult,
        adminSettingsResult
    ] = results;

    const apiStats = apiStatsResult.status === 'fulfilled' ? apiStatsResult.value : null;
    const countMapResp = countMapRespResult.status === 'fulfilled' ? countMapRespResult.value : null;
    const SensitiveAndUnauthenticatedValue = sensitiveAndUnauthenticatedValueResult.status === 'fulfilled' ? sensitiveAndUnauthenticatedValueResult.value : 0;
    const highRiskThirdPartyValue = highRiskThirdPartyValueResult.status === 'fulfilled' ? highRiskThirdPartyValueResult.value : 0;
    const shadowApisValue = shadowApisValueResult.status === 'fulfilled' ? shadowApisValueResult.value : 0;
    const adminSettings = adminSettingsResult.status === 'fulfilled' ? adminSettingsResult.value.resp : {};

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
        unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;
        thirdPartyDiff = (apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0) - (apiStatsStart.accessTypeMap?.THIRD_PARTY || 0);
    }

    return {
        highRiskCount,
        sensitiveDataCount,
        unauthenticatedCount,
        thirdPartyDiff,
        highRiskThirdPartyValue,
        shadowApisValue,
        SensitiveAndUnauthenticatedValue,
        jiraTicketUrlMap
    };
}