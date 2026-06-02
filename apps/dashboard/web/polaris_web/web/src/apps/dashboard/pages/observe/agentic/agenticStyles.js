// Shared badge-palette constants and helpers for the agentic module.
// All badge colours in this folder must come from here — never introduce new values.

export const TYPE_STYLES = {
    "AI Agent":   { bg: "#F3E8FF", color: "#9642FC", border: "#D8B4FE" },
    "MCP Server": { bg: "#ECFDF5", color: "#0D9488", border: "#99F6E4" },
    "LLM":        { bg: "#FEFCE8", color: "#CA8A04", border: "#FDE047" },
    "Skill":      { bg: "#F9FAFB", color: "#6B7280", border: "#D1D5DB" },
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
