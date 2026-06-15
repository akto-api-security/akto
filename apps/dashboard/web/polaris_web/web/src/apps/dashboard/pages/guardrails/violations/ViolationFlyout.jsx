import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
    ActionList,
    Badge,
    Box,
    Button,
    Divider,
    HorizontalStack,
    Popover,
    Tabs,
    Text,
} from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";

import AgenticFlyoutShell from "@/apps/dashboard/pages/observe/agentic/AgenticFlyoutShell";
import AiChatSection from "@/apps/dashboard/pages/observe/agentic/AiChatSection";
import { SeverityBadge } from "@/apps/dashboard/pages/observe/agentic/AgenticCellRenderers";
import JiraTicketCreationModal from "@/apps/dashboard/components/shared/JiraTicketCreationModal";
import func from "@/util/func";

import {
    ChatSessionSection,
    FileSection,
    OverviewSection,
    RemediationSection,
} from "./ViolationFlyoutSections";
import { VIOLATION_DETAILS, buildFallbackDetail } from "./violationsData";
import "../../../components/layouts/style.css";

// ─── Action dropdown ──────────────────────────────────────────────────────────

function ActionDropdown({ violationId }) {
    const [open, setOpen] = useState(false);
    const [jiraModalActive, setJiraModalActive] = useState(false);

    const otherItems = [
        { content: "Edit Policy",           onAction: () => { setOpen(false); func.setToast(true, false, "Edit Policy — coming soon"); } },
        { content: "Mark As False Positive", onAction: () => { setOpen(false); func.setToast(true, false, "Mark As False Positive — coming soon"); } },
    ];

    return (
        <>
            <JiraTicketCreationModal
                activator={<></>}
                modalActive={jiraModalActive}
                setModalActive={setJiraModalActive}
                handleSaveAction={() => func.setToast(true, false, "Jira integration coming soon")}
                jiraProjectMaps={[]}
                projId=""
                setProjId={() => {}}
                issueType=""
                setIssueType={() => {}}
                issueId={violationId}
            />
            <Popover
                active={open}
                onClose={() => setOpen(false)}
                activator={<Button size="slim" disclosure onClick={() => setOpen((p) => !p)}>Action</Button>}
                autofocusTarget="none"
                preferredAlignment="right"
            >
                <Box minWidth="220px">
                    <ActionList
                        actionRole="menuitem"
                        items={[
                            {
                                content: "Create Jira Ticket",
                                onAction: () => { setOpen(false); setJiraModalActive(true); },
                                disabled: window.JIRA_INTEGRATED !== "true",
                            },
                            ...otherItems,
                        ]}
                    />
                </Box>
            </Popover>
        </>
    );
}

// ─── Header ─────────────────────────────────────────────────────────────────────

function FlyoutHeader({ row, onClose }) {
    return (
        <>
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="3" paddingBlockEnd="3">
                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="headingMd" fontWeight="semibold">{row.violation}</Text>
                        <SeverityBadge severity={row.severity} />
                        <Badge size="small" status={row.action === "Blocked" ? "critical" : undefined}>{row.action}</Badge>
                    </HorizontalStack>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <ActionDropdown violationId={row.id} />
                        <Button plain icon={MobileCancelMajor} onClick={onClose} accessibilityLabel="Close" />
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
            <Divider />
        </>
    );
}

// ─── Flyout ─────────────────────────────────────────────────────────────────────

export default function ViolationFlyout({ violation, show, onClose }) {
    const [selectedTab, setSelectedTab] = useState(0);

    useEffect(() => { setSelectedTab(0); }, [violation?.id]);

    const detail = useMemo(() => {
        if (!violation) return null;
        return VIOLATION_DETAILS[violation.id] || buildFallbackDetail(violation);
    }, [violation]);

    // Tabs: Overview · (type-specific middle tab) · Remediation. The middle tab is
    // "Chat Session" for prompts, the file tab for skill/config, omitted otherwise.
    const tabModel = useMemo(() => {
        const tabs = [{ id: "overview", content: "Overview" }];
        let middle = null;
        if (detail?.chatSession?.length) middle = "chat";
        else if (detail?.fileContent) middle = "file";
        if (middle === "chat") tabs.push({ id: "chat", content: "Chat Session" });
        if (middle === "file") tabs.push({ id: "file", content: detail.fileTabLabel || "File" });
        tabs.push({ id: "remediation", content: "Remediation" });
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
            default:            return null;
        }
    }

    return (
        <AgenticFlyoutShell
            show={show}
            width={840}
            header={
                <>
                    <FlyoutHeader row={violation} onClose={onClose} />
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
