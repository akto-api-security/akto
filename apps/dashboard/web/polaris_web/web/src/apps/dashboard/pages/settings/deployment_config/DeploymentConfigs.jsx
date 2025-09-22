import { useCallback, useMemo, useState, useEffect } from "react";
import { 
    Page, 
    Card, 
    Button, 
    Text, 
    IndexTable, 
    Badge, 
    Modal, 
    TextField, 
    HorizontalStack, 
    VerticalStack,
    EmptyState,
    Scrollable,
    ButtonGroup,
    Tooltip,
    Box
} from "@shopify/polaris";
import { 
    EditMajor,
    DeleteMinor, 
    SettingsMajor, 
    RefreshMajor
} from "@shopify/polaris-icons";
import dummyData from "./dummy_data";
import deploymentConfigApi from "./api";

const DeploymentConfigs = () => {
    const [deployments, setDeployments] = useState(dummyData.initialDeployments);
    const [selectedDeploymentId, setSelectedDeploymentId] = useState(null);
    const [isAddEnvOpen, setIsAddEnvOpen] = useState(false);
    const [isEditEnvOpen, setIsEditEnvOpen] = useState(false);
    const [isManageEnvOpen, setIsManageEnvOpen] = useState(false);
    const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
    const [deletingKey, setDeletingKey] = useState("");
    const [editingEnvKey, setEditingEnvKey] = useState("");
    const [newKey, setNewKey] = useState("");
    const [newValue, setNewValue] = useState("");

    const selectedDeployment = useMemo(() => deployments.find(d => d.id === selectedDeploymentId) || null, [deployments, selectedDeploymentId]);

    // Fetch deployment configs on component mount
    useEffect(() => {
        fetchDeploymentConfigs();
    }, []);

    const fetchDeploymentConfigs = async () => {
        try {
            const response = await deploymentConfigApi.fetchAllDeploymentConfigs();
            if (response && response.deploymentConfigs) {
                setDeployments(response.deploymentConfigs);
            }
        } catch (error) {
            console.error('Failed to fetch deployment configs:', error);
            // Fallback to dummy data on error
            setDeployments(dummyData.initialDeployments);
        }
    };

    const toggleAddModal = useCallback((deploymentId = null) => {
        setSelectedDeploymentId(deploymentId);
        setIsAddEnvOpen(prev => !prev);
        setNewKey("");
        setNewValue("");
    }, []);

    // retained for reference; opening edit is handled by openEditFromManage

    const openManageModal = useCallback((deploymentId) => {
        setSelectedDeploymentId(deploymentId);
        setIsManageEnvOpen(true);
    }, []);
    const closeManageModal = useCallback(() => setIsManageEnvOpen(false), []);

    const openEditFromManage = useCallback((deploymentId, envKey, envValue) => {
        setIsManageEnvOpen(false);
        setSelectedDeploymentId(deploymentId);
        setEditingEnvKey(envKey);
        setNewKey(envKey);
        setNewValue(envValue);
        setIsEditEnvOpen(true);
    }, []);

    const openDeleteConfirm = useCallback((envKey) => {
        setDeletingKey(envKey);
        setIsDeleteConfirmOpen(true);
    }, []);
    const closeDeleteConfirm = useCallback(() => {
        setDeletingKey("");
        setIsDeleteConfirmOpen(false);
    }, []);
    const confirmDelete = useCallback(async () => {
        if (!selectedDeployment || !deletingKey) return;
        
        try {
            await deploymentConfigApi.removeEnvVariable(selectedDeployment.id, deletingKey);
            
            // Refresh the deployments list
            await fetchDeploymentConfigs();
            
            closeDeleteConfirm();
        } catch (error) {
            console.error('Failed to delete environment variable:', error);
            // Fallback to local state update
            setDeployments(prev => prev.map(dep => dep.id === selectedDeployment.id ? ({...dep, envVars: dep.envVars.filter(e => e.key !== deletingKey)}) : dep));
            closeDeleteConfirm();
        }
    }, [selectedDeployment, deletingKey, closeDeleteConfirm, fetchDeploymentConfigs]);

    const handleAddEnv = useCallback(async () => {
        if (!selectedDeployment || !newKey.trim()) return;
        
        try {
            const exists = selectedDeployment.envVars.some(e => e.key === newKey);
            if (exists) {
                // Update existing env variable
                await deploymentConfigApi.updateEnvVariable(selectedDeployment.id, newKey, newValue);
            } else {
                // Add new env variable
                await deploymentConfigApi.addEnvVariable(selectedDeployment.id, newKey, newValue, true);
            }
            
            // Refresh the deployments list
            await fetchDeploymentConfigs();
            
            setIsAddEnvOpen(false);
            setNewKey("");
            setNewValue("");
        } catch (error) {
            console.error('Failed to add/update environment variable:', error);
            // Fallback to local state update
            const updated = deployments.map(dep => {
                if (dep.id !== selectedDeployment.id) return dep;
                const exists = dep.envVars.some(e => e.key === newKey);
                const envVars = exists ? dep.envVars.map(e => e.key === newKey ? ({...e, value: newValue}) : e) : [...dep.envVars, { key: newKey, value: newValue, editable: true }];
                return { ...dep, envVars };
            });
            setDeployments(updated);
            setIsAddEnvOpen(false);
            setNewKey("");
            setNewValue("");
        }
    }, [deployments, selectedDeployment, newKey, newValue, fetchDeploymentConfigs]);

    const handleEditEnv = useCallback(async () => {
        if (!selectedDeployment || !editingEnvKey) return;
        
        try {
            await deploymentConfigApi.updateEnvVariable(selectedDeployment.id, editingEnvKey, newValue);
            
            // Refresh the deployments list
            await fetchDeploymentConfigs();
            
            setIsEditEnvOpen(false);
            setEditingEnvKey("");
            setNewKey("");
            setNewValue("");
        } catch (error) {
            console.error('Failed to edit environment variable:', error);
            // Fallback to local state update
            const updated = deployments.map(dep => {
                if (dep.id !== selectedDeployment.id) return dep;
                return {
                    ...dep, 
                    envVars: dep.envVars.map(e => e.key === editingEnvKey ? ({...e, value: newValue}) : e)
                };
            });
            setDeployments(updated);
            setIsEditEnvOpen(false);
            setEditingEnvKey("");
            setNewKey("");
            setNewValue("");
        }
    }, [deployments, selectedDeployment, editingEnvKey, newValue, fetchDeploymentConfigs]);


    const resourceName = {
        singular: 'deployment',
        plural: 'deployments',
    };

    const EmptyDeploymentsState = () => (
        <EmptyState
            heading="No deployments configured"
            image="https://cdn.shopify.com/s/files/1/0262/4071/2726/files/emptystate-files.png"
        >
            <p>Set up your first deployment configuration to manage environment variables.</p>
        </EmptyState>
    );

    // no inline empty state component required now

    return (
        <Page 
            title="Deployment Configuration" 
            subtitle="Manage environment variables and settings for your deployments"
        >
            {deployments.length === 0 ? (
                <Card>
                    <EmptyDeploymentsState />
                </Card>
            ) : (
                <Card>
                    <IndexTable
                        resourceName={resourceName}
                        itemCount={deployments.length}
                        selectable={false}
                        headings={[
                            {title: 'Deployment Name'},
                            {title: 'Type'},
                            {title: 'Environment Variables'},
                            {title: 'Actions'},
                        ]}
                    >
                        {deployments.map((deployment, index) => {
                            const envVarsDisplay = (
                                <HorizontalStack gap="300" align="space-between">
                                    <Text as="span" variant="bodySm" tone="subdued">
                                        {deployment.envVars.length === 0 ? 'No env added' : `${deployment.envVars.length} variables`}
                                    </Text>
                                    <Tooltip content="Manage env variables">
                                        <Button size="slim" icon={SettingsMajor} onClick={() => openManageModal(deployment.id)} />
                                    </Tooltip>
                                </HorizontalStack>
                            );

                            return (
                                <IndexTable.Row id={deployment.id} key={deployment.id} position={index}>
                                    <IndexTable.Cell>
                                        <Text as="span" variant="headingSm">{deployment.name}</Text>
                                    </IndexTable.Cell>
                                    <IndexTable.Cell>
                                        <Badge tone="info">{deployment.type}</Badge>
                                    </IndexTable.Cell>
                                    <IndexTable.Cell>
                                        {envVarsDisplay}
                                    </IndexTable.Cell>
                                    <IndexTable.Cell>
                                        <ButtonGroup>
                                            <Tooltip content="Restart (coming soon)"><Button icon={RefreshMajor} disabled size="slim"/></Tooltip>
                                        </ButtonGroup>
                                    </IndexTable.Cell>
                                </IndexTable.Row>
                            );
                        })}
                    </IndexTable>
                </Card>
            )}

            {/* Add Environment Variable Modal */}
            <Modal
                open={isAddEnvOpen}
                onClose={() => setIsAddEnvOpen(false)}
                title={`Add environment variable - ${selectedDeployment?.name}`}
                primaryAction={{
                    content: 'Add Variable', 
                    onAction: handleAddEnv,
                    disabled: !newKey.trim()
                }}
                secondaryActions={[{content: 'Cancel', onAction: () => setIsAddEnvOpen(false)}]}
            >
                <Modal.Section>
                    <VerticalStack gap="400">
                        <TextField 
                            label="Variable Key" 
                            value={newKey} 
                            onChange={setNewKey} 
                            autoComplete="off"
                            placeholder="e.g., JAVA_OPTS, PORT, ENV"
                            helpText="Environment variable name (case-sensitive)"
                        />
                        <TextField 
                            label="Variable Value" 
                            value={newValue} 
                            onChange={setNewValue} 
                            autoComplete="off"
                            placeholder="Enter the value for this variable"
                            helpText="The value assigned to this environment variable"
                        />
                    </VerticalStack>
                </Modal.Section>
            </Modal>

            {/* Edit Environment Variable Modal */}
            <Modal
                open={isEditEnvOpen}
                onClose={() => setIsEditEnvOpen(false)}
                title={`Edit ${editingEnvKey} - ${selectedDeployment?.name}`}
                primaryAction={{
                    content: 'Update Variable', 
                    onAction: handleEditEnv
                }}
                secondaryActions={[{content: 'Cancel', onAction: () => setIsEditEnvOpen(false)}]}
            >
                <Modal.Section>
                    <VerticalStack gap="400">
                        <TextField 
                            label="Variable Key" 
                            value={newKey} 
                            disabled
                            helpText="Variable key cannot be changed"
                        />
                        <TextField 
                            label="Variable Value" 
                            value={newValue} 
                            onChange={setNewValue} 
                            autoComplete="off"
                            placeholder="Enter the new value"
                        />
                    </VerticalStack>
                </Modal.Section>
            </Modal>

            {/* Manage Environment Variables Modal */}
            <Modal
                open={isManageEnvOpen}
                onClose={closeManageModal}
                title={`Manage environment variables - ${selectedDeployment?.name || ''}`}
                primaryAction={{ content: 'Close', onAction: closeManageModal }}
                secondaryActions={[{content: 'Add variable', onAction: () => { setIsManageEnvOpen(false); toggleAddModal(selectedDeploymentId); }}]}
            >
                <Modal.Section>
                    <Box padding="300">
                    <Scrollable style={{maxHeight: '420px'}}>
                        <VerticalStack gap="400">
                            {selectedDeployment?.envVars?.length === 0 ? (
                                <Text tone="subdued">No env added</Text>
                            ) : selectedDeployment?.envVars.map(({key, value, editable}) => (
                                <Box key={key} padding="200">
                                    <Box style={{display: 'flex', alignItems: 'center', gap: 12, width: '100%', flexWrap: 'nowrap'}}>
                                        <Box style={{minWidth: 220, flex: '0 0 auto'}}>
                                            <Text as="span" variant="bodyMd" fontWeight="medium">{key}</Text>
                                        </Box>
                                        <Text as="span" tone="subdued" style={{flex: '0 0 auto'}}>=</Text>
                                        <Box style={{flex: '1 1 auto', minWidth: 200}}>
                                            <TextField
                                                label=""
                                                labelHidden
                                                value={value}
                                                disabled
                                                autoComplete="off"
                                            />
                                        </Box>
                                        <Box style={{flex: '0 0 auto'}}>
                                            <ButtonGroup>
                                                <Tooltip content={editable ? "Edit" : "Editing disabled"}>
                                                    <Button size="slim" icon={EditMajor} onClick={() => openEditFromManage(selectedDeploymentId, key, value)} disabled={!editable} />
                                                </Tooltip>
                                                <Tooltip content={editable ? "Delete" : "Delete disabled"}>
                                                    <Button size="slim" icon={DeleteMinor} tone="critical" onClick={() => editable && openDeleteConfirm(key)} disabled={!editable} />
                                                </Tooltip>
                                            </ButtonGroup>
                                        </Box>
                                    </Box>
                                </Box>
                            ))}
                        </VerticalStack>
                    </Scrollable>
                    </Box>
                </Modal.Section>
            </Modal>

            {/* Delete confirmation */}
            <Modal
                open={isDeleteConfirmOpen}
                onClose={closeDeleteConfirm}
                title={`Delete ${deletingKey}?`}
                primaryAction={{ content: 'Delete', destructive: true, onAction: confirmDelete }}
                secondaryActions={[{ content: 'Cancel', onAction: closeDeleteConfirm }]}
            >
                <Modal.Section>
                    <Text>Are you sure you want to delete this environment variable?</Text>
                </Modal.Section>
            </Modal>
        </Page>
    );
};

export default DeploymentConfigs;
