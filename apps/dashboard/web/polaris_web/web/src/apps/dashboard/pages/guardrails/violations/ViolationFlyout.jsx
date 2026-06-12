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

const ACTION_ITEMS = [
    { content: "Create Jira Ticket" },
    { content: "Edit Policy" },
    { content: "Mark As False Positive" },
];

function ActionDropdown() {
    const [open, setOpen] = useState(false);
    const items = ACTION_ITEMS.map((a) => ({
        content: a.content,
        onAction: () => {
            setOpen(false);
            func.setToast(true, false, `${a.content} — coming soon`);
        },
    }));
    return (
        <Popover
            active={open}
            onClose={() => setOpen(false)}
            activator={<Button size="slim" disclosure onClick={() => setOpen((p) => !p)}>Action</Button>}
            autofocusTarget="none"
            preferredAlignment="right"
        >
            <Box minWidth="220px">
                <ActionList actionRole="menuitem" items={items} />
            </Box>
        </Popover>
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
                        <ActionDropdown />
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
                {activeId === "overview" && <OverviewSection row={violation} detail={detail} />}
                {activeId === "chat" && <ChatSessionSection messages={detail?.chatSession} highlights={detail?.evidence?.highlights || []} />}
                {activeId === "file" && <FileSection detail={detail} />}
                {activeId === "remediation" && <RemediationSection markdown={detail?.remediation} />}
            </Box>
        </AgenticFlyoutShell>
    );
}
