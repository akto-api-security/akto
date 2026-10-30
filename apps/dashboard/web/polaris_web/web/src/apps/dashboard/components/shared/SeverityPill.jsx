import Pill from "./Pill";

const SEVERITY_COLORS = {
    CRITICAL: ["#FED3D1", "#8E1F0B"],
    HIGH: ["#FFD79D", "#7A2E00"],
    MEDIUM: ["#FFEA8A", "#5C4600"],
    LOW: ["#E4E5E7", "#44474A"],
};

export default function SeverityPill({ severity }) {
    const key = String(severity || "").toUpperCase();
    const colors = SEVERITY_COLORS[key];
    if (!colors) return null;
    return (
        <Pill background={colors[0]} color={colors[1]} style={{ minWidth: "64px", padding: "5px 15px", fontWeight: 500 }}>
            {key.charAt(0) + key.slice(1).toLowerCase()}
        </Pill>
    );
}
