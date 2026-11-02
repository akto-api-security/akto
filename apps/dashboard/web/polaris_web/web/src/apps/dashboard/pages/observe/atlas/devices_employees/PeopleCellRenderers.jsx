import { Avatar, Badge, Box, HorizontalStack, ProgressBar, Text, Tooltip } from "@shopify/polaris";
import func from "@/util/func";
import { HEALTH_BADGE_STATUS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, MODE_LABELS, OS_LABELS, RISK_MAX } from "./peopleConstants";

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
    return <Text as="span" fontWeight="semibold" truncate>{data.name}</Text>;
}

export function OsCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Text as="span" color="subdued" truncate>
            {OS_LABELS[data.os] || data.os}{data.extension ? " · Browser extension" : ""}
        </Text>
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

export function RiskCellRenderer({ data, context }) {
    if (!data) return null;
    if (context?.riskAvailable === false) {
        return (
            <Tooltip content="Risk data is unavailable right now">
                <Text as="span" color="subdued">—</Text>
            </Tooltip>
        );
    }
    const score = data.riskScore ?? 0;
    return (
        <Tooltip content="Highest API risk score (0 to 5) across this employee's AI tools" width="wide">
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Box width="110px"><ProgressBar progress={score * (100 / RISK_MAX)} size="small" color={score >= HIGH_RISK_THRESHOLD ? "critical" : "primary"} /></Box>
                <Text as="span" fontWeight="semibold">{score.toFixed(1)}</Text>
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
