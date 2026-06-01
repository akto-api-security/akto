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
        };
    });
    return map;
}

export function buildDeviceEndpointsPageData(
    collections,
    trafficMap = {},
    riskScoreMap = {},
    { moduleInfos = [], usernameMap = {}, violationsByCollectionId = {} } = {},
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
                os: inferOsFromDeviceId(devId),
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

    Object.keys(deviceModules).forEach((devId) => {
        if (!deviceMap[devId]) {
            const mod = deviceModules[devId];
            deviceMap[devId] = {
                deviceId: devId,
                os: inferOsFromDeviceId(devId),
                username: mod.username || "-",
                team: mod.team || "",
                role: mod.role || "",
                maxRisk: 0,
                maxTraffic: 0,
                hasPersonalAccount: false,
                violations: emptyViolations(),
                children: {},
            };
        }
    });

    const deviceFlatData = [];
    const agentRiskData = {};
    const devicesByUsername = {};

    Object.values(deviceMap).forEach((device) => {
        const deviceRow = {
            path: [device.deviceId],
            endpoint: device.deviceId,
            os: device.os,
            userCount: 1,
            riskScore: Math.round(device.maxRisk * 10) / 10,
            username: device.username,
            group: device.team,
            role: device.role,
            violations: toViolationsObject(device.violations),
            lastTraffic: device.maxTraffic > 0 ? func.prettifyEpoch(device.maxTraffic) : "-",
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
                type: child.type,
                collectionIds: child.collectionIds,
                skillCount: child.skillCount > 0 ? child.skillCount : undefined,
                lastTraffic: child.lastTraffic > 0 ? func.prettifyEpoch(child.lastTraffic) : "-",
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
    const spark = (n) => Array.from({ length: 12 }, (_, i) => Math.max(0, Math.round(n * (0.7 + 0.03 * i))));

    const summary = {
        totalEndpoints: agentChildCount,
        totalUsers: users.size,
        totalViolations,
        deviceCount,
        violationsBySeverity: [
            { name: "Critical", y: violationsBySeverity.critical, color: "#DC2626" },
            { name: "High", y: violationsBySeverity.high, color: "#F97316" },
            { name: "Medium", y: violationsBySeverity.medium, color: "#EAB308" },
            { name: "Low", y: violationsBySeverity.low, color: "#D1D5DB" },
        ],
        osTrend: {
            mac: spark(osCounts.mac),
            windows: spark(osCounts.windows),
            linux: spark(osCounts.linux),
        },
        statSparklines: {
            endpoints: spark(agentChildCount),
            users: spark(users.size),
            violations: spark(totalViolations),
        },
    };

    return { deviceFlatData, agentRiskData, devicesByUsername, summary };
}
