import { useEffect, useMemo, useState } from "react";
import { Badge, Box, Button, HorizontalStack, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import SummaryCardInfo from "../../../../components/shared/SummaryCardInfo";
import SpinnerCentered from "../../../../components/progress/SpinnerCentered";
import AgenticFlyoutShell from "../../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../../agentic/FlyoutBreadcrumb";
import DetailGrid from "../../agentic/DetailGrid";
import AssetTopologyGraph from "../../agentic/AssetTopologyGraph";
import AiChatSection from "../../agentic/AiChatSection";
import { buildAgenticObserveChatMetadata } from "../../agentic/agenticObserveApi";
import { ACTION_UNAVAILABLE_TOOLTIP, ATLAS_CHAT_SCOPE, ATLAS_DEVICE_CHAT_SCOPE, GUARDRAIL_VIOLATIONS_PATH } from "./constants";
import { HEALTH_BADGE_STATUS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, MODE_LABELS, OS_LABELS } from "./peopleConstants";
import transform from "./transform";

const FLYOUT_WIDTH = 760;

const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;
const violationsUrl = (username) => `${GUARDRAIL_VIOLATIONS_PATH}?user=${encodeURIComponent(username)}`;
const lastSeen = (epoch) => (epoch ? func.prettifyEpoch(epoch) : "Never");

function deviceStats(device) {
    const mcpCount = device.toolsByCategory?.mcp || 0;
    return [
        { title: "AI tools", data: device.toolCount - mcpCount },
        { title: "MCP servers", data: mcpCount },
        { title: "Unapproved", data: device.unapprovedTools, color: device.unapprovedTools ? "critical" : undefined },
        { title: "Guardrail mode", data: MODE_LABELS[device.guardrailMode] || device.guardrailMode, color: device.guardrailMode === "monitor" ? "warning" : undefined },
    ];
}

function employeeStats(employee) {
    return [
        { title: "Risk score", data: employee.riskScore, color: employee.riskScore >= HIGH_RISK_THRESHOLD ? "critical" : undefined },
        { title: "Devices", data: employee.deviceCount },
        { title: "AI tools", data: employee.toolCount },
        { title: "Violations (7d)", data: employee.violationCount },
    ];
}

function deviceDetails(device) {
    return [
        { label: "Owner", value: device.owner },
        { label: "Atlas agent", value: [device.agentVersion, HEALTH_LABELS[device.health]].filter(Boolean).join(" · ") },
        { label: "Guardrail mode", value: MODE_LABELS[device.guardrailMode] || device.guardrailMode, isWarning: device.guardrailMode === "monitor" },
        { label: "Operating system", value: OS_LABELS[device.os] || device.os },
        { label: "Personal AI accounts", value: device.personalAccounts ? `${device.personalAccounts} detected` : "None", isWarning: device.personalAccounts > 0 },
        { label: "Last check-in", value: lastSeen(device.lastHeartbeat) },
    ];
}

function employeeDetails(employee) {
    return [
        { label: "User", value: employee.username },
        { label: "Team", value: (employee.teams || []).join(", ") || "Not tagged" },
        { label: "Personal AI accounts", value: employee.personalAccounts ? `${employee.personalAccounts} detected` : "None", isWarning: employee.personalAccounts > 0 },
        { label: "Unapproved tools", value: employee.unapprovedTools ? String(employee.unapprovedTools) : "None", isWarning: employee.unapprovedTools > 0 },
        { label: "Last active", value: lastSeen(employee.lastActive) },
    ];
}

function ActionButton({ action }) {
    if (!action.onClick && !action.url) {
        return (
            <Tooltip content={ACTION_UNAVAILABLE_TOOLTIP}>
                <Button primary={action.primary} disabled>{action.label}</Button>
            </Tooltip>
        );
    }
    return <Button primary={action.primary} plain={action.plain} url={action.url} onClick={action.onClick}>{action.label}</Button>;
}

function devicePrimary(device) {
    if (device.guardrailMode === "monitor") return "Switch to Enforce";
    if (device.health === "outdated") return "Update Atlas agent";
    if (device.health === "offline") return "Notify owner";
    if (device.unapprovedTools > 0) return "Remove unapproved tools";
    return "Run discovery scan";
}

function DeviceActions({ device, onOpen, onUpdated }) {
    const updateAgent = () => {
        func.showConfirmationModal(`Update the Atlas agent on ${device.name} to the latest version? It picks up the change on its next reboot.`, "Update", async () => {
            try {
                await settingRequests.bulkUpdateModuleEnvAndReboot([device.moduleId], { UPDATE_TO_LATEST_VERSION: "true" });
                func.setToast(true, false, `Atlas agent update queued for ${device.name}`);
                onUpdated();
            } catch (error) {
                func.setToast(true, true, "Failed to trigger the agent update");
            }
        });
    };
    const primaryLabel = devicePrimary(device);
    const updateAction = { label: "Update Atlas agent", onClick: updateAgent };
    const actions = [
        primaryLabel === updateAction.label ? { ...updateAction, primary: true } : { label: primaryLabel, primary: true },
        ...(device.health === "outdated" && primaryLabel !== updateAction.label ? [updateAction] : []),
        { label: "Isolate AI tools" },
        { label: "Open owner", onClick: device.owner ? () => onOpen("employee", device.owner) : undefined },
        { label: "View violations", plain: true, url: device.owner ? violationsUrl(device.owner) : undefined },
    ];
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {actions.map((action) => <ActionButton key={action.label} action={action} />)}
        </HorizontalStack>
    );
}

function EmployeeActions({ employee }) {
    const actions = [
        { label: "Send coaching message", primary: true },
        { label: "Notify manager" },
        { label: "Watch employee" },
        { label: "View violations", plain: true, url: violationsUrl(employee.username) },
    ];
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {actions.map((action) => <ActionButton key={action.label} action={action} />)}
        </HorizontalStack>
    );
}

function Footprint({ kind, detail }) {
    const graph = useMemo(() => transform.footprintGraph(kind, detail), [kind, detail]);
    return (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="baseline">
                <Text as="h3" variant="headingSm">AI footprint</Text>
                <Text as="span" variant="bodySm" color="subdued">Red links lead to unapproved or personal-account tools</Text>
            </HorizontalStack>
            {detail.toolCount > 0
                ? <AssetTopologyGraph nodes={graph.nodes} edges={graph.edges} height={graph.height} />
                : <Text as="p" color="subdued">No AI tools were seen in the last 7 days.</Text>}
        </VerticalStack>
    );
}

function PeopleFlyout({ target, deviceScope, onClose, onOpen, onUpdated }) {
    const [detail, setDetail] = useState(null);
    const [status, setStatus] = useState("loading");
    const isDevice = target?.kind === "device";

    useEffect(() => {
        if (!target) return undefined;
        let ignore = false;
        setStatus("loading");
        setDetail(null);
        settingRequests.fetchAtlasPersonDetail({ deviceScope, atlasKind: target.kind, atlasId: target.id })
            .then((response) => {
                if (ignore) return;
                setDetail(response?.atlasDetail || null);
                setStatus(response?.atlasDetail ? "ready" : "error");
            })
            .catch(() => {
                if (!ignore) setStatus("error");
            });
        return () => { ignore = true; };
    }, [target, deviceScope]);

    const chatMetadata = useMemo(() => {
        if (!target) return null;
        return isDevice
            ? buildAgenticObserveChatMetadata(ATLAS_DEVICE_CHAT_SCOPE, { deviceEndpoint: target.id, deviceId: target.id })
            : buildAgenticObserveChatMetadata(ATLAS_CHAT_SCOPE, { username: target.id, devices: detail?.devices || [] });
    }, [target, isDevice, detail]);

    if (!target) return null;

    const ready = status === "ready" && detail;
    const subtitle = !ready ? undefined : isDevice
        ? `${OS_LABELS[detail.os] || detail.os} · ${detail.owner || "No owner"}`
        : `${(detail.teams || []).join(", ") || "No team"} · ${plural(detail.deviceCount, "device")}`;

    return (
        <AgenticFlyoutShell
            show
            width={FLYOUT_WIDTH}
            header={
                <FlyoutBreadcrumb
                    items={[{ label: target.id, badge: ready && detail.riskScore ? detail.riskScore : undefined }]}
                    onClose={onClose}
                    subtitle={subtitle}
                >
                    {ready && isDevice && <Badge size="small" status={HEALTH_BADGE_STATUS[detail.health]}>{HEALTH_LABELS[detail.health]}</Badge>}
                </FlyoutBreadcrumb>
            }
            footer={
                <AiChatSection
                    placeholder={`Ask Atlas about ${target.id}...`}
                    resetKey={`${target.kind}:${target.id}`}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            }
        >
            <Box style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
                <Box padding="4">
                    {status === "loading" && <SpinnerCentered />}
                    {status === "error" && <Text as="p" color="subdued">Details for {target.id} could not be loaded.</Text>}
                    {ready && (
                        <VerticalStack gap="5">
                            {isDevice
                                ? <DeviceActions device={detail} onOpen={onOpen} onUpdated={onUpdated} />
                                : <EmployeeActions employee={detail} />}
                            <SummaryCardInfo summaryItems={isDevice ? deviceStats(detail) : employeeStats(detail)} />
                            <Footprint kind={target.kind} detail={detail} />
                            <DetailGrid items={isDevice ? deviceDetails(detail) : employeeDetails(detail)} labelWidth="160px" />
                        </VerticalStack>
                    )}
                </Box>
            </Box>
        </AgenticFlyoutShell>
    );
}

export default PeopleFlyout;
