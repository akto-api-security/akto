import api from '../apps/dashboard/pages/dashboard/api';
import func from './func';
import observeApi from '../apps/dashboard/pages/observe/api';

export async function fetchAllData(setJiraTicketUrlMap, setFetchedData, setActionItems) {
    const endTimestamp = func.timeNow();
    const startTimestamp = func.timeNow() - 3600 * 24 * 7;
    try {
        const [
            apiStats,
            countMapResp,
            SensitiveAndUnauthenticatedValue,
            highRiskThirdPartyValue,
            shadowApisValue,
            AdminSettings
        ] = await Promise.all([
            api.fetchApiStats(startTimestamp, endTimestamp),
            observeApi.fetchCountMapOfApis(),
            api.fetchSensitiveAndUnauthenticatedValue(),
            api.fetchHighRiskThirdPartyValue(),
            api.fetchShadowApisValue(),
            api.fetchAdminSettings()
        ]);

        setJiraTicketUrlMap(AdminSettings?.accountSettings?.jiraTicketUrlMap || {});
        setFetchedData({ apiStats, countMapResp, SensitiveAndUnauthenticatedValue, highRiskThirdPartyValue, shadowApisValue });
    } catch (error) {
        console.error("Error fetching action items data:", error);
        setActionItems([]);
    }
} 