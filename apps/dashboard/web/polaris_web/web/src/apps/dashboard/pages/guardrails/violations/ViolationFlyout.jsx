import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
    ActionList,
    Badge,
    Box,
    Button,
    Divider,
    HorizontalStack,
    Modal,
    Popover,
    Tabs,
    Text,
} from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";

import AgenticFlyoutShell from "@/apps/dashboard/pages/observe/agentic/AgenticFlyoutShell";
import AiChatSection from "@/apps/dashboard/pages/observe/agentic/AiChatSection";
import { SeverityBadge } from "@/apps/dashboard/pages/observe/agentic/AgenticCellRenderers";
import ActivityTracker from "@/apps/dashboard/pages/dashboard/components/ActivityTracker";
import JiraTicketCreationModal from "@/apps/dashboard/components/shared/JiraTicketCreationModal";
import func from "@/util/func";
import threatDetectionApi from "@/apps/dashboard/pages/threat_detection/api";
import issuesApi from "@/apps/dashboard/pages/issues/api";
import settingFunctions from "@/apps/dashboard/pages/settings/module";
import issuesFunctions from "@/apps/dashboard/pages/issues/module";

import {
    ChatSessionSection,
    FileSection,
    OverviewSection,
    RemediationSection,
} from "./ViolationFlyoutSections";
import { buildFallbackDetail } from "./violationsData";
import "../../../components/layouts/style.css";

// ─── Event Actions dropdown ───────────────────────────────────────────────────

function EventActionsDropdown({ violationId, eventStatus, onStatusUpdate, row }) {
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);

    // Jira state
    const [jiraModalActive, setJiraModalActive]   = useState(false);
    const [jiraProjectMaps, setJiraProjectMaps]   = useState({});
    const [projId, setProjId]                     = useState("");
    const [issueType, setIssueType]               = useState("");
    const [jiraTicketUrl, setJiraTicketUrl]       = useState("");

    // Azure Boards state
    const [azureModalActive, setAzureModalActive]         = useState(false);
    const [projectToWorkItemsMap, setProjectToWorkItemsMap] = useState({});
    const [azureProjectId, setAzureProjectId]             = useState("");
    const [workItemType, setWorkItemType]                 = useState("");
    const [azureBoardsUrl, setAzureBoardsUrl]             = useState("");

    useEffect(() => {
        if (window.AZURE_BOARDS_INTEGRATED === 'true') {
            issuesFunctions.fetchCreateABWorkItemFieldMetaData();
        }
    }, []);

    const handleStatusChange = async (newStatus) => {
        if (!violationId) return;
        setOpen(false);
        setLoading(true);
        try {
            const response = await threatDetectionApi.updateMaliciousEventStatus({ eventId: violationId, status: newStatus });
            if (response?.updateSuccess) {
                onStatusUpdate?.(newStatus);
                const label = newStatus === "UNDER_REVIEW" ? "marked for review"
                    : newStatus === "IGNORED" ? "ignored" : "reactivated";
                func.setToast(true, false, `Event ${label} successfully`);
            } else {
                func.setToast(true, true, "Failed to update event status");
            }
        } catch {
            func.setToast(true, true, "Failed to update event status");
        } finally {
            setLoading(false);
        }
    };

    const handleJiraClick = async () => {
        setOpen(false);
        try {
            const integration = await settingFunctions.fetchJiraIntegration();
            if (integration.projectIdsMap && Object.keys(integration.projectIdsMap).length > 0) {
                setJiraProjectMaps(integration.projectIdsMap);
                setProjId(Object.keys(integration.projectIdsMap)[0]);
            } else {
                setProjId(integration.projId || "");
                setIssueType(integration.issueType || "");
            }
        } catch {
            func.setToast(true, true, "Failed to fetch Jira integration settings");
        }
        setJiraModalActive(true);
    };

    const handleJiraSave = async () => {
        if (!projId || !issueType) {
            func.setToast(true, true, "Please select a project and issue type");
            return;
        }
        try {
            const title = `${row?.violation || "Guardrail Violation"} - ${row?.agenticAsset || row?.user || "Unknown"}`;
            const description = [
                "Guardrail Violation Alert",
                "",
                `Policy: ${row?.policyName || "—"}`,
                `Severity: ${row?.severity || "—"}`,
                `Type: ${row?.type || "—"}`,
                `Agentic Asset: ${row?.agenticAsset || "—"}`,
                `User: ${row?.user || "—"}`,
                `Detected: ${row?.detected ? func.epochToDateTime(row.detected) : "—"}`,
                "",
                "Evidence:",
                row?.evidenceText || "—",
                "",
                `Reference URL: ${window.location.href}`,
            ].join("\n");
            func.setToast(true, false, "Creating Jira Ticket");
            const response = await issuesApi.createGeneralJiraTicket({ title, description, projId, issueType, threatEventId: violationId });
            if (response?.errorMessage) { func.setToast(true, true, response.errorMessage); return; }
            if (response?.jiraTicketUrl) {
                setJiraTicketUrl(response.jiraTicketUrl);
                func.setToast(true, false, "Jira Ticket Created Successfully");
            }
        } catch {
            func.setToast(true, true, "Failed to create Jira ticket");
        }
    };

    const handleAzureClick = async () => {
        setOpen(false);
        try {
            const integration = await settingFunctions.fetchAzureBoardsIntegration();
            if (integration.projectToWorkItemsMap && Object.keys(integration.projectToWorkItemsMap).length > 0) {
                setProjectToWorkItemsMap(integration.projectToWorkItemsMap);
                const firstProject = Object.keys(integration.projectToWorkItemsMap)[0];
                setAzureProjectId(firstProject);
                setWorkItemType(Object.values(integration.projectToWorkItemsMap)[0]?.[0] || "");
            } else {
                setAzureProjectId(integration?.projectId || "");
                setWorkItemType(integration?.workItemType || "");
            }
        } catch {
            func.setToast(true, true, "Failed to fetch Azure Boards integration settings");
        }
        setAzureModalActive(true);
    };

    const handleAzureSave = async () => {
        if (!azureProjectId || !workItemType) {
            func.setToast(true, true, "Please select a project and work item type");
            return;
        }
        try {
            let customABWorkItemFieldsPayload = [];
            try { customABWorkItemFieldsPayload = issuesFunctions.prepareCustomABWorkItemFieldsPayload(azureProjectId, workItemType); }
            catch { func.setToast(true, true, "Please fill all required fields before creating an Azure Boards work item."); return; }

            const title = `${row?.violation || "Guardrail Violation"} - ${row?.agenticAsset || row?.user || "Unknown"}`;
            const description = [
                "Guardrail Violation Alert",
                "",
                `Policy: ${row?.policyName || "—"}`,
                `Severity: ${row?.severity || "—"}`,
                `Type: ${row?.type || "—"}`,
                `Agentic Asset: ${row?.agenticAsset || "—"}`,
                `User: ${row?.user || "—"}`,
                `Detected: ${row?.detected ? func.epochToDateTime(row.detected) : "—"}`,
                "",
                "Evidence:",
                row?.evidenceText || "—",
                "",
                `Reference URL: ${window.location.href}`,
            ].join("\n");
            func.setToast(true, false, "Creating Azure Boards Work Item");
            const response = await issuesApi.createGeneralAzureBoardsWorkItem({
                title, description,
                projectName: azureProjectId,
                workItemType,
                threatEventId: violationId,
                aktoDashboardHostName: window.location.origin,
                customABWorkItemFieldsPayload,
            });
            if (response?.errorMessage) { func.setToast(true, true, response.errorMessage); return; }
            if (response?.azureBoardsWorkItemUrl) {
                setAzureBoardsUrl(response.azureBoardsWorkItemUrl);
                func.setToast(true, false, "Azure Boards Work Item Created Successfully");
            }
        } catch {
            func.setToast(true, true, "Failed to create Azure Boards work item");
        }
    };

    const items = [
        (eventStatus === "UNDER_REVIEW" || eventStatus === "TRIAGE")
            ? { content: "Reactivate", onAction: () => handleStatusChange("ACTIVE") }
            : { content: "Mark for Review", onAction: () => handleStatusChange("UNDER_REVIEW") },
        eventStatus === "IGNORED"
            ? { content: "Reactivate", onAction: () => handleStatusChange("ACTIVE") }
            : { content: "Ignore", onAction: () => handleStatusChange("IGNORED") },
        jiraTicketUrl
            ? { content: "View Jira Ticket", onAction: () => window.open(jiraTicketUrl, "_blank") }
            : { content: "Create Jira Ticket", onAction: handleJiraClick, disabled: window.JIRA_INTEGRATED !== "true" },
        azureBoardsUrl
            ? { content: "View Work Item", onAction: () => window.open(azureBoardsUrl, "_blank") }
            : { content: "Create Work Item", onAction: handleAzureClick, disabled: window.AZURE_BOARDS_INTEGRATED !== "true" },
    ];

    return (
        <>
            <Popover
                active={open}
                onClose={() => setOpen(false)}
                activator={
                    <Button size="slim" disclosure loading={loading} disabled={!violationId} onClick={() => setOpen((p) => !p)}>
                        Event Actions
                    </Button>
                }
                autofocusTarget="none"
                preferredAlignment="right"
            >
                <Box minWidth="180px">
                    <ActionList actionRole="menuitem" items={items} />
                </Box>
            </Popover>

            <JiraTicketCreationModal
                activator={null}
                modalActive={jiraModalActive}
                setModalActive={setJiraModalActive}
                handleSaveAction={handleJiraSave}
                jiraProjectMaps={jiraProjectMaps}
                projId={projId}
                setProjId={setProjId}
                issueType={issueType}
                setIssueType={setIssueType}
                issueId={violationId}
            />
            <JiraTicketCreationModal
                activator={null}
                modalActive={azureModalActive}
                setModalActive={setAzureModalActive}
                handleSaveAction={handleAzureSave}
                jiraProjectMaps={projectToWorkItemsMap}
                projId={azureProjectId}
                setProjId={setAzureProjectId}
                issueType={workItemType}
                setIssueType={setWorkItemType}
                issueId={violationId}
                isAzureModal={true}
            />
        </>
    );
}

// ─── Block IPs button ─────────────────────────────────────────────────────────

function BlockIpsButton({ ip }) {
    const [showModal, setShowModal] = useState(false);

    return (
        <>
            <Button destructive size="slim" onClick={() => setShowModal(true)}>Block IPs</Button>
            <Modal
                open={showModal}
                onClose={() => setShowModal(false)}
                title="Block IP ranges"
                primaryAction={{ content: "Block", destructive: true, onAction: () => { setShowModal(false); func.setToast(true, false, "IP block coming soon"); } }}
                secondaryActions={[{ content: "Cancel", onAction: () => setShowModal(false) }]}
            >
                <Modal.Section>
                    <Text variant="bodyMd" color="subdued">
                        By blocking these IP ranges, no user will be able to access your application from {ip || "this source"}.
                        Are you sure you want to block these IPs?
                    </Text>
                </Modal.Section>
            </Modal>
        </>
    );
}

// ─── Header ─────────────────────────────────────────────────────────────────────

function FlyoutHeader({ row, onClose, onStatusUpdate }) {
    return (
        <>
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="3" paddingBlockEnd="3">
                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="headingMd" fontWeight="semibold">{row.violation}</Text>
                        <SeverityBadge severity={row.severity} />
                        {row.action && (
                            <Badge size="small" status={row.action === "Blocked" ? "critical" : "warning"}>
                                {row.action}
                            </Badge>
                        )}
                    </HorizontalStack>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <EventActionsDropdown
                            violationId={row.id}
                            eventStatus={row._status}
                            onStatusUpdate={onStatusUpdate}
                            row={row}
                        />
                        <BlockIpsButton ip={row.ip || row.user} />
                        <Button plain icon={MobileCancelMajor} onClick={onClose} accessibilityLabel="Close" />
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
            <Divider />
        </>
    );
}

// ─── Flyout ─────────────────────────────────────────────────────────────────────

export default function ViolationFlyout({ violation, show, onClose, onStatusUpdate, allRows = [] }) {
    const [selectedTab, setSelectedTab] = useState(0);

    useEffect(() => { setSelectedTab(0); }, [violation?.id]);

    const detail = useMemo(() => {
        if (!violation) return null;
        return buildFallbackDetail(violation);
    }, [violation]);

    // Build timeline: occurrences of THIS specific violation (same actor + rule + evidence).
    // When multiple distinct skills/prompts trigger the same rule, narrow to just the
    // selected row's evidence so the timeline shows "this skill was flagged at these times"
    // rather than an interleaved list of all different skills.
    const timelineActivity = useMemo(() => {
        if (!violation) return [];
        const sameRuleRows = allRows.filter(
            r => r.user === violation.user && r.violation === violation.violation
        );
        const source = sameRuleRows.length > 0 ? sameRuleRows : [violation];
        const sorted = [...source].sort((a, b) => a.detected - b.detected);

        const uniqueEvidence = new Set(sorted.map(r => r.evidenceText || ""));

        // If there are multiple distinct evidences (e.g. different skills all triggering
        // malicious_skill_detected), scope the timeline to only this violation's specific
        // evidence so entries don't interleave unrelated violations.
        const relevantRows = uniqueEvidence.size > 1 && violation.evidenceText
            ? sorted.filter(r => r.evidenceText === violation.evidenceText)
            : sorted;

        return relevantRows
            .slice(-50)
            .map(r => ({
                description: r.violation || "Violation detected",
                timestamp: r.detected,
            }));
    }, [violation, allRows]);

    // Tabs: Overview · (type-specific middle tab) · Remediation · Timeline.
    const tabModel = useMemo(() => {
        const tabs = [{ id: "overview", content: "Overview" }];
        let middle = null;
        if (detail?.chatSession?.length) middle = "chat";
        else if (detail?.fileContent) middle = "file";
        if (middle === "chat") tabs.push({ id: "chat", content: "Chat Session" });
        if (middle === "file") tabs.push({ id: "file", content: detail.fileTabLabel || "File" });
        tabs.push({ id: "remediation", content: "Remediation" });
        tabs.push({ id: "timeline", content: "Timeline" });
        return { tabs, middle };
    }, [detail]);

    const handleTabSelect = useCallback((idx) => setSelectedTab(idx), []);

    if (!violation) return null;

    const activeId = tabModel.tabs[selectedTab]?.id;

    function renderTabContent(id) {
        switch (id) {
            case "overview":    return <OverviewSection row={violation} detail={detail} />;
            case "chat":        return <ChatSessionSection messages={detail?.chatSession} highlights={detail?.evidence?.highlights || []} />;
            case "file":        return <FileSection detail={detail} />;
            case "remediation": return <RemediationSection markdown={detail?.remediation} />;
            case "timeline":    return <ActivityTracker latestActivity={timelineActivity} />;
            default:            return null;
        }
    }

    return (
        <AgenticFlyoutShell
            show={show}
            width={840}
            header={
                <>
                    <FlyoutHeader row={violation} onClose={onClose} onStatusUpdate={onStatusUpdate} />
                    <Box paddingInlineStart="1" paddingInlineEnd="1">
                        <Tabs tabs={tabModel.tabs} selected={selectedTab} onSelect={handleTabSelect} />
                    </Box>
                    <Divider />
                </>
            }
            footer={
                <AiChatSection
                    placeholder="Ask anything related to your endpoints..."
                    resetKey={violation.id}
                    conversationType="AGENTIC_OBSERVE"
                />
            }
        >
            <Box style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
                {renderTabContent(activeId)}
            </Box>
        </AgenticFlyoutShell>
    );
}
