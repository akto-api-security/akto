import { useState, useEffect, useMemo } from "react";
import { ActionList, Box, Button, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import { IndexFiltersMode } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { IdentityIcon, violationsHeaders, violationsSortOptions, SEV_ORD, sevBadge, AgentIcon, PolicyCell } from "./nhiViolationsData";
import IdentityGraph from "./IdentityGraph";
import observeRequests from "../observe/api";
import { extractIdentityName, violationIncludesIdentity, getFirstIdentityName } from "./identityHelper";

const NHI_VIOLATIONS_PATH = "/dashboard/nhi/violations";

// Format timestamp to relative time (e.g., "2h ago")
const formatTimestampRelative = (timestamp) => {
    if (!timestamp) return "Unknown";
    const now = Math.floor(Date.now() / 1000);
    const diff = now - timestamp;
    const minutes = Math.floor(diff / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    return "Now";
};

// Transform API violations to table format with components
const transformViolationsForUI = (apiViolations) => {
    return apiViolations
        .sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity])
        .map((v, i) => {
            // Transform policy array to object format
            const policyObj = v.policy && Array.isArray(v.policy)
                ? {
                    primary: v.policy[0] || "N/A",
                    extra: Math.max(0, v.policy.length - 1),
                    extras: v.policy.slice(1) || [],
                  }
                : v.policy;

            // Extract identity name from new format { id: ObjectId, identityName: "name" }
            const firstIdentityName = getFirstIdentityName(v.identities);

            return {
                ...v,
                id: i + 1,
                violation: v.violationType,
                identity: firstIdentityName,
                identities: v.identities,
                discovered: formatTimestampRelative(v.discoveredAt),
                severityOrder: SEV_ORD[v.severity] || 0,
                policy: policyObj,
                violationComp: <Text variant="bodyMd" fontWeight="medium">{v.violationType}</Text>,
                identityComp: (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <IdentityIcon name={firstIdentityName} />
                        <Text variant="bodyMd">{firstIdentityName}</Text>
                    </HorizontalStack>
                ),
                agentComp: (
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <AgentIcon name={v.agentName} />
                        <Text variant="bodyMd">{v.agentName}</Text>
                    </HorizontalStack>
                ),
                severityComp: sevBadge(v.severity),
                policyComp: <PolicyCell policy={policyObj} />,
            };
        });
};

export default function IdentityDetailsPanel({ row, show, setShow }) {
    const [actionActive, setActionActive] = useState(false);
    const [allViolations, setAllViolations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [disabling, setDisabling] = useState(false);

    // Fetch all violations for filtering
    useEffect(() => {
        const fetchViolations = async () => {
            try {
                setLoading(true);
                const response = await observeRequests.fetchAllNhiViolations();

                if (Array.isArray(response)) {
                    setAllViolations(response);
                } else {
                    setAllViolations([]);
                }
            } catch (err) {
                console.error("Error fetching violations:", err);
                setAllViolations([]);
            } finally {
                setLoading(false);
            }
        };

        if (show) {
            fetchViolations();
        }
    }, [show]);

    // Transform violations and filter for this identity
    const identityViolations = useMemo(() => {
        const transformed = transformViolationsForUI(allViolations);
        return transformed.filter((v) => violationIncludesIdentity(v.identities, row.identityName));
    }, [allViolations, row.identityName]);
    const violCrit = identityViolations.filter((v) => v.severity === "Critical").length;
    const violHigh = identityViolations.filter((v) => v.severity === "High").length;
    const violMed  = identityViolations.filter((v) => v.severity === "Medium").length;
    const totalViolations = identityViolations.length;

    const handleViolationClick = (violationRow) => {
        sessionStorage.setItem("nhi_pending_violation", JSON.stringify(violationRow));
        setShow(false);
        window.location.href = NHI_VIOLATIONS_PATH;
    };

    const handleDisableIdentity = async () => {
        try {
            setDisabling(true);

            await observeRequests.disableNhiIdentity(row.hexId);

            setDisabling(false);
            setActionActive(false);
            setShow(false);

            // Refresh the identities list
            window.location.reload();
        } catch (err) {
            console.error("Error disabling identity:", err);
            setDisabling(false);
            setActionActive(false);
        }
    };

    // ── TitleComponent ────────────────────────────────────────────────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="start">
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" blockAlign="center" align="start">
                        <IdentityIcon name={row.identityName} />
                        <Text variant="headingMd" fontWeight="semibold">{row.identityName}</Text>
                        {[
                            { count: violCrit, bg: "#DF2909", fg: "white"   },
                            { count: violHigh, bg: "#FED3D1", fg: "#202223" },
                            { count: violMed,  bg: "#FFD79D", fg: "#202223" },
                        ].map(({ count, bg, fg }) => count > 0 && (
                            <Box key={bg} style={{
                                background: bg, color: fg,
                                borderRadius: "50%", width: 20, height: 20,
                                display: "flex", alignItems: "center",
                                justifyContent: "center", fontSize: 11, fontWeight: 600, flexShrink: 0,
                            }}>{count}</Box>
                        ))}
                    </HorizontalStack>
                    <HorizontalStack gap="2">
                        <Text variant="bodySm" color="subdued">{row.type}</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">{row.access} Access</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">Last Used {row.lastUsed}</Text>
                    </HorizontalStack>
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
                    <ActionList items={[{ content: "Disable identity", destructive: true, onAction: handleDisableIdentity }]} />
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
                <VerticalStack gap="4">
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Graph</Text>
                        <IdentityGraph row={row} />
                    </VerticalStack>
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Description</Text>
                        <Text variant="bodyMd">
                            {(() => {
                                const access = row.access ? `${row.access.toLowerCase()}-level access ` : "";
                                const via = row.type ? `via ${row.type}` : "";
                                const suffix = totalViolations > 0
                                    ? `It currently has ${totalViolations} security violation${totalViolations > 1 ? "s" : ""} that increase the risk of misuse or unauthorized access.`
                                    : "No active security violations detected.";
                                return `This identity is actively used by ${row.agent || "an unknown agent"} with ${access}${via}. ${suffix}`.replace(/\s+/g, " ").trim();
                            })()}
                        </Text>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Violations tab ────────────────────────────────────────────────────────
    const violationsTab = {
        id: "violations",
        content: `Violations ${identityViolations.length > 0 ? identityViolations.length : ""}`.trim(),
        component: identityViolations.length > 0 ? (
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="4">
                <GithubSimpleTable
                    data={identityViolations}
                    headers={violationsHeaders}
                    resourceName={{ singular: "violation", plural: "violations" }}
                    sortOptions={violationsSortOptions}
                    filters={[]}
                    selectable={false}
                    mode={IndexFiltersMode.Default}
                    headings={violationsHeaders}
                    useNewRow={true}
                    condensedHeight={true}
                    onRowClick={handleViolationClick}
                    rowClickable={true}
                />
            </Box>
        ) : (
            <Box padding="4">
                <Text variant="bodyMd" color="subdued">No violations found for this identity.</Text>
            </Box>
        ),
    };

    return (
        <FlyLayout
            title="Identity details"
            show={show}
            setShow={setShow}
            components={[
                <TitleComponent key="title" />,
                <LayoutWithTabs
                    key={row.identityName}
                    tabs={[overviewTab, violationsTab]}
                    currTab={() => {}}
                    noLoading
                />,
            ]}
            showDivider
            newComp
        />
    );
}
