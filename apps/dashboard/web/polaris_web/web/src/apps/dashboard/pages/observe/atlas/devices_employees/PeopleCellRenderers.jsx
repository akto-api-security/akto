import { Avatar, Badge, HorizontalStack, Text, Tooltip } from "@shopify/polaris";
import func from "@/util/func";
import AgGridRow from "../../../../components/tables/rows/AgGridRow";
import { OsIcon } from "../../agentic/DeviceEndpoints";
import { RiskScoreCellRenderer, ViolationsCellRenderer as SeverityViolationsCellRenderer } from "../../agentic/AgenticCellRenderers";
import { BROWSER_ICONS, GENERIC_BROWSER_ICON, HEALTH_BADGE_STATUS, HEALTH_LABELS, MODE_LABELS, OS_ICON_KEYS, OS_LABELS } from "./peopleConstants";

function Unavailable({ what }) {
    return (
        <Tooltip content={`${what} data is unavailable right now`}>
            <Text as="span" color="subdued">—</Text>
        </Tooltip>
    );
}

export function EmployeeCellRenderer({ data }) {
    if (!data) return null;
    return (
        <AgGridRow
            icon={<Avatar size="extraSmall" name={data.username} initials={func.initials(data.username)} />}
            label={data.username}
            isBold
        />
    );
}

function BrowserIcon({ browser }) {
    return <img src={BROWSER_ICONS[browser] || GENERIC_BROWSER_ICON} width={16} height={16} alt={browser || "Browser"} style={{ flexShrink: 0 }} />;
}

const browserLabel = (browser) => (browser ? browser.charAt(0).toUpperCase() + browser.slice(1) : "Browser");

export function DeviceCellRenderer({ data }) {
    if (!data) return null;
    const icon = data.extension ? <BrowserIcon browser={data.browser} /> : <OsIcon os={OS_ICON_KEYS[data.os]} />;
    return <AgGridRow icon={icon} label={data.label || data.name} isBold />;
}

export function OsCellRenderer({ data }) {
    if (!data) return null;
    if (data.extension) return <AgGridRow icon={<BrowserIcon browser={data.browser} />} label={browserLabel(data.browser)} />;
    return <AgGridRow icon={<OsIcon os={OS_ICON_KEYS[data.os]} />} label={OS_LABELS[data.os] || data.os} />;
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

export function ViolationsCellRenderer(params) {
    if (params.context?.violationsAvailable === false) return <Unavailable what="Violation" />;
    return <SeverityViolationsCellRenderer {...params} />;
}

export function RiskCellRenderer(params) {
    if (params.context?.riskAvailable === false) return <Unavailable what="Risk" />;
    if (!params.value) return <Text as="span" color="subdued">0</Text>;
    return <RiskScoreCellRenderer {...params} />;
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
