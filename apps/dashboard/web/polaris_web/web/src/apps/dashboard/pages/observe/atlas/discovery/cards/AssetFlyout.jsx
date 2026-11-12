import { useNavigate } from "react-router-dom";
import { Avatar, Badge, Banner, HorizontalStack, Text } from "@shopify/polaris";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import func from "@/util/func";
import { THREAT_ACTIVITY_PATH, clearSavedActivityFilters, threatActivityLink } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import { HIGH_RISK_THRESHOLD } from "../../devices_employees/peopleConstants";
import AtlasFlyout, { FlyoutRowList } from "../../shared/AtlasFlyout";
import AssetStatusBadge from "../shared/AssetStatusBadge";
import ToolAvatar from "../shared/ToolAvatar";
import useAssetStatusUpdate from "../shared/useAssetStatusUpdate";
import useToolDetail from "../shared/useToolDetail";
import useToolViolations from "../shared/useToolViolations";
import useToolCapabilities from "../useToolCapabilities";
import {
    ASSET_STATUS, ATLAS_DISCOVERY_CHAT_SCOPE, CAPABILITY_TITLES, CATEGORY_LABELS, GUARDRAIL_POLICIES_PATH, INSTALL_METHODS, LOAD_STATE, VIOLATION_WINDOW_LABEL,
} from "../constants";
import { capabilityTags, formatDate, plural, signalsOf } from "../transform";

const askSuggestions = (name) => [`Who installed ${name} first?`, `What did ${name} access this week?`, `Is there an approved alternative to ${name}?`];

function statsOf(detail) {
    return [
        { title: "Devices", data: detail.devices, variant: "headingLg" },
        { title: "Employees", data: detail.employees, variant: "headingLg" },
        // Same tile as the employee flyout: critical colour from the high-risk threshold.
        { title: "Risk score", data: detail.riskScore > 0 ? detail.riskScore : "—", variant: "headingLg", color: detail.riskScore >= HIGH_RISK_THRESHOLD ? "critical" : undefined },
        { title: "First seen", data: formatDate(detail.firstSeen), variant: "headingLg" },
    ];
}

const installMethodOf = (rowType, detail) => {
    const method = INSTALL_METHODS[rowType];
    return typeof method === "function" ? method(detail.localMcp) : method;
};

const accountsOf = (detail) => (detail.personalDevices > 0
    ? `Personal accounts on ${plural(detail.personalDevices, "device")} of ${detail.devices}`
    : "No personal accounts reported");

// Publisher and Install method rows are left out when Atlas has none.
function detailsOf(detail, rowType) {
    const signals = signalsOf(detail);
    const installMethod = installMethodOf(rowType, detail);
    return [
        { label: "Category", value: CATEGORY_LABELS[detail.category] || detail.category },
        { label: "Type", value: detail.clientType },
        ...(detail.vendorName ? [{ label: "Publisher", value: detail.vendorName }] : []),
        ...(installMethod ? [{ label: "Install method", value: installMethod }] : []),
        { label: "Accounts", value: accountsOf(detail), isWarning: detail.personalDevices > 0 },
        { label: "Signals", value: signals.length ? signals.map((signal) => signal.label).join(", ") : "None", isWarning: signals.some((signal) => signal.critical) },
        { label: "Last seen", value: detail.lastSeen > 0 ? func.prettifyEpoch(detail.lastSeen) : "—" },
    ];
}

const capabilityRows = (tools) => tools.map((tool) => ({
    key: tool.name,
    label: tool.name,
    sub: tool.description,
    trailing: (
        <HorizontalStack gap="1" wrap={false}>
            {capabilityTags(tool).map((tag) => <Badge key={tag.label} size="small" status={tag.critical ? "critical" : undefined}>{tag.label}</Badge>)}
        </HorizontalStack>
    ),
}));

const connectionRows = (detail) => (detail.connections || []).map((connection) => ({
    key: connection.name,
    label: connection.name,
    sub: plural(connection.devices, "device"),
    trailing: connection.status && <AssetStatusBadge status={connection.status} />,
}));

const employeeRows = (detail) => (detail.users || []).map((user) => ({
    key: user.username,
    icon: <Avatar size="extraSmall" name={user.username} initials={func.initials(user.username)} />,
    label: user.username,
    sub: user.team,
    trailing: <Text as="span" variant="bodySm" color="subdued">{user.lastUsed > 0 ? func.prettifyEpoch(user.lastUsed) : "—"}</Text>,
}));

// Flyout for one discovered tool; row is its list row ({ key, name, status, ... }) or null when closed.
export default function AssetFlyout({ row, deviceScope, refreshKey, onClose, onUpdated }) {
    const navigate = useNavigate();
    const { update, pending } = useAssetStatusUpdate(onUpdated);
    const key = row?.key;
    const { detail, failed } = useToolDetail(key, deviceScope, refreshKey);
    const rowType = key?.split("|")[0];
    const capabilities = useToolCapabilities(rowType, detail?.capabilityCollectionIds);
    const violations = useToolViolations(detail?.key);

    const status = detail?.status || row?.status;
    const change = (next) => update([{ key, status }], next, row.name);
    // Rows from the overview cards are always reviewable; table rows say so.
    const governable = detail ? detail.governable : row?.governable !== false;
    const actions = row && governable && [
        status === ASSET_STATUS.APPROVED
            ? { label: "Create guardrail policy", primary: true, onClick: () => navigate(GUARDRAIL_POLICIES_PATH, { state: { openCreateGuardrail: true } }) }
            : { label: "Approve", primary: true, disabled: pending, onClick: () => change(ASSET_STATUS.APPROVED) },
        status !== ASSET_STATUS.BLOCKED && { label: "Block", disabled: pending, onClick: () => change(ASSET_STATUS.BLOCKED) },
        status !== ASSET_STATUS.UNDER_REVIEW && status !== ASSET_STATUS.BLOCKED && { label: "Mark under review", disabled: pending, onClick: () => change(ASSET_STATUS.UNDER_REVIEW) },
    ].filter(Boolean);

    let loadState = LOAD_STATE.READY;
    if (failed && !detail) loadState = LOAD_STATE.ERROR;
    else if (!detail) loadState = LOAD_STATE.LOADING;

    return (
        <AtlasFlyout
            show={!!row}
            onClose={onClose}
            title={row?.name}
            icon={row && <ToolAvatar tool={row} size="large" />}
            subtitle={row && [row.clientType || CATEGORY_LABELS[row.category], detail?.vendorName].filter(Boolean).join(" · ")}
            headerExtra={status && governable && <AssetStatusBadge status={status} />}
            status={loadState}
            errorText="This tool is unavailable right now."
            actions={actions}
            askBar={row && (
                <AskBar
                    key={key}
                    label="Ask Atlas"
                    placeholder={`Ask Atlas about ${row.name}`}
                    suggestions={askSuggestions(row.name)}
                    chatScope={ATLAS_DISCOVERY_CHAT_SCOPE}
                />
            )}
            stats={detail && statsOf(detail)}
            details={detail && detailsOf(detail, rowType)}
            trailing={detail && <FlyoutRowList title="Last used by" rows={employeeRows(detail)} />}
        >
            {violations?.count > 0 && violations.key === key && (
                <Banner
                    status="critical"
                    title={`${plural(violations.count, "active guardrail violation")} in the ${VIOLATION_WINDOW_LABEL}`}
                    action={{ content: "View in guardrail activity", onAction: () => { clearSavedActivityFilters(THREAT_ACTIVITY_PATH); navigate(threatActivityLink({ atlasTool: key, atlasToolName: row.name, startTimestamp: violations.startTimestamp, endTimestamp: violations.endTimestamp })); } }}
                />
            )}
            {violations && violations.key === key && violations.count === null && <Banner status="warning" title="Guardrail violations are unavailable right now." />}
            {capabilities.failed && <Text as="p" color="subdued">Tool capabilities are unavailable right now.</Text>}
            <FlyoutRowList title={`${CAPABILITY_TITLES[rowType]} (${capabilities.total})`} rows={capabilityRows(capabilities.tools)} />
            {detail && <FlyoutRowList title={detail.connectionsKind === "agents" ? "Used by agents" : "Connected MCP servers"} rows={connectionRows(detail)} />}
        </AtlasFlyout>
    );
}
