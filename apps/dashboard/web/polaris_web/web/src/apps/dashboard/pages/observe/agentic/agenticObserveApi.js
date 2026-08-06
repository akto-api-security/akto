import request from "@/util/request";
import observeApi from "../api";
import { buildMcpComponentsFromStis, buildAgentBuiltinToolsFromStis, buildSkillsFlyoutData, normalizeSeverity } from "./agenticPageBuilders";
import { deviceServiceKey } from "./constants";

export { deviceServiceKey };

function extractRuleViolated(metadata) {
    if (!metadata) return "-";
    try {
        const obj = JSON.parse(metadata);
        return obj.rule_violated || obj.ruleViolated || "-";
    } catch {
        return "-";
    }
}

function normalizeEvent(e) {
    return {
        host:         e.host || "",
        url:          e.url || "",
        severity:     e.severity?.toLowerCase() || "medium",
        title:        e.filterId || "Policy violation",
        timeEpoch:    e.timestamp || 0,
        time:         e.timestamp || 0,
        refId:        e.refId || "",
        eventType:    e.eventType || "",
        actor:        e.actor || "",
        filterId:     e.filterId || "",
        status:       e.status || "",
        sessionId:    e.sessionId || "",
        ruleViolated: extractRuleViolated(e.metadata),
        method:       e.method || "",
    };
}

export async function fetchAgenticViolations({ startTimestamp, endTimestamp, hosts = [], limit = 100000 } = {}) {
    const resp = await observeApi.fetchSuspectSampleData({ startTimestamp, endTimestamp, hosts, limit });
    return (resp?.maliciousEvents || []).map(normalizeEvent);
}

// Per-host severity counts for the whole date range, server-aggregated — returns a
// {[host]: {critical, high, medium, low}} map instead of every raw event doc. Use this wherever only
// COUNTS are needed (list-page violation columns, severity totals); fetchAgenticViolations is still
// needed for actual per-event detail (flyout tables, timestamps for trend sparklines).
export async function fetchAgenticViolationCountsByHost({ startTimestamp, endTimestamp } = {}) {
    // Unlike fetchAgenticViolations (always called outside the blocking Promise.all, with its own
    // .catch() at the call site), this is called INSIDE the blocking Promise.all on every agentic list
    // page's mount — so a failure here must not propagate, or it takes down collections/risk/traffic/
    // everything else in the same batch via Promise.all's all-or-nothing semantics. Degrade to "no
    // violation counts yet" instead.
    let rows;
    try {
        rows = await observeApi.fetchHostSeverityCounts(startTimestamp, endTimestamp);
    } catch (e) {
        // eslint-disable-next-line no-console
        console.error("fetchAgenticViolationCountsByHost failed:", e);
        rows = [];
    }
    const byHost = {};
    (rows || []).forEach((r) => {
        if (!r?.host) return;
        byHost[r.host] = {
            critical: r.critical || 0,
            high: r.high || 0,
            medium: r.medium || 0,
            low: r.low || 0,
        };
    });
    return byHost;
}

// LatestAPICollectionID in events is set to time.Now().Unix() (a fake timestamp),
// so apiCollectionId cannot be used to match events to collections.
// The real join key is the host: agentic traffic events and their collections are both
// built by ingest.go as `deviceLabel[.clientType].projectName`, so event.host === collection.hostName.
//
// Attribution uses three levels in order:
// 1. Exact:         event.host === collection.hostName
// 2. Loose:         device+service (first + last segment) — covers 2-segment events whose
//                   collection is 3-segment (`device.source.service` vs `device.service`)
// 3. Claude-config: host is 2-segment ending in `.claude-settings` or `.claude` — these are
//                   config scanner events with no collection. Attributed to any claude collection
//                   on the same device. Old ingest = `claude-settings`, new ingest = `claude`.

// Returns true for 2-segment claude config event hosts (no matching collection exists for these).
export function isClaudeConfigHost(hostName) {
    if (!hostName) return false;
    const parts = hostName.split(".");
    if (parts.length !== 2) return false;
    const service = parts[1].toLowerCase();
    return service === "claude-settings" || service === "claude";
}

// Builds the host → [collectionId] attribution maps shared by both aggregateViolationsByCollectionId
// (raw rows) and aggregateViolationCountsByCollectionId (pre-aggregated host counts), so the exact/loose/
// claude-config matching rules stay in one place.
function buildHostAttributionMaps(collections = []) {
    const hostToIds        = {};  // exact hostName → [collectionId]
    const looseToIds       = {};  // device+service key → [collectionId]
    const claudeDeviceToIds = {}; // deviceId → [collectionId] for collections whose last segment is "claude"
    const allClaudeIds = [];      // all claude collection IDs across all devices (fallback for untracked devices)

    collections.forEach((c) => {
        if (!c.hostName) return;

        if (!hostToIds[c.hostName]) hostToIds[c.hostName] = [];
        hostToIds[c.hostName].push(c.id);

        const lk = deviceServiceKey(c.hostName);
        if (lk) {
            if (!looseToIds[lk]) looseToIds[lk] = [];
            looseToIds[lk].push(c.id);
        }

        const parts = c.hostName.split(".");
        const deviceId = parts[0];
        const service  = parts[parts.length - 1]?.toLowerCase();
        if (deviceId && service === "claude") {
            if (!claudeDeviceToIds[deviceId]) claudeDeviceToIds[deviceId] = [];
            claudeDeviceToIds[deviceId].push(c.id);
            allClaudeIds.push(c.id);
        }
    });

    return { hostToIds, looseToIds, claudeDeviceToIds, allClaudeIds };
}

// Resolves a single event/count host to the collection IDs it should be attributed to, using the same
// exact → loose → claude-config fallback tiers for both raw-row and pre-aggregated-count callers.
function resolveHostToCollectionIds(host, { hostToIds, looseToIds, claudeDeviceToIds, allClaudeIds }) {
    let ids = hostToIds[host];
    if (!ids?.length) ids = looseToIds[deviceServiceKey(host)];
    if (!ids?.length && isClaudeConfigHost(host)) {
        const deviceId = host.split(".")[0];
        // Attribute to ONE canonical collection (first found) so the same event isn't counted
        // multiple times when there are several claude collections in the tenant.
        const pool = claudeDeviceToIds[deviceId]?.length ? claudeDeviceToIds[deviceId] : allClaudeIds;
        ids = pool.length ? [pool[0]] : undefined;
    }
    return ids;
}

export function aggregateViolationsByCollectionId(violationRows = [], collections = []) {
    const maps = buildHostAttributionMaps(collections);

    const byCollection = {};
    violationRows.forEach((row) => {
        const ids = resolveHostToCollectionIds(row.host, maps);
        if (!ids?.length) return;

        const sev = normalizeSeverity(row.severity);
        if (!sev) return;
        const key = sev.toLowerCase();
        ids.forEach((id) => {
            if (!byCollection[id]) byCollection[id] = { critical: 0, high: 0, medium: 0, low: 0 };
            byCollection[id][key] += 1;
        });
    });
    return byCollection;
}

// Same host→collection attribution as aggregateViolationsByCollectionId, but sums server-pre-aggregated
// per-host severity counts (from fetchAgenticViolationCountsByHost) instead of incrementing per raw row —
// avoids fetching every raw violation event just to compute this rollup.
export function aggregateViolationCountsByCollectionId(hostCounts = {}, collections = []) {
    const maps = buildHostAttributionMaps(collections);

    const byCollection = {};
    Object.entries(hostCounts).forEach(([host, counts]) => {
        const ids = resolveHostToCollectionIds(host, maps);
        if (!ids?.length) return;

        ids.forEach((id) => {
            if (!byCollection[id]) byCollection[id] = { critical: 0, high: 0, medium: 0, low: 0 };
            byCollection[id].critical += counts.critical || 0;
            byCollection[id].high     += counts.high || 0;
            byCollection[id].medium   += counts.medium || 0;
            byCollection[id].low      += counts.low || 0;
        });
    });
    return byCollection;
}

// Open the guardrail activity page deep-linked to a single violation event.
// Mirrors the URL shape the page expects: event keys as query params, #active hash.
export function openViolationInThreatActivity(row = {}) {
    const base = "/dashboard/guardrails/activity";
    const { refId, eventType, actor, filterId, status } = row;
    if (refId && eventType && actor && filterId) {
        const params = new URLSearchParams();
        params.set("refId", refId);
        params.set("eventType", eventType);
        params.set("actor", actor);
        params.set("filterId", filterId);
        params.set("eventStatus", (status || "ACTIVE").toUpperCase());
        const { severity, url, method, ruleViolated } = row;
        if (severity) params.set("severity", String(severity).toUpperCase());
        if (url) params.set("url", url);
        if (method) params.set("method", method);
        if (ruleViolated && ruleViolated !== "-") params.set("ruleViolated", ruleViolated);
        window.open(`${base}?${params.toString()}#active`, "_blank");
    } else {
        window.open(`${base}#active`, "_blank");
    }
}

// ── Config violation scoping (Claude, Codex, Copilot) ────────────────────────

const CONFIG_URL_PATTERN = /^\/[a-zA-Z0-9_-]+\/config\//;

export function isConfigViolationRow(row) {
    return !!row?.url && CONFIG_URL_PATTERN.test(row.url);
}

function assetHostSets(asset, collections = []) {
    const ids = new Set((asset?.collectionIds || []).map(Number));
    const hosts = new Set();
    const looseKeys = new Set();
    collections.forEach((c) => {
        if (!ids.has(Number(c.id)) || !c.hostName) return;
        hosts.add(c.hostName);
        const lk = deviceServiceKey(c.hostName);
        if (lk) looseKeys.add(lk);
    });
    return { hosts, looseKeys };
}

// Config violation rows (url contains /config/) attributed to this asset's hosts.
export function selectConfigViolationRows(violationRows = [], asset, collections = []) {
    const { hosts, looseKeys } = assetHostSets(asset, collections);
    if (!hosts.size && !looseKeys.size) return [];
    return violationRows.filter((r) =>
        isConfigViolationRow(r) &&
        r.host &&
        (hosts.has(r.host) || looseKeys.has(deviceServiceKey(r.host))),
    );
}

export function summarizeViolations(rows = []) {
    const t = { critical: 0, high: 0, medium: 0, low: 0 };
    rows.forEach((r) => {
        const sev = (r.severity || "").toLowerCase();
        if (t[sev] != null) t[sev] += 1;
    });
    t.total = t.critical + t.high + t.medium + t.low;
    return t;
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

    async fetchCollectionStiBundle(apiCollectionId) {
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
        return {
            id,
            stiEndpoints,
            apiInfoList: apiResp?.apiInfoList || [],
            auditRows: auditRows || [],
        };
    },

    // Tools/resources/prompts sourced from the collection's STI endpoints (for real url+method, used
    // to fetch sample traffic) classified by the MCP audit `type` field (the authoritative source for
    // tool/resource/prompt/server — same as the legacy Audit Data page), joined with apiInfoList for risk.
    async fetchMcpComponentsData(apiCollectionId) {
        const { id, stiEndpoints, apiInfoList, auditRows } = await this.fetchCollectionStiBundle(apiCollectionId);
        return buildMcpComponentsFromStis(stiEndpoints, apiInfoList, id, auditRows);
    },

    // Built-in tools on the agent host (/tool/*) — Claude Cowork, Claude CLI, etc.
    async fetchAgentBuiltinToolsData(apiCollectionId) {
        const { id, stiEndpoints, apiInfoList, auditRows } = await this.fetchCollectionStiBundle(apiCollectionId);
        return buildAgentBuiltinToolsFromStis(stiEndpoints, apiInfoList, id, auditRows);
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

};

export default agenticObserveApi;
