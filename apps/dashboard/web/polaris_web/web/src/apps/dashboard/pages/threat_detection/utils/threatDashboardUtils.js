import { flags } from "../components/flags/index.mjs";
import SessionStore from "../../../../main/SessionStore";
import PersistStore from "../../../../main/PersistStore";
import { getDashboardCategory, categoryToShortName } from "../../../../main/labelHelper";
import values from "@/util/values";
import GUARDRAIL_RULE_DEFINITIONS from "../constants/guardrailRuleDefinitions";
import { formatDisplayName } from "../../observe/agentic/mcpClientHelper";

// New tab starts with a fresh PersistStore, so carry the category via ?category= (see ThreatReport.jsx).
const getCategoryParam = () => categoryToShortName[getDashboardCategory()];

// The "All time" preset starts at new Date(1000), so forwarding it as a raw timestamp makes the
// destination page render a custom range starting Jan 1970. Forward the preset alias instead —
// both destination pages resolve ?range= before the timestamp params.
const allTimeRange = values.ranges.find((r) => r.alias === "allTime");
const allTimeStartTs = allTimeRange ? Math.floor(allTimeRange.period.since.getTime() / 1000) : 1;

const setTimeRangeParams = (params, filters, startKey, endKey) => {
    if (!filters.startTimestamp || !filters.endTimestamp) return;
    if (filters.startTimestamp <= allTimeStartTs) {
        params.set("range", "allTime");
        return;
    }
    params.set(startKey, filters.startTimestamp);
    params.set(endKey, filters.endTimestamp);
};

export const formatCategoryName = (name) => {
    if (!name) return "Unknown";
    return name.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (l) => l.toUpperCase());
};

// Same prefix list the guardrail detail dialog uses to recognize malicious-skill events
// (getGuardrailRuleInfo in guardrailRuleDefinitions.js) - single source of truth so the
// sidebar label and the detail dialog agree on what counts as a skill-detection event.
const SKILL_DETECTION_PREFIXES =
    GUARDRAIL_RULE_DEFINITIONS.find((def) => def.heading === "Malicious Skill Detected")?.prefixes || [];

const isSkillDetectionValue = (value) => {
    if (!value || typeof value !== "string") return false;
    const v = value.trim().toLowerCase();
    return SKILL_DETECTION_PREFIXES.some((p) => {
        const pfx = p.toLowerCase();
        return v.startsWith(pfx) || v.includes(pfx);
    });
};

// Label for a Recent Activity row: category only (e.g. "Block - Security Information") - never
// subCategory, which is often a raw rule id (e.g. "UserDefinedLLMRule") not meant to stand next
// to the category. Skill-related events (filterId "skill_evaluation", or a malicious-skill rule
// id like "malicious_skill_detected") always show "Skill Evaluation" instead, since the raw
// category on those events is whatever violation the skill scan found, not meaningful on its own
// without knowing it came from a skill evaluation.
export const getRecentActivityLabel = (event) => {
    const isSkillEvent = event?.filterId === "skill_evaluation"
        || isSkillDetectionValue(event?.subCategory || event?.filterId);

    if (isSkillEvent) return "Skill Evaluation";
    if (event?.category?.trim()) return formatCategoryName(event.category);
    return formatCategoryName(event?.filterId);
};

// Split a guardrail host into { username, agent } for the Top Endpoints with Violations list.
// Hosts come in two shapes:
//   <id>.ai-agent.<agent>        e.g. usmbskxhjd93xcjhf-3b129dde.ai-agent.codex
//   <id>.<source>.<service...>   e.g. saianvithalolla.chrome.chatgpt.com
// The username is always the first segment; the agent label is the segment after the literal
// "ai-agent" marker when present, otherwise the second segment - run through formatDisplayName
// so raw connector values (codex, claudecli, ...) render as their branded names.
export const parseHostForDisplay = (host) => {
    if (!host) return { username: "Unknown", agent: null };
    const clean = host.replace(/:\d+$/, "");
    const parts = clean.split(".");
    if (parts.length < 2) return { username: clean, agent: null };
    const agentSegment = parts[1]?.toLowerCase() === "ai-agent" ? (parts[2] || parts[1]) : parts[1];
    return { username: parts[0], agent: formatDisplayName(agentSegment) };
};

/**
 * Convert a subCategory value (e.g. "OS_COMMAND_INJECTION") to the corresponding
 * filterId / template _id (e.g. "OSCommandInjection") used by the backend filter.
 * Falls back to the original value if no mapping is found.
 */
export const subCategoryToFilterId = (subCategory) => {
    if (!subCategory) return subCategory;
    const threatFiltersMap = SessionStore.getState().threatFiltersMap || {};
    for (const [filterId, template] of Object.entries(threatFiltersMap)) {
        if (template?.subCategory === subCategory || filterId === subCategory) {
            return filterId;
        }
    }
    return subCategory;
};

export const COUNTRY_NAMES = {
    US: "USA", GB: "United Kingdom", DE: "Germany", RU: "Russia",
    CN: "China", IN: "India", BR: "Brazil", FR: "France",
    JP: "Japan", KR: "South Korea", CA: "Canada", AU: "Australia",
    PK: "Pakistan", IR: "Iran", UA: "Ukraine", NL: "Netherlands",
    VN: "Vietnam", TW: "Taiwan", ID: "Indonesia", TR: "Turkey",
    IT: "Italy", ES: "Spain", PL: "Poland", MX: "Mexico",
    TH: "Thailand", SG: "Singapore", AR: "Argentina", ZA: "South Africa",
    SE: "Sweden", NO: "Norway", FI: "Finland", DK: "Denmark",
    CZ: "Czech Republic", RO: "Romania", HU: "Hungary", BG: "Bulgaria",
    CL: "Chile", CO: "Colombia", EG: "Egypt", SA: "Saudi Arabia",
    AE: "UAE", IL: "Israel", MY: "Malaysia", PH: "Philippines",
    BD: "Bangladesh", NG: "Nigeria", KE: "Kenya",
};

export const countryCodeToName = (code) => {
    if (!code) return "Unknown";
    return COUNTRY_NAMES[code.toUpperCase()] || code.toUpperCase();
};

export const getFlagSrc = (countryCode) => {
    if (!countryCode) return flags["earth"];
    return countryCode in flags ? flags[countryCode] : flags["earth"];
};

/**
 * Apply a single filter dimension on the Threat/Guardrail Activity table (same page).
 * Replaces only the given filter key; keeps all other applied filters.
 * Page key matches GithubServerTable: pathname + "/" + hash.
 * Returns { resolvedValue, filterStr } so the caller can sync the filters= URL via react-router.
 */
export const applyThreatActivityTableFilter = (filterKey, filterValue) => {
    if (!filterKey || filterValue == null || filterValue === '') return null;
    const resolvedValue = filterKey === 'latestAttack'
        ? subCategoryToFilterId(filterValue)
        : filterValue;
    const pageKey = window.location.pathname + "/" + window.location.hash;
    const prev = PersistStore.getState().filtersMap || {};
    const fromStore = (prev[pageKey]?.filters || []).filter(f => f.key !== filterKey);

    const params = new URLSearchParams(window.location.search);
    const urlFiltersStr = decodeURIComponent(params.get("filters") || "");
    const fromUrl = (urlFiltersStr ? urlFiltersStr.split("&").filter(Boolean) : [])
        .map((part) => {
            const [key, valuesStr = ""] = part.split("__");
            const clean = valuesStr.replace("|negated", "");
            return { key, value: clean.split(",").filter(Boolean) };
        })
        .filter((f) => f.key && f.key !== filterKey);

    const storeKeys = new Set(fromStore.map((f) => f.key));
    const mergedOthers = [...fromStore, ...fromUrl.filter((f) => !storeKeys.has(f.key))];
    const newFilters = [...mergedOthers, { key: filterKey, value: [resolvedValue] }];

    PersistStore.getState().setFiltersMap({
        ...prev,
        [pageKey]: {
            filters: newFilters,
            sort: prev[pageKey]?.sort || [],
        },
    });

    const filterStr = newFilters.map((f) => {
        const vals = Array.isArray(f.value)
            ? f.value.join(",")
            : (f.value?.values ? f.value.values.join(",") : f.value);
        return `${f.key}__${vals}`;
    }).join("&");

    return { resolvedValue, filterStr };
};

const openActivityPage = (path, filters) => {
    const params = new URLSearchParams();
    const filterParts = [];
    if (filters.host) filterParts.push(`host__${filters.host}`);
    if (filters.latestAttack) {
        const filterId = subCategoryToFilterId(filters.latestAttack);
        filterParts.push(`latestAttack__${filterId}`);
    }
    if (filters.actor) filterParts.push(`actor__${filters.actor}`);
    if (filters.url) filterParts.push(`url__${filters.url}`);
    if (filters.severity) filterParts.push(`severity__${filters.severity}`);
    if (filterParts.length > 0) params.set("filters", filterParts.join("&"));
    if (filters.eventStatus) params.set("eventStatus", filters.eventStatus);
    setTimeRangeParams(params, filters, "startTimestamp", "endTimestamp");
    const categoryParam = getCategoryParam();
    if (categoryParam) params.set("category", categoryParam);
    const url = `${window.location.origin}${path}?${params.toString()}`;
    window.open(url, "_blank");
};

export const openThreatActivityPage = (filters = {}) =>
    openActivityPage("/dashboard/protection/threat-activity", filters);

// Guardrail Activity is the same table on its own route (see LeftNav), so it takes the same filters.
export const openGuardrailActivityPage = (filters = {}) =>
    openActivityPage("/dashboard/guardrails/activity", filters);

export const openThreatActorsPage = (filters = {}) => {
    const params = new URLSearchParams();
    const filterParts = [];
    if (filters.country) filterParts.push(`country__${filters.country}`);
    if (filters.latestAttack) filterParts.push(`latestAttack__${filters.latestAttack}`);
    if (filterParts.length > 0) params.set("filters", filterParts.join("&"));
    setTimeRangeParams(params, filters, "since", "until");
    const categoryParam = getCategoryParam();
    if (categoryParam) params.set("category", categoryParam);
    const url = `${window.location.origin}/dashboard/protection/threat-actor?${params.toString()}`;
    window.open(url, "_blank");
};
