// Shared badge-palette constants and helpers for the agentic module.
// All badge colours in this folder must come from here — never introduce new values.

export const TYPE_STYLES = {
    "AI Agent":   { bg: "#EFF6FF", color: "#1D4ED8", border: "#BFDBFE" },
    "MCP Server": { bg: "#FFFBEB", color: "#92400E", border: "#FDE68A" },
    "LLM":        { bg: "#F0FDF4", color: "#166534", border: "#BBF7D0" },
    "Skill":      { bg: "#F3E8FF", color: "#7E22CE", border: "#DDD6FE" },
};

export const SEVERITY_COLORS = {
    critical: { bg: "#DF2909", text: "#FFFBFB" },
    high:     { bg: "#FED3D1", text: "#202223" },
    medium:   { bg: "#FFD79D", text: "#202223" },
    low:      { bg: "#E4E5E7", text: "#202223" },
};

export function getRiskColor(score) {
    if (score >= 4.5) return { bg: "#FEE2E2", color: "#DC2626" };
    if (score >= 4.0) return { bg: "#FFEDD5", color: "#EA580C" };
    if (score >= 3.5) return { bg: "#FEF9C3", color: "#CA8A04" };
    return { bg: "#F0FDF4", color: "#16A34A" };
}

export function getRiskLabel(score) {
    if (score >= 4.5) return "Critical Risk";
    if (score >= 4.0) return "High Risk";
    if (score >= 3.5) return "Medium Risk";
    return "Low Risk";
}
