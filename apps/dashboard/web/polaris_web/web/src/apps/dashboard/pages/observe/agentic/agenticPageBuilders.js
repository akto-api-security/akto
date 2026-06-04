import {
    extractEndpointId,
    extractServiceName,
} from "./constants";
import {
    formatDisplayName,
    getTypeFromTags,
} from "./mcpClientHelper";
import func from "@/util/func";
import { getResolvedUsernameForCollection, DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";

const AGENTIC_ENV_KEYS = new Set([
    "mcp-server", "gen-ai", "browser-llm", "mcp-client", "ai-agent", "browser-llm-agent", "skill",
]);

export function toChildPathKey(serviceName) {
    if (!serviceName) return "unknown";
    return String(serviceName).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

export function inferOsFromDeviceId(deviceId) {
    if (!deviceId) return "mac";
    const upper = String(deviceId).toUpperCase();
    if (upper.includes("WIN")) return "windows";
    if (upper.includes("LIN") || upper.includes("LINUX")) return "linux";
    if (upper.includes("MAC")) return "mac";
    return "mac";
}

export function isAgenticCollection(collection) {
    if (!collection || collection.deactivated) return false;
    const hostName = collection.hostName;
    if (hostName && hostName.split(".").length >= 3) return true;
    const envType = collection.envType || [];
    return envType.some((tag) => {
        const key = typeof tag === "string" ? tag.split("=")[0] : tag?.keyName;
        return key && AGENTIC_ENV_KEYS.has(key);
    });
}

function emptyViolations() {
    return { critical: 0, high: 0, medium: 0, low: 0 };
}

function normalizeSeverityKey(raw) {
    if (raw == null) return null;
    const s = String(raw).toLowerCase();
    if (s.includes("crit")) return "critical";
    if (s.includes("high")) return "high";
    if (s.includes("med")) return "medium";
    if (s.includes("low")) return "low";
    return null;
}

function mergeViolations(target, source) {
    if (!source) return;
    Object.entries(source).forEach(([k, v]) => {
        const sev = normalizeSeverityKey(k);
        if (sev && v) target[sev] = (target[sev] || 0) + Number(v);
    });
}

function hasAnyViolation(v) {
    return v && Object.values(v).some((c) => c > 0);
}

function toViolationsObject(v) {
    return {
        critical: v.critical || 0,
        high: v.high || 0,
        medium: v.medium || 0,
        low: v.low || 0,
    };
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

export function buildSkillsFlyoutData(collection, apiInfoList = []) {
    const skillNames = new Set(collection?.skills || []);
    const riskBySkill = {};
    const violationsBySkill = {};

    apiInfoList.forEach((info) => {
        const url = info?.id?.url;
        if (!url) return;
        const idx = url.indexOf("skills/");
        if (idx < 0) return;
        const skillName = url.substring(idx + "skills/".length);
        if (!skillName) return;
        skillNames.add(skillName);
        riskBySkill[skillName] = Math.max(riskBySkill[skillName] || 0, info.riskScore || 0);
        const vCount = Object.values(info.violations || {}).reduce((a, b) => a + (b || 0), 0);
        if (vCount > 0) violationsBySkill[skillName] = vCount;
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
                violations: emptyViolations(),
                children: {},
            };
        }
        const device = deviceMap[devId];
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
                riskScore: 0,
                lastTraffic: 0,
                violations: emptyViolations(),
            };
        }
        const child = device.children[childKey];
        child.collectionIds.push(collection.id);
        child.skillCount = Math.max(child.skillCount, skillCount);
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
            violations: toViolationsObject(device.violations),
            lastTraffic: device.maxTraffic > 0 ? func.prettifyEpoch(device.maxTraffic) : "-",
            lastTrafficEpoch: device.maxTraffic || 0,
        };
        if (device.hasPersonalAccount) deviceRow.hasPersonalAccount = true;
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
                lastTraffic: child.lastTraffic > 0 ? func.prettifyEpoch(child.lastTraffic) : "-",
                lastTrafficEpoch: child.lastTraffic || 0,
            });
            const riskKey = path.join("/");
            const entry = { riskScore: Math.round(child.riskScore * 10) / 10 };
            if (hasAnyViolation(child.violations)) entry.violations = toViolationsObject(child.violations);
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
    // Violations: per-month count over the selected window. Count ONLY rows whose
    // apiCollectionId belongs to an agentic device collection — the SAME rows that build
    // totalViolations (46). Counting all raw violationRows would inflate to the full feed.
    const violationBucket = (() => {
        const agenticCollectionIds = new Set(Object.keys(collectionIdToDevice).map(Number));
        const relevantRows = violationRows.filter((v) => agenticCollectionIds.has(Number(v.apiCollectionId)));
        const getViolTs = (v) => v.timeEpoch || 0;
        const dataMin = earliestTs([{ items: relevantRows, getTs: getViolTs }]);
        const slots = buildWindowSlots(startTimestamp, endTimestamp, dataMin);
        const n = slots.length;
        const counts = new Array(n).fill(0);
        relevantRows.forEach((v) => {
            const ts = getViolTs(v);
            if (!ts || ts <= 0 || ts < slots[0].boundary) return;
            let idx = -1;
            for (let i = 0; i < n; i++) { if (ts >= slots[i].boundary) idx = i; }
            if (idx >= 0) counts[idx]++;
        });
        return { counts };
    })();

    // Delta over the selected window: last cumulative point minus first cumulative point.
    // Positive = growth, negative = (only possible if data shifts) reduction.
    function windowDelta(counts) {
        if (!counts || counts.length < 2) return 0;
        return (counts[counts.length - 1] || 0) - (counts[0] || 0);
    }

    const summary = {
        totalEndpoints: agentChildCount,
        totalUsers: users.size,
        totalViolations,
        deviceCount,
        monthLabels,
        deltaEndpoints:  windowDelta(endpointBucket.counts),
        deltaUsers:      windowDelta(userBucket.counts),
        // Violations delta = totalViolations itself (the number shown is already period-filtered)
        deltaViolations: violationBucket.counts.reduce((a, b) => a + b, 0),
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
            violations: violationBucket.counts,
        },
    };

    return { deviceFlatData, agentRiskData, devicesByUsername, summary };
}
