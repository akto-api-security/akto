import { useState } from "react";
import { ActionList, Box, Button, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import { IndexFiltersMode } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { IdentityIcon, violationsTableData, violationsHeaders, violationsSortOptions } from "./nhiViolationsData";

const NHI_VIOLATIONS_PATH = "/dashboard/nhi/violations";

export default function IdentityDetailsPanel({ row, show, setShow }) {
    const [actionActive, setActionActive] = useState(false);

    const identityViolations = violationsTableData.filter((r) => r.identity === row.identityName);
    const violCrit = identityViolations.filter((v) => v.severity === "Critical").length;
    const violHigh = identityViolations.filter((v) => v.severity === "High").length;
    const violMed  = identityViolations.filter((v) => v.severity === "Medium").length;
    const totalViolations = identityViolations.length;

    const handleViolationClick = (violationRow) => {
        sessionStorage.setItem("nhi_pending_violation", JSON.stringify(violationRow));
        setShow(false);
        window.location.href = NHI_VIOLATIONS_PATH;
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
                            <span key={bg} style={{
                                background: bg, color: fg,
                                borderRadius: "50%", width: 20, height: 20,
                                display: "inline-flex", alignItems: "center",
                                justifyContent: "center", fontSize: 11, fontWeight: 600, flexShrink: 0,
                            }}>{count}</span>
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
                    <ActionList items={[{ content: "Disable identity", destructive: true, onAction: () => setActionActive(false) }]} />
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
                <VerticalStack gap="3">
                    <Text variant="headingSm" color="subdued">Description</Text>
                    <Text variant="bodyMd">
                        {totalViolations > 0
                            ? `This identity is actively used by ${row.agent} with ${row.access.toLowerCase()}-level access via ${row.type}. It currently has ${totalViolations} security violation${totalViolations > 1 ? "s" : ""} that increase the risk of misuse or unauthorized access.`
                            : `This identity is actively used by ${row.agent} with ${row.access.toLowerCase()}-level access via ${row.type}. No active security violations detected.`
                        }
                    </Text>
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
