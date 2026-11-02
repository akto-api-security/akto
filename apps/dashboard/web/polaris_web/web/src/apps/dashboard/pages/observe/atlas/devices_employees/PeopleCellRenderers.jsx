import { Avatar, Badge, Box, HorizontalStack, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import CustomProgressBar from "../../../dashboard/new_components/CustomProgressBar";
import { HEALTH_BADGE_STATUS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, MODE_LABELS, OS_LABELS, RISK_COLORS } from "./peopleConstants";

const MEDIUM_RISK_THRESHOLD = 50;

function riskColor(score) {
    if (score >= HIGH_RISK_THRESHOLD) return RISK_COLORS.high;
    return score >= MEDIUM_RISK_THRESHOLD ? RISK_COLORS.medium : RISK_COLORS.low;
}

export function EmployeeCellRenderer({ data }) {
    if (!data) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <Avatar size="small" name={data.username} initials={func.initials(data.username)} />
            <Text as="span" fontWeight="semibold" truncate>{data.username}</Text>
        </HorizontalStack>
    );
}

export function DeviceCellRenderer({ data }) {
    if (!data) return null;
    return (
        <VerticalStack gap="0">
            <Text as="span" fontWeight="semibold" truncate>{data.name}</Text>
            <Text as="span" variant="bodySm" color="subdued" truncate>
                {OS_LABELS[data.os] || data.os} · {data.extension ? "Browser extension" : "Atlas agent"}
            </Text>
        </VerticalStack>
    );
}

export function OwnerCellRenderer({ value }) {
    return <Text as="span" color={value ? undefined : "subdued"} truncate>{value || "—"}</Text>;
}

export function CountCellRenderer({ value }) {
    return <Text as="span" color={value ? undefined : "subdued"}>{value ?? 0}</Text>;
}

export function UnapprovedCellRenderer({ value }) {
    return <Text as="span" color={value ? "critical" : "subdued"} fontWeight={value ? "semibold" : "regular"}>{value ?? 0}</Text>;
}

export function ViolationsCellRenderer({ value, context }) {
    if (context?.violationsAvailable === false) {
        return (
            <Tooltip content="Violation data is unavailable right now">
                <Text as="span" color="subdued">—</Text>
            </Tooltip>
        );
    }
    return <Text as="span" color={value ? undefined : "subdued"}>{value ?? 0}</Text>;
}

export function RiskCellRenderer({ data }) {
    if (!data) return null;
    const content = `Unapproved tools +${data.riskUnapprovedPoints} (of 40), personal accounts +${data.riskPersonalPoints} (of 30), violations +${data.riskViolationPoints} (of 30)`;
    return (
        <Tooltip content={content} width="wide">
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Box width="90px"><CustomProgressBar progress={data.risk} topColor={riskColor(data.risk)} backgroundColor="#EDEEEF" height="8px" borderRadius="4px" /></Box>
                <Text as="span" fontWeight="semibold">{data.risk}</Text>
            </HorizontalStack>
        </Tooltip>
    );
}

export function LastSeenCellRenderer({ value }) {
    return <Text as="span" color="subdued">{value ? func.prettifyEpoch(value) : "Never"}</Text>;
}

export function AgentCellRenderer({ data }) {
    if (!data) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <Badge size="small" status={HEALTH_BADGE_STATUS[data.health]}>{HEALTH_LABELS[data.health]}</Badge>
            <Text as="span" variant="bodySm" color="subdued" truncate>{data.agentVersion || ""}</Text>
        </HorizontalStack>
    );
}

export function GuardrailModeCellRenderer({ value }) {
    return <Text as="span" color={value === "monitor" ? "warning" : value === "none" ? "subdued" : undefined}>{MODE_LABELS[value] || value}</Text>;
}
