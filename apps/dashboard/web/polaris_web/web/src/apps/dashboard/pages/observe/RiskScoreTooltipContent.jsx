import { Badge, Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";

const MAX_RISK_SCORE = 5;

const BAND_LABELS = { critical: "Critical", attention: "High", warning: "Medium", info: "Low", success: "None" };
const METER_COLORS = {
    critical: "bg-critical-strong",
    attention: "bg-caution-strong",
    warning: "bg-caution",
    info: "bg-info-strong",
    success: "bg-success-strong",
};

const MAX_DETAIL_NAMES = 2;
const ACRONYMS = new Set(["ssrf", "bola", "bfla", "idor", "xss", "sql", "sqli", "nosql", "jwt", "api", "cors", "csrf", "xxe", "rce", "lfi", "rfi", "pii", "ssn", "id", "mcp", "llm", "ai", "url", "http", "https", "ssl", "tls", "dos", "cve"]);

const formatPoints = (points) => Number(Number(points || 0).toFixed(1));

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

function FactorRow({ points, label, detail }) {
    return (
        <HorizontalStack gap="3" wrap={false} blockAlign="start">
            <Box minWidth="44px">
                <Text variant="bodyMd" fontWeight="semibold" alignment="end">{`+${formatPoints(points)}`}</Text>
            </Box>
            <VerticalStack gap="0">
                <Text variant="bodyMd">{label}</Text>
                {detail ? <Text variant="bodySm" color="subdued">{detail}</Text> : null}
            </VerticalStack>
        </HorizontalStack>
    );
}

export default function RiskScoreTooltipContent({ riskScore, status, factors }) {
    const applied = [...(factors || [])].sort((a, b) => (b.points || 0) - (a.points || 0));

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
                                <FactorRow key={index} points={factor.points} label={factor.label} detail={prettifyDetail(factor.detail)} />
                            ))}
                        </VerticalStack>
                    </Box>
                ) : null}
            </VerticalStack>
        </Box>
    );
}
