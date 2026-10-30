const STATUS_LABEL = { ACTIVE: "Open", FIXED: "Fixed", IGNORED: "Ignored", UNDER_REVIEW: "In Review", HUMAN_APPROVAL: "Human Approval" };
const STATUS_DOT_COLOR = { ACTIVE: "#9642FC", FIXED: "#5BC0DE", IGNORED: "#F5C451", UNDER_REVIEW: "#637381", HUMAN_APPROVAL: "#5BC0DE" };

export default function ViolationStatusBadge({ status }) {
    if (!status) return null;
    const key = String(status).toUpperCase();
    const label = STATUS_LABEL[key] || status;
    const color = STATUS_DOT_COLOR[key];
    return (
        <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
            {color && <span className="agentic-dot" style={{ "--dot-color": color }} />}
            <span style={{ fontSize: "12px" }}>{label}</span>
        </span>
    );
}
