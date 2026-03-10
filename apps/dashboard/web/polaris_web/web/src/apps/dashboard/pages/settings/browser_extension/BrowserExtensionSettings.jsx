import { useState, useEffect, useCallback } from "react";
import {
    VerticalStack,
    HorizontalStack,
    Text,
    Button,
    TextField,
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

const headings = [
    { text: "Host", value: "host", title: "Host" },
    { text: "Paths", value: "pathsComp", title: "Paths" },
    { text: "Status", value: "statusComp", title: "Status" },
    { text: "Created", value: "createdTs", title: "Created", type: CellType.TEXT, sortActive: true },
    { text: "Updated", value: "updatedTs", title: "Updated", type: CellType.TEXT },
    { text: "Updated by", value: "updatedBy", title: "Updated by", type: CellType.TEXT },
    { title: "", type: CellType.ACTION },
];

const sortOptions = [
    { label: "Created", value: "createdTs asc", directionLabel: "Newest", sortKey: "createdTs", columnIndex: 3 },
    { label: "Created", value: "createdTs desc", directionLabel: "Oldest", sortKey: "createdTs", columnIndex: 3 },
];

function BrowserExtensionSettings() {
    const [configs, setConfigs] = useState([]);
    const [loading, setLoading] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [editingConfig, setEditingConfig] = useState(null);

    const [host, setHost] = useState("");
    const [pathInput, setPathInput] = useState("");
    const [paths, setPaths] = useState([]);
    const [isActive, setIsActive] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        fetchConfigs();
    }, []);

    const fetchConfigs = async () => {
        setLoading(true);
        try {
            const response = await api.fetchBrowserExtensionConfigs();
            if (response && response.browserExtensionConfigs) {
                const formatted = response.browserExtensionConfigs.map(config => ({
                    id: config.hexId,
                    host: config.host,
                    paths: config.paths || [],
                    pathsComp: (
                        <ShowListInBadge
                            itemsArr={config.paths || []}
                            maxItems={3}
                            status="info"
                            useTooltip={true}
                        />
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
                }));
                setConfigs(formatted);
            }
        } catch (error) {
            func.setToast(true, true, "Failed to load browser extension configs");
        } finally {
            setLoading(false);
        }
    };

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

    const promotedBulkActions = (selectedItems) => {
        return [
            {
                content: `Delete ${selectedItems.length} config${selectedItems.length > 1 ? "s" : ""}`,
                onAction: async () => {
                    func.showConfirmationModal(
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
                    );
                },
            },
        ];
    };

    const getActionsList = (item) => {
        return [{
            title: "Actions",
            items: [
                {
                    content: "Edit",
                    icon: EditMajor,
                    onAction: () => openEditModal(item),
                },
                {
                    content: "Delete",
                    icon: DeleteMajor,
                    onAction: () => {
                        func.showConfirmationModal(
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
                        );
                    },
                    destructive: true,
                },
            ],
        }];
    };

    const emptyStateMarkup = (
        <EmptySearchResult title="No browser extension configs found" withIllustration />
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

    const modal = (
        <Modal
            open={showModal}
            onClose={() => { setShowModal(false); resetForm(); }}
            title={editingConfig ? "Edit Browser Extension Config" : "Add Browser Extension Config"}
            primaryAction={{
                content: editingConfig ? "Update" : "Save",
                onAction: handleSave,
                loading: saving,
            }}
            secondaryActions={[{
                content: "Cancel",
                onAction: () => { setShowModal(false); resetForm(); },
            }]}
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

    return (
        <>
            <PageWithMultipleCards
                title={"Browser Extension"}
                isFirstPage={true}
                primaryAction={<Button primary onClick={openCreateModal}>Add Config</Button>}
                components={[configTable]}
            />
            {modal}
        </>
    );
}

export default BrowserExtensionSettings;
