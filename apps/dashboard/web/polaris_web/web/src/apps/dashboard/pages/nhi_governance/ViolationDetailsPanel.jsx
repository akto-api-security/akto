import { useState } from "react";
import { ActionList, Badge, Box, Button, Divider, HorizontalStack, Link, Popover, Text, VerticalStack } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import { IdentityIcon, AgentIcon, resolvePolicyName } from "./nhiViolationsData";
import func from "@/util/func";
import observeRequests from "../observe/api";
import Store from "../../store";
import JiraTicketCreationModal from "../../components/shared/JiraTicketCreationModal.jsx";
import issuesFunctions from "@/apps/dashboard/pages/issues/module";
import settingFunctions from "@/apps/dashboard/pages/settings/module";

export default function ViolationDetailsPanel({ row, show, setShow }) {
    const [actionActive, setActionActive] = useState(false);
    const [marking, setMarking] = useState(false);
    const [jiraModalActive, setJiraModalActive] = useState(false);
    const [projId, setProjId] = useState("");
    const [issueType, setIssueType] = useState("");
    const [labelsText, setLabelsText] = useState("");
    const [jiraProjectMap, setJiraProjectMap] = useState({});
    const userEmail = Store((state) => state.username);
    const handleMarkAsFixed = async () => {
        try {
            if (!userEmail) return;

            setMarking(true);

            await observeRequests.markViolationAsFixed(row.id, userEmail);

            setMarking(false);
            setActionActive(false);
            setShow(false);

            // Refresh the violations list
            window.location.reload();
        } catch (err) {
            console.error("Error marking violation as fixed:", err);
            setMarking(false);
            setActionActive(false);
        }
    };

    const handleOpenJiraModal = () => {
        setActionActive(false);
        settingFunctions.fetchJiraIntegration().then((jiraIntegration) => {
            if (jiraIntegration.projectIdsMap !== null && Object.keys(jiraIntegration.projectIdsMap).length > 0) {
                setJiraProjectMap(jiraIntegration.projectIdsMap);
                if (Object.keys(jiraIntegration.projectIdsMap).length > 0) {
                    setProjId(Object.keys(jiraIntegration.projectIdsMap)[0]);
                }
            } else {
                setProjId(jiraIntegration.projId);
                setIssueType(jiraIntegration.issueType);
            }
            setJiraModalActive(true);
        });
    };

    const handleSaveJiraAction = (issueId, labels) => {
        let jiraMetaData;
        try {
            jiraMetaData = issuesFunctions.prepareAdditionalIssueFieldsJiraMetaData(projId, issueType);
            // Use labels parameter if provided
            if (labels !== undefined && labels && labels.trim()) {
                jiraMetaData.labels = labels.trim();
            }
        } catch (error) {
            return;
        }

        setJiraModalActive(false);
        observeRequests.createJiraTicketFromViolation(row.id, window.location.origin, projId, issueType, jiraMetaData).then((res) => {
            if (!res?.errorMessage) {
                setShow(false);
                window.location.reload();
            }
        }).catch(() => {});
    };

    // ── TitleComponent ────────────────────────────────────────────────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="start">
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" blockAlign="center" wrap>
                        <Text variant="headingMd" fontWeight="semibold">{row.violation}</Text>
                        <Box className={`badge-wrapper-${row.severity.toUpperCase()}`}>
                            <Badge status={func.getHexColorForSeverity(row.severity.toUpperCase())}>{row.severity}</Badge>
                        </Box>
                    </HorizontalStack>
                    <Box style={{ alignSelf: "flex-start" }}>
                        <HorizontalStack gap="2" blockAlign="center">
                            <Box style={{ display: "flex", alignItems: "center", gap: 4 }}>
                                <IdentityIcon name={row.identity} />
                                <Text variant="bodySm" color="subdued">{row.identity}</Text>
                            </Box>
                            <Text variant="bodySm" color="subdued">|</Text>
                            <Box style={{ display: "flex", alignItems: "center", gap: 4 }}>
                                <AgentIcon name={row.agent} />
                                <Text variant="bodySm" color="subdued">{row.agent}</Text>
                            </Box>
                            <Text variant="bodySm" color="subdued">|</Text>
                            <Text variant="bodySm" color="subdued">Last Seen {row.discovered}</Text>
                        </HorizontalStack>
                    </Box>
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
                        { content: "Open Jira Ticket",  onAction: handleOpenJiraModal },
                        { content: "Mark as Fixed",     onAction: handleMarkAsFixed },
                        {
                            content: "Update Policy",
                            onAction: () => {
                                setActionActive(false);
                                const policyName = typeof row.policy === "object"
                                    ? row.policy.primary
                                    : row.policy;
                                if (policyName) {
                                    sessionStorage.setItem("nhi_policy_edit_name", policyName);
                                }
                                window.location.href = "/dashboard/nhi/policies";
                            },
                        },
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
                        <Text variant="bodyMd">{row.description}</Text>
                    </VerticalStack>
                    <Divider />
                    <HorizontalStack gap="8">
                        <Box style={{ alignSelf: "flex-start" }}>
                            <VerticalStack gap="1">
                                <Text variant="headingSm" color="subdued">
                                    {(() => {
                                        const extraCount = typeof row.policy === "object" ? (row.policy.extra || 0) : 0;
                                        return extraCount > 0 ? `Policies Triggered (${1 + extraCount})` : "Policy Triggered";
                                    })()}
                                </Text>
                                <VerticalStack gap="1">
                                    {[
                                        typeof row.policy === "object" ? row.policy.primary : row.policy,
                                        ...(typeof row.policy === "object" ? (row.policy.extras || []) : []),
                                    ].map((name) => {
                                        const resolved = resolvePolicyName(name);
                                        return (
                                            <Link
                                                key={name}
                                                url="/dashboard/nhi/policies"
                                                onClick={() => sessionStorage.setItem("nhi_pending_policy", resolved)}
                                            >
                                                {resolved}
                                            </Link>
                                        );
                                    })}
                                </VerticalStack>
                            </VerticalStack>
                        </Box>
                        <Box style={{ alignSelf: "flex-start" }}>
                            <VerticalStack gap="1">
                                <Text variant="headingSm" color="subdued">Affected Resources</Text>
                                <Text variant="bodyMd" fontWeight="semibold">{(row.affectedResources || []).join(", ")}</Text>
                            </VerticalStack>
                        </Box>
                        <Box style={{ alignSelf: "flex-start" }}>
                            <VerticalStack gap="1">
                                <Text variant="headingSm" color="subdued">Discovered</Text>
                                <Text variant="bodyMd" fontWeight="semibold">{row.discovered}</Text>
                            </VerticalStack>
                        </Box>
                    </HorizontalStack>
                    <Divider />
                    {row.whyTriggered && (
                        <>
                            <VerticalStack gap="2">
                                <Text variant="headingSm" color="subdued">Why This Triggered</Text>
                                <Text variant="bodyMd">{row.whyTriggered}</Text>
                            </VerticalStack>
                            <Divider />
                        </>
                    )}
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Blast Radius</Text>
                        <VerticalStack gap="2">
                            {(row.blastRadius || []).map((item, i) => (
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
                        {(row.remediationSteps || []).map((step, i) => (
                            <HorizontalStack key={i} gap="3" blockAlign="start" wrap={false}>
                                <Box style={{
                                    background: "#F6F6F7", borderRadius: "50%",
                                    minWidth: 24, height: 24, display: "flex",
                                    alignItems: "center", justifyContent: "center",
                                    fontSize: 12, fontWeight: 600, flexShrink: 0, color: "#202223",
                                }}>{i + 1}</Box>
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
                    {(row.timeline || []).map((item, i) => (
                        <HorizontalStack key={i} align="space-between" blockAlign="start" wrap={false}>
                            <HorizontalStack gap="3" blockAlign="start" wrap={false}>
                                <Box style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
                                    <img src="/public/issues-event-icon.svg" width={20} height={20} alt="" style={{ flexShrink: 0 }} />
                                    {i < (row.timeline || []).length - 1 && (
                                        <Box style={{ width: 2, flex: 1, minHeight: 24, background: "var(--p-color-border-subdued, #E4E5E7)", margin: "4px 0" }} />
                                    )}
                                </Box>
                                <Box paddingBlockStart="05">
                                    <Text variant="bodyMd">{item.event}</Text>
                                </Box>
                            </HorizontalStack>
                            <Box paddingInlineStart="4" paddingBlockStart="05" style={{ whiteSpace: "nowrap" }}>
                                <Text variant="bodySm" color="subdued">
                                    {item.timestamp ? new Date(item.timestamp * 1000).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : ""}
                                </Text>
                            </Box>
                        </HorizontalStack>
                    ))}
                </VerticalStack>
            </Box>
        ),
    };

    return (
        <>
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
            <JiraTicketCreationModal
                activator={<div />}
                modalActive={jiraModalActive}
                setModalActive={setJiraModalActive}
                handleSaveAction={handleSaveJiraAction}
                jiraProjectMaps={jiraProjectMap}
                setProjId={setProjId}
                setIssueType={setIssueType}
                projId={projId}
                issueType={issueType}
                issueId={row.id}
                labelsText={labelsText}
                setLabelsText={setLabelsText}
            />
        </>
    );
}
