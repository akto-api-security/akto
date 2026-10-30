import { Badge } from "@shopify/polaris";
import func from "@/util/func";

export function guardrailActionLabel(behaviour) {
    if (behaviour === "block") return "Blocked";
    if (behaviour === "warn" || behaviour === "flag") return "Flagged";
    return behaviour ? func.toSentenceCase(behaviour) : "Flagged";
}

export default function GuardrailActionBadge({ action }) {
    if (!action) return null;
    return <Badge size="small" status={action === "Blocked" ? "critical" : "warning"}>{action}</Badge>;
}
