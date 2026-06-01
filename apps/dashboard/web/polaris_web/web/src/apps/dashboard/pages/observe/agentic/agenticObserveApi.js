import request from "@/util/request";
import func from "@/util/func";
import observeApi from "../api";
import settingRequests from "../../settings/api";
import { mapMcpAuditInfoToFlyoutData, buildSkillsFlyoutData } from "./agenticPageBuilders";

function normalizeViolationRows(violations = []) {
    return violations.map((row) => ({
        ...row,
        time: typeof row.time === "number" && row.time > 0 ? func.prettifyEpoch(row.time) : row.time,
    }));
}

export function aggregateViolationsByCollectionId(violationRows = []) {
    const byCollection = {};
    violationRows.forEach((row) => {
        const collectionId = row.apiCollectionId;
        if (collectionId == null) return;
        if (!byCollection[collectionId]) {
            byCollection[collectionId] = { critical: 0, high: 0, medium: 0, low: 0 };
        }
        const sev = (row.severity || "").toLowerCase();
        if (sev.includes("crit")) byCollection[collectionId].critical += 1;
        else if (sev.includes("high")) byCollection[collectionId].high += 1;
        else if (sev.includes("med")) byCollection[collectionId].medium += 1;
        else if (sev.includes("low")) byCollection[collectionId].low += 1;
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

    async fetchSkillsFlyoutData(apiCollectionId, collection = null) {
        const id = typeof apiCollectionId === "string" ? parseInt(apiCollectionId, 10) : apiCollectionId;
        let coll = collection;
        if (!coll) {
            const resp = await observeApi.getAllCollectionsBasic();
            coll = (resp?.apiCollections || []).find((c) => c.id === id);
        }
        const apiResp = await observeApi.fetchApiInfosForCollection(id);
        return buildSkillsFlyoutData(coll, apiResp?.apiInfoList || []);
    },

    async fetchEndpointShieldModules() {
        const resp = await settingRequests.fetchModuleInfo({ moduleType: "MCP_ENDPOINT_SHIELD" });
        return resp?.moduleInfos || [];
    },
};

export default agenticObserveApi;
