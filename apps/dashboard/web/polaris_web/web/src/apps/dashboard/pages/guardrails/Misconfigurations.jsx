import { useState, useEffect, useMemo, useRef } from "react";
import {
    Box,
    VerticalStack,
    HorizontalStack,
    Text,
    Button,
    Badge,
    Modal,
    TextField,
    Select,
    EmptySearchResult,
} from "@shopify/polaris";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import api from "./api";
import func from "@/util/func";

import { editor } from "monaco-editor/esm/vs/editor/editor.api";
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import "monaco-editor/esm/vs/language/json/monaco.contribution";

const truncate = (value, maxLen = 60) => {
    if (!value) return "-";
    return value.length > maxLen ? `${value.slice(0, maxLen)}...` : value;
};

const policyResourceName = { singular: "policy", plural: "policies" };

// Only "claude" is enabled server-side today (ConfigFieldPolicyAction.SUPPORTED_TOOL_NAMES) — extend both when adding a tool.
const TOOL_NAME_OPTIONS = [
    { label: "Claude", value: "claude" },
];

const policyHeaders = [
    { text: "Agent", value: "toolNameDisplay", title: "Agent", type: CellType.TEXT },
    { text: "Field Path", value: "fieldPath", title: "Field Path", type: CellType.TEXT },
    { text: "Enforced Value", value: "enforcedValuePreview", title: "Enforced Value", type: CellType.TEXT },
    { text: "Status", value: "statusComp", title: "Status" },
    { text: "Updated", value: "updatedTs", title: "Updated", type: CellType.TEXT },
];

function JsonValueEditor({ value, onChange }) {
    const containerRef = useRef(null);
    const editorInstanceRef = useRef(null);

    useEffect(() => {
        if (!containerRef.current || editorInstanceRef.current) return;
        const instance = editor.create(containerRef.current, {
            value: value || "",
            language: "json",
            minimap: { enabled: false },
            wordWrap: "on",
            automaticLayout: true,
            scrollBeyondLastLine: false,
        });
        instance.onDidChangeModelContent(() => onChange(instance.getValue()));
        editorInstanceRef.current = instance;
        return () => {
            instance.dispose();
            editorInstanceRef.current = null;
        };
    }, []);

    useEffect(() => {
        const instance = editorInstanceRef.current;
        if (instance && instance.getValue() !== value) {
            instance.setValue(value || "");
        }
    }, [value]);

    return (
        <Box
            ref={containerRef}
            minHeight="160px"
            borderColor="border"
            borderWidth="1"
            borderRadius="1"
            overflowY="hidden"
            overflowX="hidden"
        />
    );
}

const DEFAULT_SNIPPET = `{
  "availableModels": ["sonnet", "haiku"]
}`;

// Derives {fieldPath, enforcedValueJson} from a pasted JSON snippet that must
// have exactly one top-level key — that key is the field path to pin, its
// value is what gets enforced. Returns null (with an error) if the snippet
// isn't a single-key JSON object.
function deriveFieldFromSnippet(snippetText) {
    let parsed;
    try {
        parsed = JSON.parse(snippetText);
    } catch (error) {
        return { error: "Must be valid JSON" };
    }
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
        return { error: "Must be a JSON object with one field, e.g. {\"availableModels\": [...]}" };
    }
    const keys = Object.keys(parsed);
    if (keys.length !== 1) {
        return { error: "Must contain exactly one field to pin" };
    }
    const fieldPath = keys[0];
    return { fieldPath, enforcedValueJson: JSON.stringify(parsed[fieldPath]) };
}

function PolicyFormModal({ open, onClose, editingPolicy, onSaved }) {
    const [policyName, setPolicyName] = useState("");
    const [toolName, setToolName] = useState(TOOL_NAME_OPTIONS[0].value);
    const [snippet, setSnippet] = useState(DEFAULT_SNIPPET);
    const [jsonError, setJsonError] = useState("");
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!open) return;
        if (editingPolicy) {
            setPolicyName(editingPolicy.policyName || "");
            setToolName(editingPolicy.toolName || TOOL_NAME_OPTIONS[0].value);
            let value = editingPolicy.enforcedValueJson || "null";
            try {
                value = JSON.parse(editingPolicy.enforcedValueJson);
            } catch (error) {
                value = editingPolicy.enforcedValueJson;
            }
            setSnippet(JSON.stringify({ [editingPolicy.fieldPath]: value }, null, 2));
        } else {
            setPolicyName("");
            setToolName(TOOL_NAME_OPTIONS[0].value);
            setSnippet(DEFAULT_SNIPPET);
        }
        setJsonError("");
    }, [open, editingPolicy]);

    const validateSnippet = (value) => {
        const result = deriveFieldFromSnippet(value);
        if (result.error) {
            setJsonError(result.error);
            return null;
        }
        setJsonError("");
        return result;
    };

    const handleSave = async () => {
        const derived = validateSnippet(snippet);
        if (!derived) {
            func.setToast(true, true, "Fix the JSON before saving");
            return;
        }

        const effectiveName = policyName.trim().length > 0 ? policyName : `Pin: ${derived.fieldPath}`;

        const payload = {
            policyName: effectiveName,
            description: "",
            status: editingPolicy?.status || "ACTIVE",
            toolName,
            fieldPath: derived.fieldPath,
            enforcedValueJson: derived.enforcedValueJson,
            devices: [],
        };

        setSaving(true);
        try {
            await api.createConfigFieldPolicy(payload, editingPolicy?.hexId || null);
            func.setToast(true, false, editingPolicy ? "Policy updated" : "Policy created");
            onSaved();
        } catch (error) {
            func.setToast(true, true, "Failed to save policy");
        } finally {
            setSaving(false);
        }
    };

    const toolLabel = TOOL_NAME_OPTIONS.find((t) => t.value === toolName)?.label || toolName;

    return (
        <Modal
            open={open}
            onClose={onClose}
            title={editingPolicy ? `Edit ${toolLabel} Config Policy` : `New ${toolLabel} Config Policy`}
            primaryAction={{ content: "Save", onAction: handleSave, loading: saving }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <TextField
                        label="Policy name"
                        value={policyName}
                        onChange={setPolicyName}
                        placeholder="Optional — auto-generated from the field below"
                        autoComplete="off"
                    />
                    <Select
                        label="Tool"
                        options={TOOL_NAME_OPTIONS}
                        value={toolName}
                        onChange={setToolName}
                    />
                    <VerticalStack gap="1">
                        <Text variant="bodyMd" as="p">{toolLabel} config field to pin</Text>
                        <Text variant="bodySm" color="subdued">
                            Paste a single field exactly as it should appear in {toolLabel}'s settings.json. It's created automatically on the device if missing, and any local edit gets reverted back to this value.
                        </Text>
                        <JsonValueEditor value={snippet} onChange={(v) => { setSnippet(v); if (jsonError) validateSnippet(v); }} />
                        {jsonError && <Text variant="bodySm" color="critical">{jsonError}</Text>}
                    </VerticalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}

function Misconfigurations() {
    const [policies, setPolicies] = useState([]);
    const [loading, setLoading] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [editingPolicy, setEditingPolicy] = useState(null);
    // GithubSimpleTable caches its rows internally and only re-derives them
    // when its own key changes — bump this after every mutation so
    // Activate/Deactivate/Edit (which don't change row count) still refresh.
    const [refreshCounter, setRefreshCounter] = useState(0);

    const fetchPolicies = async () => {
        setLoading(true);
        try {
            const resp = await api.fetchConfigFieldPolicies();
            setPolicies(resp?.configFieldPolicies || []);
            setRefreshCounter((v) => v + 1);
        } catch (error) {
            func.setToast(true, true, "Failed to load misconfiguration policies");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPolicies();
    }, []);

    const tablePolicies = useMemo(() => policies.map((p) => ({
        id: p.hexId,
        toolNameDisplay: TOOL_NAME_OPTIONS.find((t) => t.value === p.toolName)?.label || p.toolName || "-",
        fieldPath: p.fieldPath,
        enforcedValuePreview: truncate(p.enforcedValueJson),
        statusComp: (
            <Badge status={p.status === "ACTIVE" ? "success" : "subdued"}>
                {p.status === "ACTIVE" ? "Active" : "Inactive"}
            </Badge>
        ),
        updatedTs: func.prettifyEpoch(p.updatedAt),
    })), [policies]);

    const setPolicyStatus = async (selectedIds, status) => {
        try {
            await Promise.all(selectedIds.map((id) => {
                const rawPolicy = policies.find((p) => p.hexId === id);
                if (!rawPolicy) return Promise.resolve();
                return api.createConfigFieldPolicy({ ...rawPolicy, status }, id);
            }));
            func.setToast(true, false, status === "ACTIVE" ? "Activated" : "Deactivated");
            await fetchPolicies();
        } catch (error) {
            func.setToast(true, true, "Failed to update status");
        }
    };

    const promotedBulkActions = (selectedIds) => {
        const actions = [];

        if (selectedIds.length === 1) {
            actions.push({
                content: "Edit",
                onAction: () => {
                    const rawPolicy = policies.find((p) => p.hexId === selectedIds[0]);
                    setEditingPolicy(rawPolicy || null);
                    setShowCreateModal(true);
                },
            });
        }

        actions.push({
            content: "Activate",
            onAction: () => setPolicyStatus(selectedIds, "ACTIVE"),
        });
        actions.push({
            content: "Deactivate",
            onAction: () => setPolicyStatus(selectedIds, "INACTIVE"),
        });

        actions.push({
            content: `Delete ${selectedIds.length} polic${selectedIds.length > 1 ? "ies" : "y"}`,
            onAction: async () => {
                try {
                    await api.deleteConfigFieldPolicies(selectedIds);
                    func.setToast(true, false, "Deleted successfully");
                    await fetchPolicies();
                } catch (error) {
                    func.setToast(true, true, "Failed to delete policies");
                }
            },
        });

        return actions;
    };

    const handleModalClose = () => {
        setShowCreateModal(false);
        setEditingPolicy(null);
    };

    const handleSaved = async () => {
        setShowCreateModal(false);
        setEditingPolicy(null);
        await fetchPolicies();
    };

    const emptyPoliciesMarkup = (
        <EmptySearchResult
            title="No config field policies found"
            description="Pin a field in a tool's settings.json so local edits get auto-reverted"
            withIllustration
        />
    );

    const components = [
        <Box key="policies-header" paddingBlockEnd="4">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text variant="headingMd">Config Field Policies</Text>
                <Button primary onClick={() => { setEditingPolicy(null); setShowCreateModal(true); }}>
                    New Policy
                </Button>
            </HorizontalStack>
        </Box>,
        <GithubSimpleTable
            key={`policies-table-${refreshCounter}`}
            resourceName={policyResourceName}
            useNewRow={true}
            headers={policyHeaders}
            headings={policyHeaders}
            data={tablePolicies}
            loading={loading}
            loadingText="Loading misconfiguration policies..."
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            emptyStateMarkup={emptyPoliciesMarkup}
            filterStateUrl="/dashboard/guardrails/misconfigurations/"
        />,
    ];

    return (
        <>
            <PageWithMultipleCards
                title="Misconfigurations"
                isFirstPage={true}
                components={components}
            />
            <PolicyFormModal
                open={showCreateModal}
                onClose={handleModalClose}
                editingPolicy={editingPolicy}
                onSaved={handleSaved}
            />
        </>
    );
}

export default Misconfigurations;
