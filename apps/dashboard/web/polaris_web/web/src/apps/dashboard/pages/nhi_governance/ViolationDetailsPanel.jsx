import { useState } from "react";
import { ActionList, Badge, Box, Button, Divider, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import { IdentityIcon, AgentIcon, getViolationDetail, resolvePolicyName } from "./nhiViolationsData";
import func from "@/util/func";

export default function ViolationDetailsPanel({ row, show, setShow }) {
    const [actionActive, setActionActive] = useState(false);
    const detail = getViolationDetail(row.violation);

    // ── TitleComponent ────────────────────────────────────────────────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="start">
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" blockAlign="center" wrap>
                        <Text variant="headingMd" fontWeight="semibold">{row.violation}</Text>
                        <div className={`badge-wrapper-${row.severity.toUpperCase()}`}>
                            <Badge status={func.getHexColorForSeverity(row.severity.toUpperCase())}>{row.severity}</Badge>
                        </div>
                    </HorizontalStack>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                            <IdentityIcon name={row.identity} />
                            <Text variant="bodySm" color="subdued">{row.identity}</Text>
                        </div>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                            <AgentIcon name={row.agent} />
                            <Text variant="bodySm" color="subdued">{row.agent}</Text>
                        </div>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">Last Seen {row.discovered}</Text>
                    </div>
                </VerticalStack>
                <Popover
                    active={actionActive}
                    activator={
                        <Button size="slim" disclosure onClick={() => setActionActive((v) => !v)}>
                            Action
                        </Button>
                    }
                    onClose={() => setActionActive(false)}
                >
                    <ActionList items={[
                        { content: "Open Jira Ticket",  onAction: () => setActionActive(false) },
                        { content: "Mark as Fixed",     onAction: () => setActionActive(false) },
                        { content: "Update Policy",     onAction: () => setActionActive(false) },
                        { content: "Disable Identity",  destructive: true, onAction: () => setActionActive(false) },
                    ]} />
                </Popover>
            </HorizontalStack>
        </Box>
    );

    // ── Overview tab ──────────────────────────────────────────────────────────
    const overviewTab = {
        id: "overview",
        content: "Overview",
        component: (
            <Box padding="4">
                <VerticalStack gap="5">
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Description</Text>
                        <Text variant="bodyMd">{detail.description}</Text>
                    </VerticalStack>
                    <Divider />
                    <HorizontalStack gap="8" blockAlign="start" wrap>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Policy Triggered</Text>
                            <Text variant="bodyMd" fontWeight="semibold">
                                {resolvePolicyName(typeof row.policy === "object" ? row.policy.primary : row.policy)}
                            </Text>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Affected Resources</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{detail.affectedResources}</Text>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Discovered</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{row.discovered}</Text>
                        </VerticalStack>
                    </HorizontalStack>
                    <Divider />
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Why This Triggered</Text>
                        <Text variant="bodyMd">{detail.whyTriggered}</Text>
                    </VerticalStack>
                    <Divider />
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Blast Radius</Text>
                        <VerticalStack gap="2">
                            {detail.blastRadius.map((item, i) => (
                                <HorizontalStack key={i} gap="2" blockAlign="start" wrap={false}>
                                    <Text variant="bodyMd">•</Text>
                                    <Text variant="bodyMd">{item}</Text>
                                </HorizontalStack>
                            ))}
                        </VerticalStack>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Remediation tab ───────────────────────────────────────────────────────
    const remediationTab = {
        id: "remediation",
        content: "Remediation",
        component: (
            <Box padding="4">
                <VerticalStack gap="4">
                    <Text variant="headingSm" color="subdued">Steps to resolve</Text>
                    <VerticalStack gap="4">
                        {detail.remediationSteps.map((step, i) => (
                            <HorizontalStack key={i} gap="3" blockAlign="start" wrap={false}>
                                <span style={{
                                    background: "#F6F6F7", borderRadius: "50%",
                                    minWidth: 24, height: 24, display: "inline-flex",
                                    alignItems: "center", justifyContent: "center",
                                    fontSize: 12, fontWeight: 600, flexShrink: 0, color: "#202223",
                                }}>{i + 1}</span>
                                <Text variant="bodyMd">{step}</Text>
                            </HorizontalStack>
                        ))}
                    </VerticalStack>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Timeline tab ──────────────────────────────────────────────────────────
    const timelineTab = {
        id: "timeline",
        content: "Timeline",
        component: (
            <Box padding="5">
                <VerticalStack>
                    {detail.timeline.map((item, i) => (
                        <div key={i} style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                            <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
                                <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
                                    <img src="/public/issues-event-icon.svg" width={20} height={20} alt="" style={{ flexShrink: 0 }} />
                                    {i < detail.timeline.length - 1 && (
                                        <div style={{ width: 2, flex: 1, minHeight: 24, background: "var(--p-color-border-subdued, #E4E5E7)", margin: "4px 0" }} />
                                    )}
                                </div>
                                <div style={{ paddingTop: 1 }}>
                                    <Text variant="bodyMd">{item.event}</Text>
                                </div>
                            </div>
                            <div style={{ whiteSpace: "nowrap", paddingInlineStart: 16, paddingTop: 2 }}>
                                <Text variant="bodySm" color="subdued">{item.time}</Text>
                            </div>
                        </div>
                    ))}
                </VerticalStack>
            </Box>
        ),
    };

    return (
        <FlyLayout
            title="Violation details"
            show={show}
            setShow={setShow}
            components={[
                <TitleComponent key="title" />,
                <LayoutWithTabs
                    key={row.id}
                    tabs={[overviewTab, remediationTab, timelineTab]}
                    currTab={() => {}}
                    noLoading
                />,
            ]}
            showDivider
            newComp
        />
    );
}
