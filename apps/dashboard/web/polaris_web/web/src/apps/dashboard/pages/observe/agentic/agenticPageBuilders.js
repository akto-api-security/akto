import {
    extractEndpointId,
    extractServiceName,
} from "./constants";
import {
    formatDisplayName,
    getFriendlyLlmName,
    getTypeFromTags,
    hasPersonalAccountTag,
    hasMisconfiguredConfigTag,
} from "./mcpClientHelper";
import func from "@/util/func";
import { getResolvedUsernameForCollection, DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";

// ─── Severity / risk helpers ──────────────────────────────────────────────────

// Normalise any incoming severity to one of the canonical Akto severities
// (func.getAktoSeverities() → CRITICAL/HIGH/MEDIUM/LOW), same convention as testruns.
// Callers lowercase it where a renderer needs the lowercase key.
export function normalizeSeverity(raw) {
    if (raw == null) return null;
    const upper = String(raw).trim().toUpperCase();
    return func.getAktoSeverities().find((sev) => upper.startsWith(sev.slice(0, 3))) || null;
}

// Risk score → Akto severity bucket (drives the .badge-wrapper-<SEVERITY> colour on RiskPill).
export function getRiskLevel(score) {
    if (score >= 4.5) return "CRITICAL";
    if (score >= 4.0) return "HIGH";
    if (score >= 3.5) return "MEDIUM";
    return "LOW";
}

// Polaris Badge status for a risk score — matches the old Endpoints layout.
export function getRiskStatus(score) {
    if (score >= 4.5) return "critical";
    if (score >= 4) return "attention";
    if (score >= 2.5) return "warning";
    if (score > 0) return "info";
    return undefined;
}

function deviceServiceKey(hostName) {
    if (!hostName) return null;
    const parts = hostName.split(".");
    if (parts.length < 2) return null;
    return parts[0] + "\0" + parts[parts.length - 1];
}

// Human label derived from the bucket — no duplicated thresholds.
export function getRiskLabel(score) {
    return `${func.toSentenceCase(getRiskLevel(score))} Risk`;
}

export function toChildPathKey(serviceName) {
    if (!serviceName) return "unknown";
    return String(serviceName).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

// Components linked to an agentic asset — prefers the two-level tree rows for the asset,
// falling back to its declared mcpServers. Used by the asset flyout's topology + tab counts.
export function getAgentLinkedComponents(asset, agenticTreeData = [], agenticFlatData = []) {
    const fromTree = agenticTreeData.filter((r) => r.path?.length === 2 && r.path[0] === asset.id);
    if (fromTree.length) return fromTree;

    const seen = new Set();
    const linked = [];
    (asset.mcpServers || []).forEach((name) => {
        const key = String(name).toLowerCase();
        if (seen.has(key)) return;
        seen.add(key);
        const flat = agenticFlatData.find((a) => a.name === name || a.id === name);
        linked.push({ name: flat?.name || name, type: flat?.type || "MCP Server" });
    });
    return linked;
}

// LLM traffic on the agent host (/v1/messages) — Cowork, Claude CLI, etc.
export function isAgentLlmMessagesUrl(url) {
    const u = String(url || "");
    return u === "/v1/messages" || u.startsWith("/v1/messages/");
}

// Observed tools + inline LLM on the agent collection for topology graphs.
export function buildAgentInlineTopologyComponents(stiEndpoints = [], builtinTools = [], asset = {}) {
    const items = [];
    const hasLlm = (stiEndpoints || []).some((ep) => {
        const url = ep?.url || ep?._id?.url;
        return isAgentLlmMessagesUrl(url);
    });
    if (hasLlm) {
        const tag = asset?.assetTagValue || asset?.name || "claude";
        const label = tag.toLowerCase().includes("claude")
            ? getFriendlyLlmName("claude.ai")
            : formatDisplayName(tag);
        items.push({ id: "inline-llm", cat: "ai-model", type: "LLM", label, edgeColor: "#ec4899" });
    }
    const seenTools = new Set();
    (builtinTools || []).forEach((tool, i) => {
        const name = tool?.name;
        if (!name || seenTools.has(name)) return;
        seenTools.add(name);
        items.push({ id: `inline-tool-${i}`, cat: "mcp", type: "Tool", label: name, edgeColor: "#4cbebb" });
    });
    return items;
}

export function inferOsFromDeviceId(deviceId) {
    if (!deviceId) return null;
    const upper = String(deviceId).toUpperCase();
    if (upper.includes("WIN")) return "windows";
    if (upper.includes("LIN") || upper.includes("LINUX")) return "linux";
    if (upper.includes("MAC")) return "mac";
    return null;
}

// Collections reaching this UI are already scoped to agentic context-source, so we only
// guard against deactivated ones and confirm the agentic <endpoint>.<source>.<service>
// hostname shape — no env-tag re-check needed.
export function isAgenticCollection(collection) {
    if (!collection || collection.deactivated) return false;
    const hostName = collection.hostName;
    return !!(hostName && hostName.split(".").length >= 3);
}

function emptyViolations() {
    return { critical: 0, high: 0, medium: 0, low: 0 };
}

function mergeViolations(target, source) {
    if (!source) return;
    Object.entries(source).forEach(([k, v]) => {
        const sev = normalizeSeverity(k);
        if (sev && v) {
            const key = sev.toLowerCase();
            target[key] = (target[key] || 0) + Number(v);
        }
    });
}

function hasAnyViolation(v) {
    return v && Object.values(v).some((c) => c > 0);
}

function mapRiskLevel(row) {
    const analysis = row?.componentRiskAnalysis;
    if (analysis?.isComponentMalicious) return "critical";
    if (analysis?.hasPrivilegedAccess) return "high";
    if (row?.remarks === "Rejected") return "high";
    return "medium";
}

export function mapMcpAuditInfoToFlyoutData(auditRows = []) {
    const tools = [];
    const resources = [];
    const prompts = [];
    const toolViolations = {};
    let id = 0;

    auditRows.forEach((row) => {
        if (!row?.type || !row?.resourceName) return;
        const type = String(row.type).toLowerCase();
        const name = row.resourceName;
        const item = {
            id: id++,
            name,
            description: row.remarks || "",
            riskLevel: mapRiskLevel(row),
            params: [],
        };
        if (type.includes("tool")) {
            tools.push(item);
        } else if (type.includes("resource")) {
            resources.push({ ...item, uri: name });
        } else if (type.includes("prompt")) {
            prompts.push({ ...item, args: [] });
        }
    });

    return { tools, resources, prompts, toolViolations };
}

// Last path segment of an MCP url — "/mcp/tools/list" → "list", "/search_company_database" →
// "search_company_database". This is the friendly component name shown in the list.
function mcpDisplayName(url) {
    const trimmed = String(url || "").replace(/\/+$/, "");
    const seg = trimmed.split("/").filter(Boolean).pop();
    return seg || trimmed || url;
}

// Map an MCP audit `type` ("mcp-tool" / "mcp-resource" / "mcp-prompt" / "mcp-server" / "AGENT_SKILL")
// to the component bucket — the authoritative classification, same as the legacy Audit Data page.
function bucketFromAuditType(type) {
    const t = String(type || "").toLowerCase();
    if (t.includes("skill")) return "Skill";
    if (t.includes("resource")) return "Resource";
    if (t.includes("prompt")) return "Prompt";
    if (t.includes("server")) return "Server";
    if (t.includes("tool")) return "Tool";
    return null;
}

// Fallback when no audit record matches — mirrors getMethod() in GetPrettifyEndpoint.jsx.
// MCP protocol endpoints (/mcp/initialize, /mcp/tools/list) are Server-type infrastructure.
function bucketFromUrl(url) {
    const u = String(url || "").toLowerCase();
    if (u.includes("skill")) return "Skill";
    if (u.includes("resource")) return "Resource";
    if (u.includes("prompt")) return "Prompt";
    if (u.includes("server")) return "Server";
    // MCP protocol paths (initialize, tools/list, ping) are server infrastructure, not user tools
    if (/^\/mcp\b/.test(u) || u === "initialize" || u === "ping") return "Server";
    return "Tool";
}

// Claude Cowork / shield built-in tools are ingested on the agent host as /tool/{name}.
export function isAgentBuiltinToolUrl(url) {
    const u = String(url || "");
    return u.startsWith("/tool/") && u.length > "/tool/".length;
}

// Built-in agent tools from agent-host STI (/tool/*). MCP tools stay on MCP collections.
export function buildAgentBuiltinToolsFromStis(stiEndpoints = [], apiInfoList = [], apiCollectionId, auditRows = []) {
    const toolEndpoints = (stiEndpoints || []).filter((ep) => {
        const url = ep?.url || ep?._id?.url;
        return isAgentBuiltinToolUrl(url);
    });
    return buildMcpComponentsFromStis(toolEndpoints, apiInfoList, apiCollectionId, auditRows).tools;
}

// Build flyout component rows from the collection's STI endpoints (real url+method, used to fetch sample
// traffic), classified by the MCP audit `type` field (authoritative) with a url-based fallback. Risk +
// violations are joined from apiInfoList. Skills are excluded — they belong under the agent's Skills.
export function buildMcpComponentsFromStis(stiEndpoints = [], apiInfoList = [], apiCollectionId, auditRows = []) {
    const infoByKey = {};
    apiInfoList.forEach((info) => {
        const m = info?.id?.method;
        const u = info?.id?.url;
        if (!m || !u) return;
        const vCount = Object.values(info.violations || {}).reduce((a, b) => a + (b || 0), 0);
        infoByKey[`${m} ${u}`] = { riskScore: info.riskScore || 0, violations: vCount };
    });

    // type + risk metadata lookup keyed by resourceName
    // mcp-server rows have a hostname as resourceName — skip those, they don't map to individual endpoints
    const typeByName = {};
    const isMaliciousByName = {};
    const privilegedByName = {};
    const riskDescByName = {};
    auditRows.forEach((row) => {
        if (!row?.type || !row?.resourceName) return;
        const t = String(row.type).toLowerCase();
        if (t.includes("server")) return;
        typeByName[row.resourceName] = row.type;
        const cra = row.componentRiskAnalysis || {};
        if (cra.isComponentMalicious) isMaliciousByName[row.resourceName] = true;
        if (cra.hasPrivilegedAccess) privilegedByName[row.resourceName] = true;
        // evidence is the authoritative "why" string shown in the tooltip (from the AI analysis).
        // Fall back to description then remarks so nothing is silently dropped.
        if (cra.evidence || cra.description || row.remarks) {
            riskDescByName[row.resourceName] = cra.evidence || cra.description || row.remarks || "";
        }
    });

    const tools = [], resources = [], prompts = [], skills = [];
    const toolViolations = {};
    let id = 0;

    stiEndpoints.forEach((ep) => {
        const method = ep?.method || ep?._id?.method;
        const url = ep?.url || ep?._id?.url;
        if (!method || !url) return;
        const name = mcpDisplayName(url);
        const type = bucketFromAuditType(typeByName[name]) || bucketFromUrl(url);
        const info = infoByKey[`${method} ${url}`] || {};
        const isMalicious = isMaliciousByName[name] || false;
        const hasPrivilegedAccess = privilegedByName[name] || false;
        const riskDescription = riskDescByName[name] || "";
        const riskScore = info.riskScore || 0;
        const item = { id: id++, name, url, method, apiCollectionId, description: "", riskScore, riskLevel: isMalicious ? "critical" : null, isMalicious, hasPrivilegedAccess, riskDescription, params: [], violations: info.violations || 0 };
        if (type === "Skill") {
            skills.push(item);
        } else if (type === "Resource") {
            resources.push({ ...item, uri: url });
        } else if (type === "Prompt") {
            prompts.push({ ...item, args: [] });
        } else {
            // Tool + Server both surface as tool-like components in the list
            if (info.violations) toolViolations[name] = info.violations;
            tools.push({ ...item, _serverType: type === "Server" });
        }
    });

    return { tools, resources, prompts, skills, toolViolations };
}

export function buildSkillsFlyoutData(collection, apiInfoList = [], stiEndpoints = [], apiCollectionId) {
    const skillNames = new Set(collection?.skills || []);
    const riskBySkill = {};
    const violationsBySkill = {};
    const urlBySkill = {};
    const methodBySkill = {};
    const descriptionBySkill = {};

    const skillNameFromUrl = (url) => {
        if (!url) return null;
        const idx = url.indexOf("skills/");
        if (idx < 0) return null;
        const name = url.substring(idx + "skills/".length);
        return name || null;
    };

    apiInfoList.forEach((info) => {
        const url = info?.id?.url;
        const skillName = skillNameFromUrl(url);
        if (!skillName) return;
        skillNames.add(skillName);
        urlBySkill[skillName] = url;
        if (info?.id?.method) methodBySkill[skillName] = info.id.method;
        if (info?.description) descriptionBySkill[skillName] = info.description;
        riskBySkill[skillName] = Math.max(riskBySkill[skillName] || 0, info.riskScore || 0);
        const vCount = Object.values(info.violations || {}).reduce((a, b) => a + (b || 0), 0);
        if (vCount > 0) violationsBySkill[skillName] = vCount;
    });

    // Also pick up skills present as STI endpoints (SKILL /skills/<name>) that may not yet have apiInfo
    stiEndpoints.forEach((ep) => {
        const skillName = skillNameFromUrl(ep?.url);
        if (!skillName) return;
        skillNames.add(skillName);
        if (!urlBySkill[skillName]) urlBySkill[skillName] = ep.url;
        if (!methodBySkill[skillName] && ep.method) methodBySkill[skillName] = ep.method;
    });

    let i = 0;
    const skills = [...skillNames].map((name) => ({
        id: i++,
        name: formatDisplayName(name),
        rawName: name,
        isNew: false,
        violations: violationsBySkill[name] || 0,
        blocked: false,
        riskScore: riskBySkill[name] || 0,
        url: urlBySkill[name] || `/skills/${name}`,
        method: methodBySkill[name] || "SKILL",
        apiCollectionId,
        description: descriptionBySkill[name] || "",
    }));

    return { skills };
}

function buildModuleDeviceMap(moduleInfos = []) {
    const map = {};
    moduleInfos.forEach((module) => {
        if (!module?.name) return;
        const ad = module.additionalData || {};
        map[module.name] = {
            username: ad.username || ad.userName || ad.user || ad.email || "-",
            team: ad.team || "",
            role: ad.userRole || "",
            os: ad.os || null,
        };
    });
    return map;
}

/**
 * Bucket a list of items into 12 monthly slots covering the given time window.
 * Each slot counts how many items fall in that calendar month.
 * If startTimestamp is 0 (all-time), uses the earliest item's month as the window start.
 *
 * @param {Array} items - raw objects
 * @param {function} getTs - extracts epoch-seconds timestamp from an item
 * @param {number} windowStart - epoch seconds (0 = all-time / use data min)
 * @param {number} windowEnd   - epoch seconds (0 = now)
 * @returns {number[]} 12-element array, oldest month first
 */
const MONTH_ABBR = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
const SECONDS_PER_DAY = 24 * 3600;

/**
 * Build monthly slots spanning the selected window [windowStart, windowEnd].
 * - windowStart > 0: span exactly from that month to the end month (matches the date filter).
 * - windowStart <= 0 (all-time): span from the earliest data month to now.
 * Each slot: { boundary (epoch s, 1st of month), label ("Jun '25") }.
 */
function buildWindowSlots(windowStart, windowEnd, dataMinTs) {
    const nowSec = Math.floor(Date.now() / 1000);
    const end = windowEnd > 0 ? windowEnd : nowSec;

    let startSec;
    if (windowStart > 0) {
        startSec = windowStart;
    } else {
        // all-time → anchor at earliest data point (fallback: 12 months back)
        startSec = dataMinTs > 0 ? dataMinTs : end - 365 * SECONDS_PER_DAY;
    }

    const startDate = new Date(startSec * 1000);
    const snapStart = new Date(startDate.getFullYear(), startDate.getMonth(), 1);
    const endDate = new Date(end * 1000);
    const snapEnd = new Date(endDate.getFullYear(), endDate.getMonth(), 1);

    const numMonths = Math.max(
        (snapEnd.getFullYear() - snapStart.getFullYear()) * 12 +
        (snapEnd.getMonth() - snapStart.getMonth()) + 1,
        1,
    );

    return Array.from({ length: numMonths }, (_, i) => {
        const d = new Date(snapStart.getFullYear(), snapStart.getMonth() + i, 1);
        const yr = String(d.getFullYear()).slice(2);
        return { boundary: Math.floor(d.getTime() / 1000), label: `${MONTH_ABBR[d.getMonth()]} '${yr}` };
    });
}

/** Earliest timestamp across one or more {items, getTs} series (0 if none). */
function earliestTs(seriesList) {
    let min = Infinity;
    seriesList.forEach(({ items, getTs }) => {
        items.forEach((item) => {
            const ts = getTs(item);
            if (ts > 0 && ts < min) min = ts;
        });
    });
    return Number.isFinite(min) ? min : 0;
}

/**
 * Compute a cumulative running-total array for one series over the given slots.
 * Items dated before the first slot seed the baseline so the line is a TRUE cumulative
 * total (ends at the count of all items <= windowEnd), not just within-window additions.
 */
function cumulativeCounts(items, getTs, slots, end, getWeight) {
    const n = slots.length;
    const firstBoundary = slots[0].boundary;
    let baseline = 0;
    const perMonth = new Array(n).fill(0);

    items.forEach((item) => {
        const w = getWeight ? getWeight(item) : 1;
        const ts = getTs(item);
        // Items without a usable in-window timestamp (missing, zero, or dated before the first
        // slot) still exist — seed them into the baseline so the running total reaches the true
        // grand total at the final month. Only genuinely future items (ts > end) are excluded.
        if (!ts || ts <= 0 || ts < firstBoundary) { baseline += w; return; }
        if (ts > end) return;
        let idx = -1;
        for (let i = 0; i < n; i++) {
            if (ts >= slots[i].boundary) idx = i;
        }
        if (idx >= 0) perMonth[idx] += w;
    });

    const counts = new Array(n).fill(0);
    let acc = baseline;
    for (let i = 0; i < n; i++) { acc += perMonth[i]; counts[i] = acc; }
    return counts;
}

/**
 * Cumulative monthly series for a single metric over the selected window.
 * Returns { labels, counts } — counts is a running total ending at the all-time total.
 * Pass `getWeight` to sum a per-item value (e.g. AI interactions) instead of counting rows.
 */
export function cumulativeByMonth(items, getTs, windowStart, windowEnd, getWeight) {
    const nowSec = Math.floor(Date.now() / 1000);
    const end = windowEnd > 0 ? windowEnd : nowSec;
    const dataMin = earliestTs([{ items, getTs }]);
    const slots = buildWindowSlots(windowStart, windowEnd, dataMin);
    return { labels: slots.map((s) => s.label), counts: cumulativeCounts(items, getTs, slots, end, getWeight) };
}

/**
 * Cumulative monthly series for multiple aligned series (shared slots/labels).
 * Returns { labels, data: [counts, ...] }.
 */
function cumulativeSeriesByMonth(seriesList, windowStart, windowEnd) {
    const nowSec = Math.floor(Date.now() / 1000);
    const end = windowEnd > 0 ? windowEnd : nowSec;
    const dataMin = earliestTs(seriesList);
    const slots = buildWindowSlots(windowStart, windowEnd, dataMin);
    const data = seriesList.map(({ items, getTs }) => cumulativeCounts(items, getTs, slots, end));
    return { labels: slots.map((s) => s.label), data };
}


export function buildDeviceEndpointsPageData(
    collections,
    trafficMap = {},
    riskScoreMap = {},
    { moduleInfos = [], usernameMap = {}, violationsByCollectionId = {}, violationRows = [], startTimestamp = 0, endTimestamp = 0 } = {},
) {
    const agenticCollections = collections.filter(isAgenticCollection);
    const deviceModules = buildModuleDeviceMap(moduleInfos);
    const deviceMap = {};
    const collectionIdToDevice = {};

    agenticCollections.forEach((collection) => {
        const hostName = collection.hostName || collection.displayName || collection.name;
        if (!hostName) return;
        const devId = extractEndpointId(hostName);
        if (!devId) return;
        // Skip collections ingested via an external connector (e.g. MICROSOFT_DEFENDER, source=DEFENDER).
        // These represent the AI agent as seen through the connector, not a real service child row.
        const tags = collection.envType || [];
        const isConnectorIngested = tags.some(t =>
            t.keyName === "connector" || (t.keyName === "source" && t.value === "DEFENDER")
        );
        if (isConnectorIngested) return;

        collectionIdToDevice[collection.id] = devId;
        if (!deviceMap[devId]) {
            const mod = deviceModules[devId] || {};
            const resolvedUsername = getResolvedUsernameForCollection(collection, usernameMap);
            const username = resolvedUsername !== DEFAULT_VALUE ? resolvedUsername : (mod.username || "-");
            deviceMap[devId] = {
                deviceId: devId,
                os: mod.os || inferOsFromDeviceId(devId),
                username,
                team: mod.team || "",
                role: mod.role || "",
                maxRisk: 0,
                maxTraffic: 0,
                hasPersonalAccount: false,
                hasMisconfiguredConfig: false,
                violations: emptyViolations(),
                children: {},
            };
        }
        const device = deviceMap[devId];
        // Personal-account marker is per-collection (browser-llm-account-type=personal /
        // login-user-email-type=personal); OR it across all of a device's collections.
        if (hasPersonalAccountTag(collection.envType)) device.hasPersonalAccount = true;
        if (hasMisconfiguredConfigTag(collection.envType)) device.hasMisconfiguredConfig = true;
        const serviceName = extractServiceName(hostName);
        const childKey = toChildPathKey(serviceName);
        const clientType = getTypeFromTags(collection.envType);
        const collRisk = riskScoreMap[collection.id] || 0;
        const traffic = trafficMap[collection.id] || 0;
        const skillCount = (collection.skills || []).length;

        if (!device.children[childKey]) {
            device.children[childKey] = {
                pathKey: childKey,
                endpoint: formatDisplayName(serviceName || hostName),
                rawServiceName: serviceName || hostName,
                type: clientType,
                collectionIds: [],
                skillCount: 0,
                skillNames: new Set(),
                riskScore: 0,
                lastTraffic: 0,
                violations: emptyViolations(),
            };
        }
        const child = device.children[childKey];
        child.collectionIds.push(collection.id);
        child.skillCount = Math.max(child.skillCount, skillCount);
        (collection.skills || []).forEach(s => { if (s) child.skillNames.add(s); });
        child.riskScore = Math.max(child.riskScore, collRisk);
        child.lastTraffic = Math.max(child.lastTraffic, traffic);

        const collViolations = violationsByCollectionId[collection.id];
        if (collViolations) mergeViolations(child.violations, collViolations);

        device.maxRisk = Math.max(device.maxRisk, collRisk);
        device.maxTraffic = Math.max(device.maxTraffic, traffic);
        if (collViolations) mergeViolations(device.violations, collViolations);
    });

    // Only enrich existing devices with module metadata — never create new deviceMap
    // entries from moduleInfos alone, as that would inflate the count with devices
    // that have no agentic collection traffic.
    Object.keys(deviceModules).forEach((devId) => {
        if (deviceMap[devId]) {
            const mod = deviceModules[devId];
            const device = deviceMap[devId];
            if (!device.os) device.os = mod.os || inferOsFromDeviceId(devId);
            if (!device.team) device.team = mod.team || "";
            if (!device.role) device.role = mod.role || "";
        }
    });

    const deviceFlatData = [];
    const agentRiskData = {};
    const devicesByUsername = {};

    Object.values(deviceMap).forEach((device) => {
        const deviceRow = {
            path: [device.deviceId],
            endpoint: device.username && device.username !== "-" ? device.username : "-",
            deviceId: device.deviceId,
            os: device.os,
            userCount: 1,
            riskScore: Math.round(device.maxRisk * 10) / 10,
            username: device.username,
            group: device.team,
            role: device.role,
            violations: { ...device.violations },
            lastTraffic: device.maxTraffic > 0 ? func.prettifyEpoch(device.maxTraffic) : "-",
            lastTrafficEpoch: device.maxTraffic || 0,
        };
        if (device.hasPersonalAccount) deviceRow.hasPersonalAccount = true;
        if (device.hasMisconfiguredConfig) deviceRow.hasMisconfiguredConfig = true;
        deviceFlatData.push(deviceRow);

        if (device.username && device.username !== "-") {
            if (!devicesByUsername[device.username]) devicesByUsername[device.username] = [];
            devicesByUsername[device.username].push({ endpoint: device.deviceId, os: device.os });
        }

        Object.values(device.children).forEach((child) => {
            const path = [device.deviceId, child.pathKey];
            deviceFlatData.push({
                path,
                endpoint: child.endpoint,
                rawServiceName: child.rawServiceName || child.endpoint,
                type: child.type,
                collectionIds: child.collectionIds,
                skillCount: child.skillCount > 0 ? child.skillCount : undefined,
                skillNames: child.skillNames.size > 0 ? [...child.skillNames] : undefined,
                lastTraffic: child.lastTraffic > 0 ? func.prettifyEpoch(child.lastTraffic) : "-",
                lastTrafficEpoch: child.lastTraffic || 0,
            });
            const riskKey = path.join("/");
            const entry = { riskScore: Math.round(child.riskScore * 10) / 10 };
            if (hasAnyViolation(child.violations)) entry.violations = { ...child.violations };
            agentRiskData[riskKey] = entry;
        });
    });

    const deviceCount = Object.keys(deviceMap).length;
    const users = new Set(Object.values(deviceMap).map((d) => d.username).filter((u) => u && u !== "-"));
    let agentChildCount = 0;
    const violationsBySeverity = emptyViolations();
    const osCounts = { mac: 0, windows: 0, linux: 0 };

    Object.values(deviceMap).forEach((d) => {
        osCounts[d.os] = (osCounts[d.os] || 0) + 1;
        mergeViolations(violationsBySeverity, d.violations);
        agentChildCount += Object.keys(d.children).length;
    });

    const totalViolations = Object.values(violationsBySeverity).reduce((a, b) => a + b, 0);

    // ── Real time-series bucketing ────────────────────────────────────────────
    // Endpoints & OS: bucket agenticCollections by startTs (when each collection was first seen)
    // Each unique device (first segment of hostName) is counted once per month using its
    // earliest collection's startTs as the "first seen" date.
    const deviceFirstSeen = {}; // deviceId → earliest startTs
    agenticCollections.forEach((c) => {
        const hostName = c.hostName || c.displayName || c.name;
        const devId = extractEndpointId(hostName);
        if (!devId) return;
        const ts = c.startTs || 0;
        if (!deviceFirstSeen[devId] || ts < deviceFirstSeen[devId].ts) {
            deviceFirstSeen[devId] = { ts, os: deviceMap[devId]?.os || inferOsFromDeviceId(devId) };
        }
    });
    const deviceFirstSeenItems = Object.values(deviceFirstSeen);

    // Unique-user first-seen: use the earliest collection startTs per username
    const userFirstSeen = {}; // username → earliest startTs
    agenticCollections.forEach((c) => {
        const hostName = c.hostName || c.displayName || c.name;
        const devId = extractEndpointId(hostName);
        if (!devId) return;
        const dev = deviceMap[devId];
        const username = dev?.username;
        if (!username || username === "-") return;
        const ts = c.startTs || 0;
        if (!userFirstSeen[username] || ts < userFirstSeen[username]) {
            userFirstSeen[username] = ts;
        }
    });
    const userFirstSeenItems = Object.values(userFirstSeen).map((ts) => ({ ts }));

    // OS trend: count of newly-first-seen devices per OS per month.
    // Use cumulativeSeriesByMonth so all 3 OS series share identical slot boundaries + labels.
    const macItems     = deviceFirstSeenItems.filter((d) => d.os === "mac");
    const windowsItems = deviceFirstSeenItems.filter((d) => d.os === "windows");
    const linuxItems   = deviceFirstSeenItems.filter((d) => d.os === "linux");

    const getTs = (d) => d.ts;
    // OS trend: cumulative new-devices-per-OS over the selected window, shared slots/labels.
    const { labels: monthLabels, data: osTrendData } = cumulativeSeriesByMonth(
        [
            { items: macItems,     getTs },
            { items: windowsItems, getTs },
            { items: linuxItems,   getTs },
        ],
        startTimestamp,
        endTimestamp,
    );
    const [macCounts, windowsCounts, linuxCounts] = osTrendData;

    // Sparklines — cumulative, same window as the OS trend
    const endpointBucket  = cumulativeByMonth(deviceFirstSeenItems, getTs, startTimestamp, endTimestamp);
    const userBucket      = cumulativeByMonth(userFirstSeenItems,   (d) => d.ts, startTimestamp, endTimestamp);
    const violationBucket = (() => {
        const agenticHosts = new Set();
        const looseAgenticHosts = new Set();
        Object.keys(violationsByCollectionId).forEach((collId) => {
            const coll = agenticCollections.find((c) => String(c.id) === String(collId));
            if (coll && coll.hostName) {
                agenticHosts.add(coll.hostName);
                const lk = deviceServiceKey(coll.hostName);
                if (lk) looseAgenticHosts.add(lk);
            }
        });

        const filtered = violationRows.filter(
            (v) => v.host && v.timeEpoch && (agenticHosts.has(v.host) || looseAgenticHosts.has(deviceServiceKey(v.host)))
        );
        return cumulativeByMonth(filtered, (v) => v.timeEpoch, startTimestamp, endTimestamp);
    })();

    const violCounts = (() => {
        const c = violationBucket.counts;
        if (!c.length || totalViolations == null) return c;
        const diff = totalViolations - (c[c.length - 1] || 0);
        if (diff === 0) return c;
        return c.map(v => Math.max(0, v + diff));
    })();

    function windowDelta(counts) {
        if (!counts || counts.length < 2) return 0;
        return Math.max(0, (counts[counts.length - 1] || 0) - (counts[0] || 0));
    }

    const summary = {
        totalEndpoints: agentChildCount,
        totalUsers: users.size,
        totalViolations,
        deviceCount,
        monthLabels,
        deltaEndpoints:  Math.max(0, windowDelta(endpointBucket.counts)),
        deltaUsers:      Math.max(0, windowDelta(userBucket.counts)),
        deltaViolations: windowDelta(violCounts),
        violationsBySeverity: [
            { name: "Critical", y: violationsBySeverity.critical, color: "#DC2626" },
            { name: "High", y: violationsBySeverity.high, color: "#F97316" },
            { name: "Medium", y: violationsBySeverity.medium, color: "#EAB308" },
            { name: "Low", y: violationsBySeverity.low, color: "#D1D5DB" },
        ],
        osTrend: {
            mac:     macCounts,
            windows: windowsCounts,
            linux:   linuxCounts,
        },
        statSparklines: {
            endpoints:  endpointBucket.counts,
            users:      userBucket.counts,
            violations: violCounts,
        },
    };

    return { deviceFlatData, agentRiskData, devicesByUsername, summary };
}
