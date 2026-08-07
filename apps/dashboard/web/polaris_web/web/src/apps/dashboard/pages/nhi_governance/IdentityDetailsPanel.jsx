import { useState, useEffect, useMemo } from "react";
import { ActionList, Box, Button, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import { IndexFiltersMode } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { IdentityIcon, violationsHeaders, violationsSortOptions, transformApiViolations } from "./nhiViolationsData";
import IdentityGraph from "./IdentityGraph";
import observeRequests from "../observe/api";
import func from "@/util/func";

const NHI_VIOLATIONS_PATH = "/dashboard/nhi/violations";
// A single identity's violations are bounded by usage against that one credential, unlike the
// account-wide violations stream — one page comfortably covers virtually every identity.
const IDENTITY_VIOLATIONS_LIMIT = 200;

export default function IdentityDetailsPanel({ row, show, setShow, onUpdated }) {
    const [actionActive, setActionActive] = useState(false);
    const [rawViolations, setRawViolations] = useState([]);
    const [total, setTotal] = useState(0);
    const [severityCounts, setSeverityCounts] = useState({});
    const [loading, setLoading] = useState(true);
    const [disabling, setDisabling] = useState(false);

    // Fetch violations scoped to THIS identity only (server-side filter), instead of pulling every
    // violation account-wide with no time bound and filtering client-side on every panel open.
    useEffect(() => {
        const fetchViolations = async () => {
            try {
                setLoading(true);
                const { violations, total: totalCount, stats } = await observeRequests.fetchViolationsByIdentity(
                    row.hexId, { limit: IDENTITY_VIOLATIONS_LIMIT }
                );
                setRawViolations(Array.isArray(violations) ? violations : []);
                setTotal(totalCount || 0);
                setSeverityCounts(stats?.bySeverityOpen || {});
            } catch (err) {
                console.error("Error fetching violations:", err);
                setRawViolations([]);
                setTotal(0);
                setSeverityCounts({});
            } finally {
                setLoading(false);
            }
        };

        if (show && row?.hexId) {
            fetchViolations();
        }
    }, [show, row?.hexId]);

    const identityViolations = useMemo(() => transformApiViolations(rawViolations), [rawViolations]);
    const violCrit = severityCounts.Critical || 0;
    const violHigh = severityCounts.High || 0;
    const violMed  = severityCounts.Medium || 0;
    const totalViolations = total;

    const handleViolationClick = (violationRow) => {
        sessionStorage.setItem("nhi_pending_violation", JSON.stringify(violationRow));
        setShow(false);
        window.location.href = NHI_VIOLATIONS_PATH;
    };

    const handleDisableIdentity = async () => {
        try {
            setDisabling(true);

            await observeRequests.disableNhiIdentity(row.hexId);

            func.setToast(true, false, "Identity disabled successfully");
            setActionActive(false);
            setShow(false);
            await onUpdated?.();
        } catch (err) {
            func.setToast(true, true, "Failed to disable identity");
        } finally {
            setDisabling(false);
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
        content: `Violations ${total > 0 ? total : ""}`.trim(),
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
