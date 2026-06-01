import { useState, useEffect, useCallback } from "react";
import {
    VerticalStack,
    HorizontalStack,
    Text,
    Button,
    TextField,
    Select,
    Badge,
    Modal,
    Box,
    EmptySearchResult,
    Checkbox,
} from "@shopify/polaris";
import { DeleteMajor, EditMajor, CancelSmallMinor, PlusMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import func from "@/util/func";
import api from "../../guardrails/api";
import ShowListInBadge from "../../../components/shared/ShowListInBadge";

const resourceName = {
    singular: "config",
    plural: "configs",
};

const ruleResourceName = {
    singular: "blocked website",
    plural: "blocked websites",
};

const headings = [
    { text: "Host", value: "host", title: "Host" },
    { text: "Paths", value: "pathsComp", title: "Paths" },
    { text: "Status", value: "statusComp", title: "Status" },
    { text: "Created", value: "createdTs", title: "Created", type: CellType.TEXT, sortActive: true },
    { text: "Updated", value: "updatedTs", title: "Updated", type: CellType.TEXT },
    { text: "Updated by", value: "updatedBy", title: "Updated by", type: CellType.TEXT },
    { title: "", type: CellType.ACTION },
];

const ruleHeadings = [
    { text: "Match type", value: "matchTypeComp", title: "Match type" },
    { text: "Pattern", value: "patternComp", title: "Pattern" },
    { text: "Effect", value: "effect", title: "Effect", type: CellType.TEXT },
    { text: "Action", value: "actionComp", title: "Action" },
    { text: "Status", value: "statusComp", title: "Status" },
    { text: "Updated", value: "updatedTs", title: "Updated", type: CellType.TEXT },
    { title: "", type: CellType.ACTION },
];

const sortOptions = [
    { label: "Created", value: "createdTs asc", directionLabel: "Newest", sortKey: "createdTs", columnIndex: 3 },
    { label: "Created", value: "createdTs desc", directionLabel: "Oldest", sortKey: "createdTs", columnIndex: 3 },
];

const MATCH_TYPE_OPTIONS = [
    { label: "Domain (+ all subdomains)", value: "DOMAIN" },
    { label: "Exact host only", value: "HOST" },
    { label: "URL pattern (wildcards)", value: "URL_PATTERN" },
    { label: "Regex", value: "REGEX" },
];

const MATCH_TYPE_LABEL = {
    DOMAIN: "Domain",
    HOST: "Exact host",
    URL_PATTERN: "URL pattern",
    REGEX: "Regex",
};

const ACTION_OPTIONS = [
    { label: "Block", value: "block" },
    { label: "Allow (exception)", value: "allow" },
];

const PLACEHOLDER_BY_TYPE = {
    DOMAIN: "e.g. deepseek.com",
    HOST: "e.g. chat.deepseek.com",
    URL_PATTERN: "e.g. chatgpt.com/g/*",
    REGEX: "e.g. ^https?://([^/]+\\.)?deepseek\\.",
};

// Plain-English description of what a rule does (shared by the table and the modal preview).
function describeRule(matchType, value, action) {
    const verb = action === "allow" ? "Allows" : "Blocks";
    const v = value || "…";
    switch (matchType) {
        case "DOMAIN": return `${verb} ${v} + all subdomains`;
        case "HOST": return `${verb} ${v} only (other subdomains unaffected)`;
        case "URL_PATTERN": return `${verb} URLs matching ${v}`;
        case "REGEX": return `${verb} URLs matching this regex`;
        default: return "";
    }
}

// Light client-side validation; the server is authoritative.
function validateRuleValue(matchType, value) {
    const v = (value || "").trim();
    if (!v) return "Value is required";
    if (matchType === "DOMAIN" || matchType === "HOST") {
        if (v.includes(" ") || !v.includes(".")) return "Enter a valid domain or host, e.g. deepseek.com";
    } else if (matchType === "URL_PATTERN") {
        if (v === "*" || v.includes(" ")) return "Enter a valid URL pattern";
    } else if (matchType === "REGEX") {
        try { new RegExp(v); } catch (e) { return "Invalid regular expression"; }
    }
    return null;
}

function BrowserExtensionSettings() {
    const [configs, setConfigs] = useState([]);
    const [blockRules, setBlockRules] = useState([]);
    const [loading, setLoading] = useState(false);

    // monitor config modal state
    const [showModal, setShowModal] = useState(false);
    const [editingConfig, setEditingConfig] = useState(null);
    const [host, setHost] = useState("");
    const [pathInput, setPathInput] = useState("");
    const [paths, setPaths] = useState([]);
    const [isActive, setIsActive] = useState(true);
    const [saving, setSaving] = useState(false);

    // block rule modal state
    const [showRuleModal, setShowRuleModal] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [ruleMatchType, setRuleMatchType] = useState("DOMAIN");
    const [ruleValue, setRuleValue] = useState("");
    const [ruleAction, setRuleAction] = useState("block");
    const [ruleActive, setRuleActive] = useState(true);
    const [ruleDescription, setRuleDescription] = useState("");
    const [savingRule, setSavingRule] = useState(false);

    useEffect(() => {
        fetchConfigs();
    }, []);

    const fetchConfigs = async () => {
        setLoading(true);
        try {
            const response = await api.fetchBrowserExtensionConfigs();
            const all = (response && response.browserExtensionConfigs) || [];

            const monitorConfigs = all.filter(c => (c.type || "MONITOR") !== "BLOCK");
            const rules = all.filter(c => c.type === "BLOCK");

            setConfigs(monitorConfigs.map(config => ({
                id: config.hexId,
                host: config.host,
                paths: config.paths || [],
                pathsComp: (
                    <ShowListInBadge itemsArr={config.paths || []} maxItems={3} status="info" useTooltip={true} />
                ),
                statusComp: (
                    <Badge tone={config.active ? "success" : undefined} size="small">
                        {config.active ? "Active" : "Inactive"}
                    </Badge>
                ),
                status: config.active ? "Active" : "Inactive",
                isActive: config.active,
                createdTs: func.prettifyEpoch(config.createdTimestamp),
                updatedTs: func.prettifyEpoch(config.updatedTimestamp),
                updatedBy: config.updatedBy || "-",
                originalData: config,
            })));

            setBlockRules(rules.map(rule => ({
                id: rule.hexId,
                matchTypeComp: <Badge size="small">{MATCH_TYPE_LABEL[rule.matchType] || rule.matchType}</Badge>,
                patternComp: <Text variant="bodyMd" fontWeight="medium"><code>{rule.value}</code></Text>,
                effect: describeRule(rule.matchType, rule.value, rule.action),
                actionComp: (
                    <Badge tone={rule.action === "allow" ? "success" : "critical"} size="small">
                        {rule.action === "allow" ? "Allow" : "Block"}
                    </Badge>
                ),
                statusComp: (
                    <Badge tone={rule.active ? "success" : undefined} size="small">
                        {rule.active ? "Active" : "Inactive"}
                    </Badge>
                ),
                updatedTs: func.prettifyEpoch(rule.updatedTimestamp),
                originalData: rule,
            })));
        } catch (error) {
            func.setToast(true, true, "Failed to load browser extension configs");
        } finally {
            setLoading(false);
        }
    };

    // ---------------- monitor config handlers ----------------

    const resetForm = useCallback(() => {
        setHost("");
        setPathInput("");
        setPaths([]);
        setIsActive(true);
        setEditingConfig(null);
    }, []);

    const openCreateModal = () => {
        resetForm();
        setShowModal(true);
    };

    const openEditModal = (item) => {
        const config = item.originalData;
        setEditingConfig(config);
        setHost(config.host || "");
        setPaths(config.paths || []);
        setIsActive(config.active);
        setPathInput("");
        setShowModal(true);
    };

    const handleAddPath = () => {
        const trimmed = pathInput.trim();
        if (trimmed && !paths.includes(trimmed)) {
            setPaths([...paths, trimmed]);
            setPathInput("");
        }
    };

    const handleRemovePath = (pathToRemove) => {
        setPaths(paths.filter(p => p !== pathToRemove));
    };

    const handlePathKeyPress = (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            handleAddPath();
        }
    };

    const handleSave = async () => {
        if (!host.trim()) {
            func.setToast(true, true, "Host is required");
            return;
        }
        if (paths.length === 0) {
            func.setToast(true, true, "At least one path is required");
            return;
        }

        setSaving(true);
        try {
            const configData = {
                type: "MONITOR",
                host: host.trim(),
                paths,
                active: isActive,
            };
            const hexId = editingConfig ? editingConfig.hexId : null;
            await api.saveBrowserExtensionConfig(configData, hexId);
            func.setToast(true, false, editingConfig ? "Config updated successfully" : "Config created successfully");
            setShowModal(false);
            resetForm();
            await fetchConfigs();
        } catch (error) {
            func.setToast(true, true, "Failed to save config");
        } finally {
            setSaving(false);
        }
    };

    // ---------------- block rule handlers ----------------

    const resetRuleForm = useCallback(() => {
        setRuleMatchType("DOMAIN");
        setRuleValue("");
        setRuleAction("block");
        setRuleActive(true);
        setRuleDescription("");
        setEditingRule(null);
    }, []);

    const openCreateRuleModal = () => {
        resetRuleForm();
        setShowRuleModal(true);
    };

    const openEditRuleModal = (item) => {
        const rule = item.originalData;
        setEditingRule(rule);
        setRuleMatchType(rule.matchType || "DOMAIN");
        setRuleValue(rule.value || "");
        setRuleAction(rule.action || "block");
        setRuleActive(rule.active);
        setRuleDescription(rule.description || "");
        setShowRuleModal(true);
    };

    const handleSaveRule = async () => {
        const validationError = validateRuleValue(ruleMatchType, ruleValue);
        if (validationError) {
            func.setToast(true, true, validationError);
            return;
        }

        setSavingRule(true);
        try {
            const ruleData = {
                type: "BLOCK",
                matchType: ruleMatchType,
                value: ruleValue.trim(),
                action: ruleAction,
                active: ruleActive,
                description: ruleDescription,
            };
            const hexId = editingRule ? editingRule.hexId : null;
            await api.saveBrowserExtensionConfig(ruleData, hexId);
            func.setToast(true, false, editingRule ? "Rule updated successfully" : "Rule created successfully");
            setShowRuleModal(false);
            resetRuleForm();
            await fetchConfigs();
        } catch (error) {
            func.setToast(true, true, "Failed to save rule");
        } finally {
            setSavingRule(false);
        }
    };

    // ---------------- shared bulk/row actions ----------------

    const promotedBulkActions = (selectedItems) => ([
        {
            content: `Delete ${selectedItems.length} config${selectedItems.length > 1 ? "s" : ""}`,
            onAction: () => func.showConfirmationModal(
                `Are you sure you want to delete ${selectedItems.length} config${selectedItems.length > 1 ? "s" : ""}?`,
                "Delete",
                async () => {
                    try {
                        await api.deleteBrowserExtensionConfigs(selectedItems);
                        func.setToast(true, false, `${selectedItems.length} config${selectedItems.length > 1 ? "s" : ""} deleted`);
                        await fetchConfigs();
                    } catch (error) {
                        func.setToast(true, true, "Failed to delete configs");
                    }
                }
            ),
        },
    ]);

    const promotedBulkRuleActions = (selectedItems) => ([
        {
            content: `Delete ${selectedItems.length} rule${selectedItems.length > 1 ? "s" : ""}`,
            onAction: () => func.showConfirmationModal(
                `Are you sure you want to delete ${selectedItems.length} rule${selectedItems.length > 1 ? "s" : ""}?`,
                "Delete",
                async () => {
                    try {
                        await api.deleteBrowserExtensionConfigs(selectedItems);
                        func.setToast(true, false, `${selectedItems.length} rule${selectedItems.length > 1 ? "s" : ""} deleted`);
                        await fetchConfigs();
                    } catch (error) {
                        func.setToast(true, true, "Failed to delete rules");
                    }
                }
            ),
        },
    ]);

    const getActionsList = (item) => ([{
        title: "Actions",
        items: [
            { content: "Edit", icon: EditMajor, onAction: () => openEditModal(item) },
            {
                content: "Delete",
                icon: DeleteMajor,
                destructive: true,
                onAction: () => func.showConfirmationModal(
                    `Delete config for host "${item.host}"?`,
                    "Delete",
                    async () => {
                        try {
                            await api.deleteBrowserExtensionConfigs([item.id]);
                            func.setToast(true, false, "Config deleted");
                            await fetchConfigs();
                        } catch (error) {
                            func.setToast(true, true, "Failed to delete config");
                        }
                    }
                ),
            },
        ],
    }]);

    const getRuleActionsList = (item) => ([{
        title: "Actions",
        items: [
            { content: "Edit", icon: EditMajor, onAction: () => openEditRuleModal(item) },
            {
                content: "Delete",
                icon: DeleteMajor,
                destructive: true,
                onAction: () => func.showConfirmationModal(
                    `Delete this blocked website rule?`,
                    "Delete",
                    async () => {
                        try {
                            await api.deleteBrowserExtensionConfigs([item.id]);
                            func.setToast(true, false, "Rule deleted");
                            await fetchConfigs();
                        } catch (error) {
                            func.setToast(true, true, "Failed to delete rule");
                        }
                    }
                ),
            },
        ],
    }]);

    const emptyStateMarkup = (
        <EmptySearchResult title="No browser extension configs found" withIllustration />
    );

    const emptyRuleStateMarkup = (
        <EmptySearchResult title="No blocked websites configured" withIllustration />
    );

    const configTable = (
        <GithubSimpleTable
            key={`ext-configs-${configs.length}`}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={configs}
            hideQueryField={true}
            hidePagination={true}
            showFooter={false}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}
            onRowClick={openEditModal}
            rowClickable={true}
            getActions={getActionsList}
            hasRowActions={true}
            hardCodedKey={true}
            loading={loading}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
        />
    );

    const blockRulesSection = (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="center">
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="h2">Blocked websites</Text>
                    <Text variant="bodySm" tone="subdued">
                        Block entire domains, single subdomains, URL patterns, or regex matches. Allow rules act as exceptions to broader blocks.
                    </Text>
                </VerticalStack>
                <Button icon={PlusMinor} onClick={openCreateRuleModal}>Add blocked website</Button>
            </HorizontalStack>
            <GithubSimpleTable
                key={`ext-rules-${blockRules.length}`}
                resourceName={ruleResourceName}
                useNewRow={true}
                headers={ruleHeadings}
                headings={ruleHeadings}
                data={blockRules}
                hideQueryField={true}
                hidePagination={true}
                showFooter={false}
                emptyStateMarkup={emptyRuleStateMarkup}
                onRowClick={openEditRuleModal}
                rowClickable={true}
                getActions={getRuleActionsList}
                hasRowActions={true}
                hardCodedKey={true}
                loading={loading}
                selectable={true}
                promotedBulkActions={promotedBulkRuleActions}
            />
        </VerticalStack>
    );

    const modal = (
        <Modal
            open={showModal}
            onClose={() => { setShowModal(false); resetForm(); }}
            title={editingConfig ? "Edit Browser Extension Config" : "Add Browser Extension Config"}
            primaryAction={{ content: editingConfig ? "Update" : "Save", onAction: handleSave, loading: saving }}
            secondaryActions={[{ content: "Cancel", onAction: () => { setShowModal(false); resetForm(); } }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <TextField
                        label="Host"
                        value={host}
                        onChange={setHost}
                        placeholder="e.g. chat.openai.com"
                        autoComplete="off"
                        helpText="The website hostname the browser extension should monitor."
                    />
                    <VerticalStack gap="2">
                        <TextField
                            label="Paths"
                            value={pathInput}
                            onChange={setPathInput}
                            placeholder="e.g. /api/v1/chat"
                            autoComplete="off"
                            onKeyDown={handlePathKeyPress}
                            helpText="Press Enter or click Add to add a path. Wildcards supported (e.g. /api/*)."
                            connectedRight={
                                <Button icon={PlusMinor} onClick={handleAddPath} disabled={!pathInput.trim()} accessibilityLabel="Add path" />
                            }
                        />
                        {paths.length > 0 && (
                            <VerticalStack gap="1">
                                {paths.map((p, i) => (
                                    <Box key={i} padding="2" paddingInline="3" borderWidth="1" borderColor="border" borderRadius="2" background="bg-surface">
                                        <HorizontalStack align="space-between" blockAlign="center">
                                            <Text variant="bodyMd">{p}</Text>
                                            <Button plain icon={CancelSmallMinor} onClick={() => handleRemovePath(p)} accessibilityLabel={`Remove ${p}`} />
                                        </HorizontalStack>
                                    </Box>
                                ))}
                            </VerticalStack>
                        )}
                    </VerticalStack>
                    <Checkbox
                        label="Active"
                        checked={isActive}
                        onChange={setIsActive}
                        helpText="When enabled, the browser extension will apply guardrails on matching requests."
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );

    const ruleValueError = ruleValue ? validateRuleValue(ruleMatchType, ruleValue) : null;

    const ruleModal = (
        <Modal
            open={showRuleModal}
            onClose={() => { setShowRuleModal(false); resetRuleForm(); }}
            title={editingRule ? "Edit blocked website" : "Add blocked website"}
            primaryAction={{ content: editingRule ? "Update" : "Save", onAction: handleSaveRule, loading: savingRule, disabled: !!ruleValueError || !ruleValue.trim() }}
            secondaryActions={[{ content: "Cancel", onAction: () => { setShowRuleModal(false); resetRuleForm(); } }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Select
                        label="Match type"
                        options={MATCH_TYPE_OPTIONS}
                        value={ruleMatchType}
                        onChange={setRuleMatchType}
                        helpText="Domain blocks the site and every subdomain. Exact host blocks only that subdomain."
                    />
                    <TextField
                        label={ruleMatchType === "REGEX" ? "Regular expression" : (ruleMatchType === "URL_PATTERN" ? "URL pattern" : "Domain / host")}
                        value={ruleValue}
                        onChange={setRuleValue}
                        placeholder={PLACEHOLDER_BY_TYPE[ruleMatchType]}
                        autoComplete="off"
                        error={ruleValueError || undefined}
                        monospaced={ruleMatchType === "REGEX" || ruleMatchType === "URL_PATTERN"}
                    />
                    <Select
                        label="Action"
                        options={ACTION_OPTIONS}
                        value={ruleAction}
                        onChange={setRuleAction}
                        helpText="Allow rules override broader block rules (e.g. block deepseek.com but allow api.deepseek.com)."
                    />
                    {ruleValue.trim() && !ruleValueError && (
                        <Box padding="3" borderWidth="1" borderColor="border" borderRadius="2" background="bg-surface-secondary">
                            <HorizontalStack gap="2" blockAlign="center">
                                <Badge tone={ruleAction === "allow" ? "success" : "critical"} size="small">
                                    {ruleAction === "allow" ? "Allow" : "Block"}
                                </Badge>
                                <Text variant="bodyMd">{describeRule(ruleMatchType, ruleValue.trim(), ruleAction)}</Text>
                            </HorizontalStack>
                        </Box>
                    )}
                    <TextField
                        label="Description (optional)"
                        value={ruleDescription}
                        onChange={setRuleDescription}
                        placeholder="Why is this blocked?"
                        autoComplete="off"
                        multiline={2}
                    />
                    <Checkbox
                        label="Active"
                        checked={ruleActive}
                        onChange={setRuleActive}
                        helpText="When enabled, the browser extension enforces this rule."
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );

    return (
        <>
            <PageWithMultipleCards
                title={"Browser Extension"}
                isFirstPage={true}
                primaryAction={<Button primary onClick={openCreateModal}>Add Config</Button>}
                components={[configTable, blockRulesSection]}
            />
            {modal}
            {ruleModal}
        </>
    );
}

export default BrowserExtensionSettings;
