import { flags } from "../components/flags/index.mjs";
import SessionStore from "../../../../main/SessionStore";

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
    if (filterParts.length > 0) params.set("filters", filterParts.join("&"));
    if (filters.eventStatus) params.set("eventStatus", filters.eventStatus);
    if (filters.startTimestamp) params.set("startTimestamp", filters.startTimestamp);
    if (filters.endTimestamp) params.set("endTimestamp", filters.endTimestamp);
    const url = `${window.location.origin}/dashboard/protection/threat-activity?${params.toString()}`;
    window.open(url, "_blank");
};
