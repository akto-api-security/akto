import { useRef, useState } from "react";
import { ActionList, Badge, Box, Button, Divider, HorizontalStack, Popover, Text, VerticalStack } from "@shopify/polaris";
import { IndexFiltersMode } from "@shopify/polaris";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import SampleData from "../../components/shared/SampleData";
import { AgentIcon, violationsTableData, violationsHeaders, violationsSortOptions, unresolvedPolicyName } from "./nhiViolationsData";
import { POLICY_YAML_MAP } from "./nhiData";

const NHI_VIOLATIONS_PATH = "/dashboard/nhi/violations";
const STATUS_COLOR = { Active: "success", Inactive: "", Draft: "warning" };

// ── Exported textarea-based code editor (used in Create Policy modal) ─────────
export function CodeEditor({ value, onChange, minHeight = 300 }) {
    const lineNumRef = useRef(null);
    const lines = (value || "").split("\n");
    return (
        <Box style={{
            display: "flex",
            fontFamily: "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace",
            fontSize: 13, border: "1px solid #E4E5E7", borderRadius: 8,
            overflow: "hidden", minHeight,
        }}>
            <Box ref={lineNumRef} style={{
                background: "#F6F6F7", color: "#8C9196",
                padding: "12px 8px 12px 4px", textAlign: "right",
                userSelect: "none", overflowY: "hidden",
                lineHeight: "19.5px", minWidth: 36,
                borderRight: "1px solid #E4E5E7", flexShrink: 0,
            }}>
                {lines.map((_, i) => <Box key={i}>{i + 1}</Box>)}
            </Box>
            <textarea
                value={value}
                onChange={onChange ? (e) => onChange(e.target.value) : undefined}
                readOnly={!onChange}
                onScroll={(e) => { if (lineNumRef.current) lineNumRef.current.scrollTop = e.target.scrollTop; }}
                style={{
                    flex: 1, padding: "12px 12px 12px 8px",
                    border: "none", outline: "none", resize: "none",
                    fontFamily: "inherit", fontSize: 13,
                    lineHeight: "19.5px", background: "white",
                    color: "#202223", overflowY: "auto",
                }}
                spellCheck={false}
            />
        </Box>
    );
}

export default function PolicyDetailsPanel({ row, show, setShow, onSave }) {
    const [actionActive, setActionActive] = useState(false);
    const [isDirty, setIsDirty]           = useState(false);

    // Frozen at mount time via lazy initializer — never changes even if row.policyName
    // is updated after a save, so SampleData never gets a new data prop and won't reset
    // the editor or lose the cursor position.
    // row.yaml is the persisted YAML from localStorage (set on previous saves).
    const [initialYaml] = useState(
        () => row.yaml
            || POLICY_YAML_MAP[row.policyName]
            || `id: pol-xxx\nname: ${row.policyName}\nstatus: ${row.status.toLowerCase()}\n`
    );

    // yamlRef tracks live edits without triggering re-renders
    const yamlRef     = useRef(initialYaml);
    // savedYamlRef is the baseline for dirty detection — updates on every save
    const savedYamlRef = useRef(initialYaml);

    // Resolve fresh every render — never stale, picks up any rename immediately.
    // unresolvedPolicyName("New Name") → looks up nhi_policy_name_map → returns original key
    // that violations still reference.
    const originalPolicyName = unresolvedPolicyName(row.policyName);

    const policyViolations = violationsTableData.filter((v) => {
        const names = typeof v.policy === "object"
            ? [v.policy.primary, ...(v.policy.extras || [])]
            : [v.policy];
        return names.some((n) => n === originalPolicyName || n === row.policyName);
    });
    const violCrit = policyViolations.filter((v) => v.severity === "Critical").length;
    const violHigh = policyViolations.filter((v) => v.severity === "High").length;
    const violMed  = policyViolations.filter((v) => v.severity === "Medium").length;
    const violLow  = policyViolations.filter((v) => v.severity === "Low").length;

    const handleViolationClick = (violationRow) => {
        sessionStorage.setItem("nhi_pending_violation", JSON.stringify(violationRow));
        setShow(false);
        window.location.href = NHI_VIOLATIONS_PATH;
    };

    const handleSave = () => {
        const yaml = yamlRef.current;
        const nameMatch = yaml.match(/^name:\s*(.+)$/m);
        const newName = nameMatch ? nameMatch[1].trim() : row.policyName;
        savedYamlRef.current = yaml;   // update baseline so next edit compares against saved state
        setIsDirty(false);
        onSave && onSave({ policyName: newName, yaml });
    };

    const scopeLabel = row.agents && row.agents.length > 1
        ? `${row.agents[0]} +${row.agents.length - 1}`
        : row.agents && row.agents.length === 1
            ? row.agents[0]
            : "All Agents";

    // ── TitleComponent ────────────────────────────────────────────────────────
    const TitleComponent = () => (
        <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
            <VerticalStack gap="1">
                <HorizontalStack align="space-between" blockAlign="start">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text as="span" variant="headingMd" fontWeight="semibold">{row.policyName}</Text>
                        <Badge status={STATUS_COLOR[row.status] || ""}>{row.status}</Badge>
                        {[
                            { count: violCrit, bg: "#DF2909", fg: "white"   },
                            { count: violHigh, bg: "#FED3D1", fg: "#202223" },
                            { count: violMed,  bg: "#FFD79D", fg: "#202223" },
                            { count: violLow,  bg: "#E4E5E7", fg: "#202223" },
                        ].map(({ count, bg, fg }) => count > 0 && (
                            <Box key={bg} style={{
                                background: bg, color: fg,
                                borderRadius: "50%", width: 20, height: 20,
                                display: "flex", alignItems: "center",
                                justifyContent: "center", fontSize: 11, fontWeight: 600, flexShrink: 0,
                            }}>{count}</Box>
                        ))}
                    </HorizontalStack>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Button size="slim" disabled={!isDirty} onClick={handleSave}>Save</Button>
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
                                ...(row.status === "Active"
                                    ? [{ content: "Mark as Inactive", onAction: () => setActionActive(false) }]
                                    : [{ content: "Mark as Active",   onAction: () => setActionActive(false) }]
                                ),
                                { content: "Delete Policy", destructive: true, onAction: () => setActionActive(false) },
                            ]} />
                        </Popover>
                    </HorizontalStack>
                </HorizontalStack>
                <HorizontalStack gap="2">
                    <Text variant="bodySm" color="subdued">{scopeLabel}</Text>
                    <Text variant="bodySm" color="subdued">|</Text>
                    <Text variant="bodySm" color="subdued">Last Triggered {row.lastTriggered}</Text>
                    <Text variant="bodySm" color="subdued">|</Text>
                    <Text variant="bodySm" color="subdued">Last Modified by {row.lastModified}</Text>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    );

    // ── Policy (YAML) tab — primary ───────────────────────────────────────────
    const policyTab = {
        id: "policy",
        content: "Policy",
        component: (
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="2" paddingBlockEnd="4">
                <Box style={{ height: "calc(100vh - 260px)", minHeight: 300 }}>
                    <SampleData
                        data={{ message: initialYaml }}
                        editorLanguage="custom_yaml"
                        readOnly={false}
                        getEditorData={(val) => {
                            yamlRef.current = val;
                            setIsDirty(val !== savedYamlRef.current);
                        }}
                        minHeight="calc(100vh - 260px)"
                    />
                </Box>
            </Box>
        ),
    };

    // ── Overview tab ──────────────────────────────────────────────────────────
    const overviewTab = {
        id: "overview",
        content: "Overview",
        component: (
            <Box padding="4">
                <VerticalStack gap="5">
                    <HorizontalStack gap="8" blockAlign="start" wrap>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Status</Text>
                            <Badge status={STATUS_COLOR[row.status] || ""}>{row.status}</Badge>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Total Violations</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{policyViolations.length}</Text>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Last Triggered</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{row.lastTriggered}</Text>
                        </VerticalStack>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" color="subdued">Created</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{row.created}</Text>
                        </VerticalStack>
                    </HorizontalStack>
                    <Divider />
                    <VerticalStack gap="2">
                        <Text variant="headingSm" color="subdued">Agents in scope</Text>
                        <VerticalStack gap="2">
                            {(row.agents || []).map((agent) => (
                                <HorizontalStack key={agent} gap="2" blockAlign="center">
                                    {agent !== "All Agents" && <AgentIcon name={agent} />}
                                    <Text variant="bodyMd">{agent}</Text>
                                </HorizontalStack>
                            ))}
                        </VerticalStack>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        ),
    };

    // ── Violations tab ────────────────────────────────────────────────────────
    const violationsTab = {
        id: "violations",
        content: `Violations${policyViolations.length > 0 ? ` ${policyViolations.length}` : ""}`,
        component: policyViolations.length > 0 ? (
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="4">
                <GithubSimpleTable
                    data={policyViolations}
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
                <Text variant="bodyMd" color="subdued">No violations triggered by this policy.</Text>
            </Box>
        ),
    };

    return (
        <FlyLayout
            title="Policy details"
            show={show}
            setShow={setShow}
            components={[
                <TitleComponent key="title" />,
                <LayoutWithTabs
                    key={row.id}
                    tabs={[policyTab, overviewTab, violationsTab]}
                    currTab={() => {}}
                    noLoading
                />,
            ]}
            showDivider
            newComp
        />
    );
}
