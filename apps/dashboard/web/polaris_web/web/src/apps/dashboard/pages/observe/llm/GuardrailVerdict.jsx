import { Badge, Box, Button, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import { getBehaviourTone } from "../../threat_detection/utils/formatUtils";
import { openGuardrailActivityPage } from "../../threat_detection/utils/threatDashboardUtils";
import { truncate } from "./constants";

// Guardrail result saved on a trace span when the traffic was ingested (Gateway.recordGuardrailVerdict).
// Shows whether a policy was hit, which policy and rule, what was done about it and why, so an
// investigation does not have to look up the threat event separately.
//
// guardrailViolated means "a policy was hit", not "the request was blocked": the guardrails service
// returns Allowed=false for warn and alert as well. guardrailAction says what actually happened, so
// the outcome shown here comes from the action, not from the flag on its own.

// A span has no guardrail keys when guardrails did not run on it, and for anything ingested before
// this feature. Both show nothing, because "Passed" would wrongly suggest the prompt was checked
// and cleared.
export function hasGuardrailVerdict(span) {
    if (!span) return false;
    return span.guardrailViolated !== undefined
        || !!span.guardrailAction
        || !!span.guardrailPolicy
        || !!span.guardrailRule;
}

// Only "block" actually stopped the request, so only it gets the red box. Warn, alert and approval
// let the traffic through, so they read as caution.
const CRITICAL_BOX = { bg: "bg-critical-subdued", border: "border-critical" };
const CAUTION_BOX  = { bg: "bg-caution-subdued",  border: "border-caution" };

// The label and colour come from the same place the Guardrail Activity table uses, so a policy set
// to "Alert" reads the same on a trace as it does on its activity row. "approval" is stored as-is
// but shown as "Human Approval", again matching that table.
function classify(span) {
    if (!span?.guardrailViolated) return { label: "Passed", status: "success" };

    const behaviour = String(span.guardrailAction || "").toLowerCase();
    // A policy was hit but the behaviour was not saved, so we cannot say the traffic went through.
    if (!behaviour) return { label: "Violation", status: "critical", box: CRITICAL_BOX };

    return {
        label: behaviour === "approval" ? "Human Approval" : func.toSentenceCase(behaviour),
        status: getBehaviourTone(behaviour) || "attention",
        box: behaviour === "block" ? CRITICAL_BOX : CAUTION_BOX,
    };
}

// Small badge for a span header.
export function GuardrailVerdictBadge({ span }) {
    if (!hasGuardrailVerdict(span)) return null;
    const { label, status } = classify(span);
    return <Badge status={status} size="small">{label}</Badge>;
}

// Severity uses the dashboard-wide badge-wrapper-<SEVERITY> classes, so it is coloured the same here
// as on the guardrail policy and activity pages instead of rendering as a plain grey chip.
function SeverityBadge({ severity }) {
    if (!severity) return null;
    const value = String(severity).toUpperCase();
    return (
        <div className={`badge-wrapper-${value}`}>
            <Badge size="small">{value}</Badge>
        </div>
    );
}

// Background and border for a turn box, decided in the same place as the badge so a new behaviour
// only has to be added once. Null for clean turns, which get no colour.
export function verdictTone(span) {
    return classify(span).box || null;
}

// Status for one span, for callers that sum up several spans in a single badge.
export function verdictStatus(span) {
    return classify(span).status;
}

// How far either side of the span to look for its guardrail event, in seconds. The event is
// detected a moment after the traffic is recorded, so the two timestamps are close but not equal.
const EVENT_SEARCH_WINDOW = 15 * 60;

// Opens Guardrail Activity on the event behind this verdict. A guardrail event is filtered by its
// policy name, which is the same value saved on the span, so filtering by policy over a short window
// around the span lands on the matching row.
function GuardrailActivityLink({ span }) {
    if (!span?.guardrailViolated || !span.guardrailPolicy || !span.timestamp) return null;
    const seconds = Math.floor(span.timestamp / 1000);
    return (
        <Button
            plain
            onClick={() => openGuardrailActivityPage({
                latestAttack: span.guardrailPolicy,
                startTimestamp: seconds - EVENT_SEARCH_WINDOW,
                endTimestamp: seconds + EVENT_SEARCH_WINDOW,
            })}
        >
            View in Guardrail Activity
        </Button>
    );
}

/**
 * One line at the top of a conversation turn. Keeps the result next to its own exchange without
 * breaking up the prompt and response. Shown only when a policy was hit, since a clean turn needs
 * no line. The badge already names the behaviour, so the detail text is just policy and rule, and
 * the reason is cut short to keep the banner to a single line - the full text is in the span detail.
 */
export function GuardrailBanner({ span }) {
    if (!hasGuardrailVerdict(span) || !span.guardrailViolated) return null;
    const { label, status } = classify(span);
    const detail = [span.guardrailPolicy, span.guardrailRule].filter(Boolean).join(" · ");
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap>
            <Badge status={status} size="small">{label}</Badge>
            <SeverityBadge severity={span.guardrailSeverity} />
            {detail && <Text variant="bodySm" fontWeight="medium">{detail}</Text>}
            {span.guardrailReason && (
                <Text variant="bodySm" color="subdued">{truncate(span.guardrailReason, 70)}</Text>
            )}
            <GuardrailActivityLink span={span} />
        </HorizontalStack>
    );
}

function Field({ label, value }) {
    if (!value) return null;
    return (
        <HorizontalStack gap="2" blockAlign="start" wrap={false}>
            <Box minWidth="88px">
                <Text variant="bodySm" color="subdued">{label}</Text>
            </Box>
            <Text variant="bodySm" breakWord>{value}</Text>
        </HorizontalStack>
    );
}

// Full box: policy, rule, behaviour, severity and reason. Shown only when there is something to
// say, so clean spans do not get an empty box. The box colour comes from the same classify() as the
// badge, so a warn reads as caution here exactly as it does in the turn banner.
export default function GuardrailVerdict({ span }) {
    if (!hasGuardrailVerdict(span)) return null;
    const { label, status, box } = classify(span);

    const hasDetail = span.guardrailPolicy || span.guardrailRule
        || span.guardrailAction || span.guardrailReason;
    if (!span.guardrailViolated && !hasDetail) return null;

    return (
        <Box
            background={box ? box.bg : "bg-subdued"}
            borderRadius="2"
            padding="2"
            borderWidth="1"
            borderColor={box ? box.border : "border-subdued"}
        >
            <VerticalStack gap="1">
                <HorizontalStack gap="2" blockAlign="center" wrap>
                    <Text variant="bodySm" color="subdued" fontWeight="semibold">GUARDRAIL</Text>
                    <Badge status={status} size="small">{label}</Badge>
                    <SeverityBadge severity={span.guardrailSeverity} />
                    <GuardrailActivityLink span={span} />
                </HorizontalStack>
                <Field label="Policy" value={span.guardrailPolicy} />
                <Field label="Rule" value={span.guardrailRule} />
                <Field label="Behaviour" value={span.guardrailAction} />
                <Field label="Reason" value={span.guardrailReason} />
            </VerticalStack>
        </Box>
    );
}
