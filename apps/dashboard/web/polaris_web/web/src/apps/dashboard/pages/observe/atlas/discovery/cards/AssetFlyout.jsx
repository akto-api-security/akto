import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Avatar, Badge, Banner, HorizontalStack, Text } from "@shopify/polaris";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import { getRiskLabel, getRiskLevel } from "@/apps/dashboard/pages/observe/agentic/agenticPageBuilders";
import func from "@/util/func";
import { clearSavedGuardrailActivityFilters, fetchActiveToolViolationCount, guardrailActivityLink } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import AtlasFlyout, { FlyoutRowList } from "../../shared/AtlasFlyout";
import discoveryApi from "../discoveryApi";
import AssetStatusBadge from "../shared/AssetStatusBadge";
import ToolAvatar from "../shared/ToolAvatar";
import useAssetStatusUpdate from "../shared/useAssetStatusUpdate";
import useToolCapabilities from "../useToolCapabilities";
import {
    ATLAS_DISCOVERY_CHAT_SCOPE, CAPABILITY_TITLES, CATEGORY_LABELS, FLYOUT_ASK_ENABLED, GUARDRAIL_POLICIES_PATH, HIGH_RISK_LEVELS, INSTALL_METHODS, VIOLATION_WINDOW_LABEL, VIOLATION_WINDOW_SECONDS,
} from "../constants";
import { capabilityTags, formatDate, signalsOf } from "../transform";

// Last 7 days ending on the next quarter hour, so tools opened close together share one server lookup.
const violationWindow = () => {
    const end = Math.ceil(Date.now() / 900000) * 900;
    return { startTimestamp: end - VIOLATION_WINDOW_SECONDS, endTimestamp: end };
};

const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;
const askSuggestions = (name) => [`Who installed ${name} first?`, `What did ${name} access this week?`, `Is there an approved alternative to ${name}?`];

function statsOf(detail) {
    const level = getRiskLevel(detail.riskScore || 0);
    return [
        { title: "Devices", data: detail.devices, variant: "headingLg" },
        { title: "Employees", data: detail.employees, variant: "headingLg" },
        { title: "Risk", data: detail.riskScore > 0 ? getRiskLabel(detail.riskScore).replace(" Risk", "") : "—", variant: "headingLg", color: HIGH_RISK_LEVELS.includes(level) ? "critical" : undefined },
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
    trailing: <Text as="span" variant="bodySm" color="subdued">{plural(user.devices, "device")}</Text>,
}));

// Flyout for one discovered tool; row is its list row ({ key, name, status, ... }) or null when closed.
export default function AssetFlyout({ row, startTimestamp, endTimestamp, deviceScope, refreshKey, onClose, onUpdated }) {
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
        // Another tool starts from a spinner; a refresh after an action keeps the open flyout (and its Ask chat).
        if (loadedKey.current !== key) {
            loadedKey.current = key;
            setDetail(null);
        }
        setFailed(false);
        discoveryApi.fetchAtlasToolDetail(key, startTimestamp, endTimestamp, deviceScope)
            .then((response) => { if (!ignore) setDetail(response); })
            .catch(() => { if (!ignore) setFailed(true); });
        return () => { ignore = true; };
    }, [key, startTimestamp, endTimestamp, deviceScope, refreshKey]);

    const status = detail?.status || row?.status;
    const change = (next) => update([{ key, status }], next, row.name);
    // Rows from the overview cards are always reviewable; table rows say so.
    const governable = detail ? detail.governable : row?.governable !== false;
    const actions = row && governable && [
        status === "APPROVED"
            ? { label: "Create guardrail policy", primary: true, onClick: () => navigate(GUARDRAIL_POLICIES_PATH, { state: { openCreateGuardrail: true } }) }
            : { label: "Approve", primary: true, disabled: pending, onClick: () => change("APPROVED") },
        status !== "BLOCKED" && { label: "Block", disabled: pending, onClick: () => change("BLOCKED") },
        status !== "UNDER_REVIEW" && { label: "Mark under review", disabled: pending, onClick: () => change("UNDER_REVIEW") },
    ].filter(Boolean);

    let loadState = "ready";
    if (failed && !detail) loadState = "error";
    else if (!detail) loadState = "loading";

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
            askBar={FLYOUT_ASK_ENABLED && row && (
                <AskBar
                    key={key}
                    showInput={false}
                    title={`Ask Atlas about ${row.name}`}
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
                    action={{ content: "View in guardrail activity", onAction: () => { clearSavedGuardrailActivityFilters(); navigate(guardrailActivityLink({ atlasTool: key, atlasToolName: row.name, startTimestamp: violations.startTimestamp, endTimestamp: violations.endTimestamp })); } }}
                />
            )}
            <FlyoutRowList title={`${CAPABILITY_TITLES[rowType]} (${capabilities.total})`} rows={capabilityRows(capabilities.tools)} />
            {detail && <FlyoutRowList title={detail.connectionsKind === "agents" ? "Used by agents" : "Connected MCP servers"} rows={connectionRows(detail)} />}
        </AtlasFlyout>
    );
}
