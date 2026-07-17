import { flags } from "../components/flags/index.mjs";
import SessionStore from "../../../../main/SessionStore";
import PersistStore from "../../../../main/PersistStore";
import { getDashboardCategory, categoryToShortName } from "../../../../main/labelHelper";

// New tab starts with a fresh PersistStore, so carry the category via ?category= (see ThreatReport.jsx).
const getCategoryParam = () => categoryToShortName[getDashboardCategory()];

export const formatCategoryName = (name) => {
    if (!name) return "Unknown";
    return name.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (l) => l.toUpperCase());
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

export const openThreatActivityPage = (filters = {}) => {
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
    if (filters.startTimestamp) params.set("startTimestamp", filters.startTimestamp);
    if (filters.endTimestamp) params.set("endTimestamp", filters.endTimestamp);
    const categoryParam = getCategoryParam();
    if (categoryParam) params.set("category", categoryParam);
    const url = `${window.location.origin}/dashboard/protection/threat-activity?${params.toString()}`;
    window.open(url, "_blank");
};

export const openThreatActorsPage = (filters = {}) => {
    const params = new URLSearchParams();
    const filterParts = [];
    if (filters.country) filterParts.push(`country__${filters.country}`);
    if (filters.latestAttack) filterParts.push(`latestAttack__${filters.latestAttack}`);
    if (filterParts.length > 0) params.set("filters", filterParts.join("&"));
    if (filters.startTimestamp) params.set("since", filters.startTimestamp);
    if (filters.endTimestamp) params.set("until", filters.endTimestamp);
    const categoryParam = getCategoryParam();
    if (categoryParam) params.set("category", categoryParam);
    const url = `${window.location.origin}/dashboard/protection/threat-actor?${params.toString()}`;
    window.open(url, "_blank");
};
