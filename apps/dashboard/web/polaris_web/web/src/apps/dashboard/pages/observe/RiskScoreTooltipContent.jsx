import { Badge, Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";

// Max the scoring formula can reach (ApiInfoDao.getRiskScore): 1 new/threat + 1 public + 1 sensitive + 2 severity.
const MAX_RISK_SCORE = 5;

// Status comes from observeFunc.getStatus, so band label and meter colour always match the table badge.
const BAND_LABELS = { critical: "Critical", attention: "High", warning: "Medium", info: "Low", success: "None" };
const METER_COLORS = {
    critical: "bg-critical-strong",
    attention: "bg-caution-strong",
    warning: "bg-caution",
    info: "bg-info-strong",
    success: "bg-success-strong",
};

// The four inputs of the formula, mirroring RiskScoreExplainer.buildFactors. `labels` are the factor
// labels the backend can emit for the slot; `absent` is the muted row shown when the slot scored 0.
const FACTOR_SLOTS = [
    {
        key: "findings",
        labels: {
            "High or critical open test findings": "Serious weaknesses found by security tests",
            "Medium open test findings": "Moderate weaknesses found by security tests",
            "Low open test findings": "Minor weaknesses found by security tests",
        },
        absent: "No open test findings",
    },
    {
        key: "public",
        labels: { "Publicly accessible": "Reachable from outside your network" },
        absent: "Not publicly accessible",
    },
    {
        key: "sensitive",
        labels: { "Sensitive data in responses": "Responses contain personal or confidential data" },
        absent: "No sensitive data in responses",
    },
    {
        key: "recent",
        labels: {
            "Discovered in the last 30 days": "Recently added to your inventory",
            "Guardrail or threat activity": "Suspicious activity detected on this endpoint",
        },
        absent: "Not discovered in the last 30 days",
    },
];

const MAX_DETAIL_NAMES = 2;
const ACRONYMS = new Set(["ssrf", "bola", "bfla", "idor", "xss", "sql", "sqli", "nosql", "jwt", "api", "cors", "csrf", "xxe", "rce", "lfi", "rfi", "pii", "ssn", "id", "mcp", "llm", "ai", "url", "http", "https", "ssl", "tls", "dos", "cve"]);

const formatPoints = (points) => Number(Number(points || 0).toFixed(1));

const findSlot = (label) => FACTOR_SLOTS.find((slot) => slot.labels[label]);

// Backend details are raw ids ("SSRF_ON_FILE_UPLOAD", "EMAIL") and may end with "(+N more)".
const prettifyName = (name) => {
    if (!/^[A-Z0-9_ ]+$/.test(name) && !name.includes("_")) return name;
    const words = name.replace(/_/g, " ").trim().split(/\s+/).map((w) => w.toLowerCase());
    const text = words.map((w) => (ACRONYMS.has(w) ? w.toUpperCase() : w)).join(" ");
    return text.charAt(0).toUpperCase() + text.slice(1);
};

function prettifyDetail(detail) {
    if (!detail) return null;
    const match = String(detail).match(/^(.*?)(?: \(\+(\d+) more\))?$/);
    const names = match[1].split(", ").map((n) => n.trim()).filter(Boolean).map(prettifyName);
    if (names.length === 0) return null;
    const hidden = Math.max(0, names.length - MAX_DETAIL_NAMES) + Number(match[2] || 0);
    const shown = names.slice(0, MAX_DETAIL_NAMES).join(", ");
    return hidden > 0 ? `${shown} +${hidden} more` : shown;
}

// One block of the 5-block meter; fill is 0..1 so a score like 3.5 half-fills the fourth block.
function MeterBlock({ fill, color }) {
    return (
        <Box width="22px" minHeight="8px" background="bg-strong" borderRadius="1" overflowX="hidden">
            <Box width={`${Math.round(fill * 100)}%`} minHeight="8px" background={color} />
        </Box>
    );
}

function RiskMeter({ score, status }) {
    const color = METER_COLORS[status] || "bg-info-strong";
    return (
        <HorizontalStack gap="1" wrap={false}>
            {Array.from({ length: MAX_RISK_SCORE }, (_, i) => (
                <MeterBlock key={i} fill={Math.max(0, Math.min(1, score - i))} color={color} />
            ))}
        </HorizontalStack>
    );
}

function FactorRow({ points, label, explanation, detail, muted }) {
    const textColor = muted ? "subdued" : undefined;
    return (
        <HorizontalStack gap="3" wrap={false} blockAlign="start">
            <Box minWidth="44px">
                <Text variant="bodyMd" fontWeight={muted ? "regular" : "semibold"} color={textColor} alignment="end">{`+${formatPoints(points)}`}</Text>
            </Box>
            <VerticalStack gap="0">
                <Text variant="bodyMd" color={textColor}>{label}</Text>
                {detail ? <Text variant="bodySm" color="subdued">{detail}</Text> : null}
                {explanation ? <Text variant="bodySm" color="subdued">{explanation}</Text> : null}
            </VerticalStack>
        </HorizontalStack>
    );
}

export default function RiskScoreTooltipContent({ riskScore, status, factors, note }) {
    const applied = [...(factors || [])].sort((a, b) => (b.points || 0) - (a.points || 0));

    // Muted "doesn't apply" rows only when the list is complete (no shortfall note) and every label is
    // one we know — otherwise we'd be guessing what is missing.
    const canShowMissing = !note && applied.length > 0 && applied.every((f) => findSlot(f.label));
    const appliedSlots = new Set(applied.map((f) => findSlot(f.label)?.key));
    const missing = canShowMissing ? FACTOR_SLOTS.filter((slot) => !appliedSlots.has(slot.key)) : [];

    return (
        <Box width="350px" paddingBlockStart="1" paddingBlockEnd="1">
            <VerticalStack gap="3">
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                            <Text variant="headingXl" as="span">{formatPoints(riskScore)}</Text>
                            <Text variant="bodySm" color="subdued">{`out of ${MAX_RISK_SCORE}`}</Text>
                            <Badge status={status} size="small">{BAND_LABELS[status] || ""}</Badge>
                        </HorizontalStack>
                        <RiskMeter score={riskScore} status={status} />
                    </HorizontalStack>
                    <Text variant="bodySm" color="subdued">Based on the highest-risk endpoint in this asset</Text>
                </VerticalStack>

                {applied.length > 0 ? (
                    <Box borderBlockStartWidth="1" borderColor="border-subdued" paddingBlockStart="3">
                        <VerticalStack gap="3">
                            {applied.map((factor, index) => (
                                <FactorRow
                                    key={index}
                                    points={factor.points}
                                    label={factor.label}
                                    detail={prettifyDetail(factor.detail)}
                                    explanation={findSlot(factor.label)?.labels[factor.label]}
                                />
                            ))}
                            {missing.map((slot) => <FactorRow key={slot.key} points={0} label={slot.absent} muted />)}
                        </VerticalStack>
                    </Box>
                ) : null}

                {note ? <Text variant="bodySm" color="subdued">{note}</Text> : null}
            </VerticalStack>
        </Box>
    );
}
