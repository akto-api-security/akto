import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Avatar, Badge, Banner, HorizontalStack, Text } from "@shopify/polaris";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import func from "@/util/func";
import { THREAT_ACTIVITY_PATH, clearSavedActivityFilters, fetchActiveToolViolationCount, threatActivityLink } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import { HIGH_RISK_THRESHOLD } from "../../devices_employees/peopleConstants";
import AtlasFlyout, { FlyoutRowList } from "../../shared/AtlasFlyout";
import discoveryApi from "../discoveryApi";
import AssetStatusBadge from "../shared/AssetStatusBadge";
import ToolAvatar from "../shared/ToolAvatar";
import useAssetStatusUpdate from "../shared/useAssetStatusUpdate";
import useToolCapabilities from "../useToolCapabilities";
import {
    ASSET_STATUS, ATLAS_DISCOVERY_CHAT_SCOPE, CAPABILITY_TITLES, CATEGORY_LABELS, GUARDRAIL_POLICIES_PATH, INSTALL_METHODS, LOAD_STATE, VIOLATION_WINDOW_LABEL, VIOLATION_WINDOW_SECONDS,
} from "../constants";
import { capabilityTags, formatDate, plural, signalsOf } from "../transform";

// Last 7 days ending on the next quarter hour, so tools opened close together share one server lookup.
const violationWindow = () => {
    const end = Math.ceil(Date.now() / 900000) * 900;
    return { startTimestamp: end - VIOLATION_WINDOW_SECONDS, endTimestamp: end };
};

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
    const [fetched, setDetail] = useState(null);
    const [failed, setFailed] = useState(false);
    const { update, pending } = useAssetStatusUpdate(onUpdated);
    const key = row?.key;
    // Never the previous tool's detail while the next one loads.
    const detail = fetched?.key === key ? fetched : null;
    const loadedKey = useRef(null);
    const rowType = key?.split("|")[0];
    const capabilities = useToolCapabilities(rowType, detail?.capabilityCollectionIds);
    // Active violations of one tool, counted as the activity page counts them; null until known.
    const [violations, setViolations] = useState(null);
    const loadedDetailKey = detail?.key;

    useEffect(() => {
        setViolations(null);
        if (!loadedDetailKey) return undefined;
        let ignore = false;
        const range = violationWindow();
        fetchActiveToolViolationCount(loadedDetailKey, range.startTimestamp, range.endTimestamp)
            .then((count) => { if (!ignore) setViolations({ key: loadedDetailKey, count, ...range }); });
        return () => { ignore = true; };
    }, [loadedDetailKey]);

    useEffect(() => {
        if (!key) {
            loadedKey.current = null;
            setDetail(null);
            setFailed(false);
            return undefined;
        }
        let ignore = false;
        // Another tool starts from a spinner; a refresh after an action keeps the open flyout.
        if (loadedKey.current !== key) {
            loadedKey.current = key;
            setDetail(null);
        }
        setFailed(false);
        discoveryApi.fetchAtlasToolDetail(key, deviceScope)
            .then((response) => { if (!ignore) setDetail(response); })
            .catch(() => { if (!ignore) setFailed(true); });
        return () => { ignore = true; };
    }, [key, deviceScope, refreshKey]);

    const status = detail?.status || row?.status;
    const change = (next) => update([{ key, status }], next, row.name);
    // Rows from the overview cards are always reviewable; table rows say so.
    const governable = detail ? detail.governable : row?.governable !== false;
    const actions = row && governable && [
        status === ASSET_STATUS.APPROVED
            ? { label: "Create guardrail policy", primary: true, onClick: () => navigate(GUARDRAIL_POLICIES_PATH, { state: { openCreateGuardrail: true } }) }
            : { label: "Approve", primary: true, disabled: pending, onClick: () => change(ASSET_STATUS.APPROVED) },
        status !== ASSET_STATUS.BLOCKED && { label: "Block", disabled: pending, onClick: () => change(ASSET_STATUS.BLOCKED) },
        status !== ASSET_STATUS.UNDER_REVIEW && { label: "Mark under review", disabled: pending, onClick: () => change(ASSET_STATUS.UNDER_REVIEW) },
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
            trailing={detail && <FlyoutRowList title="Top employees" rows={employeeRows(detail)} />}
        >
            {violations?.count > 0 && violations.key === key && (
                <Banner
                    status="critical"
                    title={`${plural(violations.count, "active guardrail violation")} in the ${VIOLATION_WINDOW_LABEL}`}
                    action={{ content: "View in guardrail activity", onAction: () => { clearSavedActivityFilters(THREAT_ACTIVITY_PATH); navigate(threatActivityLink({ atlasTool: key, atlasToolName: row.name, startTimestamp: violations.startTimestamp, endTimestamp: violations.endTimestamp })); } }}
                />
            )}
            <FlyoutRowList title={`${CAPABILITY_TITLES[rowType]} (${capabilities.total})`} rows={capabilityRows(capabilities.tools)} />
            {detail && <FlyoutRowList title={detail.connectionsKind === "agents" ? "Used by agents" : "Connected MCP servers"} rows={connectionRows(detail)} />}
        </AtlasFlyout>
    );
}
