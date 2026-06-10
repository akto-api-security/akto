import request from "@/util/request";
import func from "@/util/func";
import observeApi from "../api";
import settingRequests from "../../settings/api";
import { mapMcpAuditInfoToFlyoutData, buildMcpComponentsFromStis, buildSkillsFlyoutData, normalizeSeverity } from "./agenticPageBuilders";

function formatViolationTime(epoch) {
    if (typeof epoch !== "number" || epoch <= 0) return epoch;
    return func.formatChatTimestamp(epoch);
}

function normalizeViolationRows(violations = []) {
    return violations.map((row) => {
        const epochSec = typeof row.time === "number" && row.time > 0 ? row.time : 0;
        return {
            ...row,
            timeEpoch: epochSec,
            time: epochSec > 0 ? formatViolationTime(epochSec) : row.time,
        };
    });
}

export function aggregateViolationsByCollectionId(violationRows = []) {
    const byCollection = {};
    violationRows.forEach((row) => {
        const collectionId = row.apiCollectionId;
        if (collectionId == null) return;
        const sev = normalizeSeverity(row.severity);
        if (!sev) return;
        if (!byCollection[collectionId]) {
            byCollection[collectionId] = { critical: 0, high: 0, medium: 0, low: 0 };
        }
        byCollection[collectionId][sev.toLowerCase()] += 1;
    });
    return byCollection;
}

export function buildAgenticObserveChatMetadata(scope, data = {}) {
    return {
        type: "agentic_observe",
        data: {
            scope,
            ...data,
        },
    };
}

const agenticObserveApi = {
    async listUserAnalysis() {
        const resp = await request({
            url: "/api/listUserAnalysis",
            method: "post",
            data: {},
        });
        if (Array.isArray(resp)) return resp;
        if (Array.isArray(resp?.userAnalysisList)) return resp.userAnalysisList;
        return [];
    },

    async fetchAgenticViolations({
        deviceId,
        assetId,
        apiCollectionIds,
        startTimestamp = 0,
        endTimestamp = 0,
    } = {}) {
        const resp = await request({
            url: "/api/fetchAgenticViolations",
            method: "post",
            data: {
                deviceId,
                assetId,
                apiCollectionIds,
                startTimestamp,
                endTimestamp,
            },
        });
        return normalizeViolationRows(resp?.violations || []);
    },

    async fetchMcpFlyoutData(apiCollectionId) {
        const id = typeof apiCollectionId === "string" ? parseInt(apiCollectionId, 10) : apiCollectionId;
        const auditRows = await observeApi.fetchMcpAuditInfoByCollection(id);
        return mapMcpAuditInfoToFlyoutData(auditRows);
    },

    // Tools/resources/prompts sourced from the collection's STI endpoints (for real url+method, used
    // to fetch sample traffic) classified by the MCP audit `type` field (the authoritative source for
    // tool/resource/prompt/server — same as the legacy Audit Data page), joined with apiInfoList for risk.
    async fetchMcpComponentsData(apiCollectionId) {
        const id = typeof apiCollectionId === "string" ? parseInt(apiCollectionId, 10) : apiCollectionId;
        const [stiResp, apiResp, auditRows] = await Promise.all([
            observeApi.fetchApisFromStis(id),
            observeApi.fetchApiInfosForCollection(id),
            observeApi.fetchMcpAuditInfoByCollection(id),
        ]);
        const stiEndpoints = (stiResp?.list || []).map((x) => ({
            method: x?._id?.method,
            url: x?._id?.url,
        }));
        return buildMcpComponentsFromStis(stiEndpoints, apiResp?.apiInfoList || [], id, auditRows || []);
    },

    async fetchSkillsFlyoutData(apiCollectionId, collection = null) {
        const id = typeof apiCollectionId === "string" ? parseInt(apiCollectionId, 10) : apiCollectionId;
        let coll = collection;
        const [apiResp, stiResp] = await Promise.all([
            observeApi.fetchApiInfosForCollection(id),
            observeApi.fetchApisFromStis(id),
        ]);
        if (!coll) {
            const resp = await observeApi.getAllCollectionsBasic();
            coll = (resp?.apiCollections || []).find((c) => c.id === id);
        }
        const stiEndpoints = (stiResp?.list || []).map((x) => ({ method: x?._id?.method, url: x?._id?.url }));
        return buildSkillsFlyoutData(coll, apiResp?.apiInfoList || [], stiEndpoints, id);
    },

    async fetchEndpointShieldModules() {
        const resp = await settingRequests.fetchModuleInfo({ moduleType: "MCP_ENDPOINT_SHIELD" });
        return resp?.moduleInfos || [];
    },
};

export default agenticObserveApi;
