import api from "./api";
import dashboardApi from "../dashboard/api";

// Helper to safely extract promise result - exported for reuse
export const getResult = (result, defaultValue = {}) => 
    result.status === "fulfilled" ? result.value : defaultValue;

// Core API promises used by both pages
export const getCoreApiPromises = () => [
    api.getAllCollectionsBasic(),
    api.getLastTrafficSeen(),
    api.getRiskScoreInfo(),
    api.getSensitiveInfoForCollections(),
];

// Extract normalized data from core API results
export const extractCoreData = (results, startIdx = 0) => {
    const riskScoreResp = getResult(results[startIdx + 2], {});
    const sensitiveResp = getResult(results[startIdx + 3], {});
    return {
        collections: getResult(results[startIdx], { apiCollections: [] }).apiCollections || [],
        trafficInfoMap: getResult(results[startIdx + 1], {}),
        riskScoreMap: riskScoreResp.riskScoreOfCollectionsMap || {},
        criticalEndpointsCount: riskScoreResp.criticalEndpointsCount || 0,
        sensitiveInfoMap: sensitiveResp.sensitiveSubtypesInCollection || {},
        sensitiveUrlsCount: sensitiveResp.sensitiveUrlsInResponse || 0,
    };
};

// Simple fetch for Endpoints page
export const fetchCollectionsData = async () => {
    const results = await Promise.allSettled([
        ...getCoreApiPromises(),
        dashboardApi.fetchEndpointsCount(0, 0),
    ]);
    return {
        ...extractCoreData(results),
        endpointsCount: getResult(results[4], {}).newCount || 0,
    };
};
