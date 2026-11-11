import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { Badge, Box, Button, Divider, HorizontalStack, Select, Tabs, Text, VerticalStack } from "@shopify/polaris";
import { useNavigate } from "react-router-dom";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import SummaryCardInfo from "../../../../components/shared/SummaryCardInfo";
import SpinnerCentered from "../../../../components/progress/SpinnerCentered";
import AgenticFlyoutShell from "../../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../../agentic/FlyoutBreadcrumb";
import DetailGrid from "../../agentic/DetailGrid";
import AssetTopologyGraph from "../../agentic/AssetTopologyGraph";
import { LegendDot } from "../../agentic/AgenticStatsCard";
import AskBar from "../../../../components/shared/AskBar";
import LocalStore from "../../../../../main/LocalStorageStore";
import { openViolationInGuardrailViolations, openViolationInThreatActivity } from "../../agentic/agenticObserveApi";
import { RiskFactorRow } from "../../agentic/RiskFactorRow";
import { AgenticsTab, UserAnalysisSection, ViolationsTab, computeRiskFactors } from "../../agentic/DeviceFlyout";
import { ATLAS_CHAT_SCOPE, ATLAS_DEVICE_QUESTIONS, ATLAS_EMPLOYEE_QUESTIONS } from "./constants";
import { FOOTPRINT_LEGEND, HEALTH_BADGE_STATUS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, MODE_LABELS, formatNumber, osLabel } from "./peopleConstants";
import { ATLAS_VIOLATION_COLUMNS } from "./ViolationColumns";
import transform from "./transform";

const FLYOUT_WIDTH = 760;

const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;
const lastSeen = (epoch) => (epoch ? func.prettifyEpoch(epoch) : "Never");

function deviceStats(device) {
    const mcpCount = device.toolsByCategory?.mcp || 0;
    return [
        { title: "AI tools", data: formatNumber(device.toolCount - mcpCount) },
        { title: "MCP servers", data: formatNumber(mcpCount) },
        { title: "Skills", data: formatNumber(device.skillCount) },
        { title: "Plugins", data: formatNumber(transform.pluginCount(device.tools || [])) },
        { title: "Unapproved", data: formatNumber(device.unapprovedTools), color: device.unapprovedTools ? "critical" : undefined },
    ];
}

function employeeStats(employee) {
    return [
        { title: "Risk score", data: employee.riskScore, color: employee.riskScore >= HIGH_RISK_THRESHOLD ? "critical" : undefined },
        { title: "Devices", data: formatNumber(employee.deviceCount) },
        { title: "AI tools", data: formatNumber(transform.employeeAiToolCount(employee)) },
        { title: "Skills", data: formatNumber(employee.skillCount) },
        { title: "Violations (7d)", data: formatNumber(employee.violationCount) },
    ];
}

function deviceDetails(device) {
    return [
        { label: "Owner", value: device.owner },
        { label: "Atlas agent", value: [device.agentVersion, HEALTH_LABELS[device.health]].filter(Boolean).join(" · ") },
        { label: "Guardrail mode", value: MODE_LABELS[device.guardrailMode] || device.guardrailMode, isWarning: device.guardrailMode === "monitor" },
        { label: "Operating system", value: osLabel(device.os) || "—" },
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

const openViolation = (row) => (LocalStore.getState().guardrailViolationsNewLayout
    ? openViolationInGuardrailViolations({ refId: row.refId, filterId: row.filterId })
    : openViolationInThreatActivity(row));

function EmployeeAssets({ options }) {
    const [selected, setSelected] = useState(options[0]?.value);
    if (!options.length) return <Box padding="4"><Text as="p" color="subdued">No agentic assets discovered.</Text></Box>;
    return (
        <>
            <Box padding="3">
                <Select label="Device" labelInline options={options} value={selected} onChange={setSelected} />
            </Box>
            <AgenticsTab deviceId={selected} />
        </>
    );
}

const SEVERITY_RANK = { critical: 0, high: 1, medium: 2, low: 3 };

function riskFactorsOf(detail, isDevice) {
    const subject = isDevice
        ? detail
        : { violations: detail.violations, hasPersonalAccount: detail.personalAccounts > 0 };
    const factors = computeRiskFactors(subject, []);
    if (isDevice && detail.hasMisconfiguredConfig) {
        factors.push({ severity: "high", title: "Misconfigured config", description: "Contains misconfigured config", type: "misconfigured_config" });
    }
    factors.forEach((factor) => { factor.title = factor.title.replace(/^\d+/, formatNumber); });
    return factors.sort((a, b) => (SEVERITY_RANK[a.severity] ?? 99) - (SEVERITY_RANK[b.severity] ?? 99));
}

function RiskAnalysis({ factors, onOpenViolations }) {
    if (!factors.length) return null;
    return (
        <VerticalStack gap="2">
            <Text as="h3" variant="headingSm">Risk analysis</Text>
            <VerticalStack gap="0">
                {factors.map((factor, index) => (
                    <Fragment key={factor.type + factor.severity + index}>
                        {index > 0 && <Divider />}
                        <RiskFactorRow factor={factor} onClick={factor.type === "violation" ? onOpenViolations : undefined} />
                    </Fragment>
                ))}
            </VerticalStack>
        </VerticalStack>
    );
}

function ActionButton({ action }) {
    return <Button primary={action.primary} plain={action.plain} onClick={action.onClick}>{action.label}</Button>;
}

function DeviceActions({ device, onOpen, onUpdated, queued }) {
    const navigate = useNavigate();
    const updateAgent = () => {
        func.showConfirmationModal(`Update the Atlas agent on ${device.name} to the latest version? It picks up the change on its next reboot.`, "Update", async () => {
            try {
                await settingRequests.bulkUpdateModuleEnvAndReboot([device.moduleId], { UPDATE_TO_LATEST_VERSION: "true" });
                func.setToast(true, false, `Atlas agent update queued for ${device.name}`);
                onUpdated([device.moduleId]);
            } catch (error) {
                func.setToast(true, true, "Failed to trigger the agent update");
            }
        });
    };
    const outdated = device.health === "outdated" && !queued;
    const actions = [
        ...(outdated ? [{ label: "Update Atlas agent", primary: true, onClick: updateAgent }] : []),
        ...(device.unapprovedTools > 0 ? [{ label: "Review unapproved tools", primary: !outdated, onClick: () => navigate("/dashboard/observe/audit") }] : []),
        ...(device.owner ? [{ label: "Open owner", onClick: () => onOpen("employee", device.owner) }] : []),
    ];
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {actions.map((action) => <ActionButton key={action.label} action={action} />)}
        </HorizontalStack>
    );
}

function Footprint({ kind, detail }) {
    const [highlight, setHighlight] = useState(null);
    const [view, setView] = useState(transform.defaultFootprintView);
    const model = useMemo(() => transform.footprintGraph(kind, detail, view), [kind, detail, view]);
    const graph = useMemo(() => transform.footprintLayout(model, highlight), [model, highlight]);
    const onNodeClick = useCallback((event, node) => {
        if (node.type !== "topoNode") return;
        const action = model.actions[node.id];
        if (action) setView((current) => transform.nextFootprintView(current, action));
        if (action && action.type !== "skills-toggle") return;
        setHighlight((current) => (current === node.id ? null : node.id));
    }, [model]);
    const clearHighlight = useCallback(() => setHighlight(null), []);
    const hasGraph = detail.toolCount > 0 || detail.skillCount > 0;
    const resetView = useCallback(() => {
        setView(transform.defaultFootprintView());
        setHighlight(null);
    }, []);
    return (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="baseline">
                <Text as="h3" variant="headingSm">AI footprint</Text>
                <HorizontalStack gap="3" blockAlign="baseline">
                    {!transform.isDefaultFootprintView(view) && <Button plain onClick={resetView}>Reset view</Button>}
                </HorizontalStack>
            </HorizontalStack>
            {hasGraph ? (
                <VerticalStack gap="3">
                    <AssetTopologyGraph
                        nodes={graph.nodes}
                        edges={graph.edges}
                        height={graph.height}
                        onNodeClick={onNodeClick}
                        onPaneClick={clearHighlight}
                        fitKey={model}
                        fitPadding={0.02}
                    />
                    <HorizontalStack gap="4" blockAlign="center" wrap>
                        {FOOTPRINT_LEGEND.map((item) => (
                            <HorizontalStack key={item.label} gap="1" blockAlign="center" wrap={false}>
                                <LegendDot color={item.color} />
                                <Text as="span" variant="bodySm" color="subdued">{item.label}</Text>
                            </HorizontalStack>
                        ))}
                    </HorizontalStack>
                </VerticalStack>
            ) : <Text as="p" color="subdued">No AI tools discovered.</Text>}
        </VerticalStack>
    );
}

function PeopleFlyout({ target, deviceScope, onClose, onOpen, onUpdated, queuedIds = [] }) {
    const [detail, setDetail] = useState(null);
    const [status, setStatus] = useState("loading");
    const [selectedTab, setSelectedTab] = useState(0);
    const [violationsTotal, setViolationsTotal] = useState(null);
    const isDevice = target?.kind === "device";

    useEffect(() => {
        if (!target) return undefined;
        let ignore = false;
        setStatus("loading");
        setDetail(null);
        setSelectedTab(0);
        setViolationsTotal(null);
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

    useEffect(() => {
        if (!target) return undefined;
        const closeOnEscape = (event) => { if (event.key === "Escape") onClose(); };
        window.addEventListener("keydown", closeOnEscape);
        return () => window.removeEventListener("keydown", closeOnEscape);
    }, [target, onClose]);

    const ready = status === "ready" && detail;
    const { hostNames, deviceId } = useMemo(() => (ready ? transform.flyoutHosts(detail, isDevice) : { hostNames: [], deviceId: undefined }), [ready, detail, isDevice]);

    if (!target) return null;

    const subtitle = !ready ? undefined : isDevice
        ? [osLabel(detail.os), detail.owner || "No owner"].filter(Boolean).join(" · ")
        : `${(detail.teams || []).join(", ") || "No team"} · ${plural(detail.deviceCount, "device")}`;
    const displayName = ready ? (detail.label || detail.name || detail.username || target.id) : target.id;
    const questions = (isDevice ? ATLAS_DEVICE_QUESTIONS : ATLAS_EMPLOYEE_QUESTIONS).map((question) => question.replace("{name}", displayName));
    const tabs = !ready ? [] : [
        { id: "overview", content: "Overview" },
        { id: "assets", content: "Agentic Assets" },
        { id: "violations", content: `Violations (${formatNumber(violationsTotal ?? detail.violationCount)})` },
    ];
    const activeTab = tabs[selectedTab]?.id;
    const riskFactors = ready ? riskFactorsOf(detail, isDevice) : [];

    return (
        <AgenticFlyoutShell
            show
            width={FLYOUT_WIDTH}
            header={
                <>
                    <FlyoutBreadcrumb
                        items={[{ label: displayName, badge: ready && !isDevice && detail.riskScore ? detail.riskScore : undefined }]}
                        onClose={onClose}
                        subtitle={subtitle}
                    >
                        {ready && isDevice && <Badge size="small" status={HEALTH_BADGE_STATUS[detail.health]}>{HEALTH_LABELS[detail.health]}</Badge>}
                    </FlyoutBreadcrumb>
                    {ready && (
                        <>
                            <Box paddingInlineStart="1" paddingInlineEnd="1">
                                <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
                            </Box>
                            <Divider />
                        </>
                    )}
                </>
            }
        >
            <Box style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column" }}>
                {!ready && (
                    <Box padding="4">
                        {status === "loading" && <SpinnerCentered />}
                        {status === "error" && <Text as="p" color="subdued">Details for {target.id} could not be loaded.</Text>}
                    </Box>
                )}
                {ready && activeTab === "overview" && (
                    <Box padding="4">
                        <VerticalStack gap="5">
                            {isDevice && <DeviceActions device={detail} onOpen={onOpen} onUpdated={onUpdated} queued={queuedIds.includes(detail.moduleId)} />}
                            <AskBar
                                key={`${target.kind}:${target.id}`}
                                label="Ask Atlas"
                                placeholder={`Ask Atlas about ${target.id}`}
                                suggestions={questions}
                                chatScope={ATLAS_CHAT_SCOPE}
                            />
                            <SummaryCardInfo summaryItems={isDevice ? deviceStats(detail) : employeeStats(detail)} />
                            <Footprint key={`${target.kind}:${target.id}`} kind={target.kind} detail={detail} />
                            <RiskAnalysis factors={riskFactors} onOpenViolations={() => setSelectedTab(tabs.findIndex((tab) => tab.id === "violations"))} />
                            <DetailGrid items={isDevice ? deviceDetails(detail) : employeeDetails(detail)} labelWidth="160px" />
                            <UserAnalysisSection
                                key={`${target.kind}:${target.id}`}
                                username={isDevice ? detail.owner : detail.username}
                            />
                        </VerticalStack>
                    </Box>
                )}
                {ready && activeTab === "assets" && isDevice && (deviceId && (detail.toolCount > 0 || detail.skillCount > 0) ? <AgenticsTab deviceId={deviceId} /> : <Box padding="4"><Text as="p" color="subdued">No agentic assets discovered.</Text></Box>)}
                {ready && activeTab === "assets" && !isDevice && <EmployeeAssets key={target.id} options={transform.assetDeviceOptions(detail)} />}
                {ready && activeTab === "violations" && (
                    <ViolationsTab
                        hostNames={hostNames}
                        deviceId={deviceId}
                        onTotalChange={setViolationsTotal}
                        onViolationClick={openViolation}
                        columnDefs={ATLAS_VIOLATION_COLUMNS}
                    />
                )}
            </Box>
        </AgenticFlyoutShell>
    );
}

export default PeopleFlyout;
