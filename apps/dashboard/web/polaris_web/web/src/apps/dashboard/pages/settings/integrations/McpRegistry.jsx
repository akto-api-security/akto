import React, { useEffect, useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout'
import { Box, Button, LegacyCard, TextField, Text, VerticalStack, HorizontalStack, Badge, Banner } from '@shopify/polaris'
import { DeleteMinor } from '@shopify/polaris-icons'
import "../settings.css"
import func from "@/util/func"
import api from '../api'

function McpRegistry() {

    const defaultRegistryUrl = 'https://registry.modelcontextprotocol.io/v0/servers';
    const MAX_REGISTRIES = 10;
    const MAX_NAME_LENGTH = 100;
    const MAX_URL_LENGTH = 500;
    const TEST_CONNECTION_TIMEOUT = 10000; // 10 seconds
    
    const [registries, setRegistries] = useState([
        { id: 'default', name: 'Official MCP Registry', url: defaultRegistryUrl, isDefault: true }
    ]);
    const [originalRegistries, setOriginalRegistries] = useState([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [testingConnectionId, setTestingConnectionId] = useState(null);
    const [connectionStatuses, setConnectionStatuses] = useState({});
    
    // New registry form state
    const [newRegistryName, setNewRegistryName] = useState('');
    const [newRegistryUrl, setNewRegistryUrl] = useState('');
    const [showAddForm, setShowAddForm] = useState(false);

    async function fetchRegistrySettings() {
        setLoading(true);
        try {
            // Call the add API with null to fetch current state from DB without modifying it
            const response = await api.addMcpRegistryIntegration(null);
            
            let registriesList = [];
            if (response && response.mcpRegistryConfig && response.mcpRegistryConfig.registries) {
                registriesList = response.mcpRegistryConfig.registries;
            } else {
                // Default registry if none exists
                registriesList = [
                    { id: 'default', name: 'Official MCP Registry', url: defaultRegistryUrl, isDefault: true }
                ];
            }
            
            setRegistries(registriesList);
            setOriginalRegistries(JSON.parse(JSON.stringify(registriesList)));
        } catch (error) {
            console.error("Failed to load registry settings:", error);
            const errorMsg = error?.response?.data?.actionErrors?.[0] || "Failed to load registry settings";
            func.setToast(true, true, errorMsg);
            // Set default registry on error
            const defaultList = [
                { id: 'default', name: 'Official MCP Registry', url: defaultRegistryUrl, isDefault: true }
            ];
            setRegistries(defaultList);
            setOriginalRegistries(JSON.parse(JSON.stringify(defaultList)));
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        fetchRegistrySettings();
    }, []);

    const testConnection = async (registryId, url) => {
        setTestingConnectionId(registryId);
        setConnectionStatuses(prev => ({ ...prev, [registryId]: null }));
        
        try {
            // Create abort controller for timeout
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), TEST_CONNECTION_TIMEOUT);
            
            // Test the connection by attempting to fetch from the registry URL
            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                },
                signal: controller.signal,
            });
            
            clearTimeout(timeoutId);
            
            if (response.ok) {
                setConnectionStatuses(prev => ({ 
                    ...prev, 
                    [registryId]: { success: true, message: 'Connection successful!' } 
                }));
            } else {
                setConnectionStatuses(prev => ({ 
                    ...prev, 
                    [registryId]: { success: false, message: `Failed with status: ${response.status}` } 
                }));
            }
        } catch (error) {
            let errorMessage = 'Failed to connect to registry';
            if (error.name === 'AbortError') {
                errorMessage = 'Connection timeout (10 seconds)';
            } else if (error.message.includes('CORS')) {
                errorMessage = 'CORS error - registry may not allow cross-origin requests';
            } else if (error.message) {
                errorMessage = error.message;
            }
            
            setConnectionStatuses(prev => ({ 
                ...prev, 
                [registryId]: { 
                    success: false, 
                    message: errorMessage
                } 
            }));
        } finally {
            setTestingConnectionId(null);
        }
    };

    const validateRegistryUrl = (url) => {
        const urlPattern = /^https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)$/;
        return urlPattern.test(url);
    };

    const sanitizeName = (name) => {
        // Remove potentially dangerous characters
        return name.replace(/[<>"']/g, '');
    };

    const addRegistry = () => {
        // Trim inputs
        const trimmedName = newRegistryName.trim();
        const trimmedUrl = newRegistryUrl.trim();
        
        // Basic validation
        if (!trimmedName || !trimmedUrl) {
            func.setToast(true, true, "Please enter both registry name and URL");
            return;
        }
        
        // Length validation
        if (trimmedName.length > MAX_NAME_LENGTH) {
            func.setToast(true, true, `Registry name too long (max ${MAX_NAME_LENGTH} characters)`);
            return;
        }
        
        if (trimmedUrl.length > MAX_URL_LENGTH) {
            func.setToast(true, true, `Registry URL too long (max ${MAX_URL_LENGTH} characters)`);
            return;
        }
        
        // URL format validation
        if (!validateRegistryUrl(trimmedUrl)) {
            func.setToast(true, true, "Invalid URL format. Must be a valid http or https URL");
            return;
        }
        
        // Check max registries
        if (registries.length >= MAX_REGISTRIES) {
            func.setToast(true, true, `Maximum ${MAX_REGISTRIES} registries allowed`);
            return;
        }
        
        // Check for duplicate names (case-insensitive)
        const nameLower = trimmedName.toLowerCase();
        if (registries.some(r => r.name.toLowerCase() === nameLower)) {
            func.setToast(true, true, "A registry with this name already exists");
            return;
        }
        
        // Check for duplicate URLs (case-insensitive)
        const urlLower = trimmedUrl.toLowerCase();
        if (registries.some(r => r.url.toLowerCase() === urlLower)) {
            func.setToast(true, true, "A registry with this URL already exists");
            return;
        }

        // Sanitize name and create new registry
        const newRegistry = {
            id: `custom-${Date.now()}`,
            name: sanitizeName(trimmedName),
            url: trimmedUrl,
            isDefault: false
        };

        setRegistries([...registries, newRegistry]);
        setNewRegistryName('');
        setNewRegistryUrl('');
        setShowAddForm(false);
        func.setToast(true, false, "Registry added. Click 'Save Configuration' to apply changes.");
    };

    const deleteRegistry = (registryId, registryName) => {
        // Prevent deleting the default registry
        const registry = registries.find(r => r.id === registryId);
        if (registry && registry.isDefault) {
            func.setToast(true, true, "Cannot delete the official registry");
            return;
        }

        // Confirm deletion
        if (!window.confirm(`Are you sure you want to delete the registry "${registryName}"?`)) {
            return;
        }

        setRegistries(registries.filter(r => r.id !== registryId));
        func.setToast(true, false, "Registry removed. Click 'Save Configuration' to apply changes.");
    };

    const resetToDefault = () => {
        // Confirm reset
        if (!window.confirm('Are you sure you want to remove all custom registries and reset to the official registry only? This action will remove all your custom registry configurations.')) {
            return;
        }

        // Reset to only the default registry (local state only)
        const defaultRegistry = [
            { id: 'default', name: 'Official MCP Registry', url: defaultRegistryUrl, isDefault: true }
        ];
        setRegistries(defaultRegistry);
        func.setToast(true, false, "Reset to default. Click 'Save Configuration' to apply changes.");
    };

    const saveAction = async () => {
        if (registries.length === 0) {
            func.setToast(true, true, "You must have at least one registry");
            return;
        }

        if (saving) {
            return; // Prevent double-clicking
        }

        setSaving(true);
        try {
            // If only default registry exists, send empty array to delete custom config
            const dataToSend = (registries.length === 1 && registries[0].isDefault) ? [] : registries;
            const response = await api.addMcpRegistryIntegration(dataToSend);
            
            // Update original registries from the response
            if (response && response.mcpRegistryConfig && response.mcpRegistryConfig.registries) {
                setOriginalRegistries(JSON.parse(JSON.stringify(response.mcpRegistryConfig.registries)));
                setRegistries(response.mcpRegistryConfig.registries);
            } else {
                setOriginalRegistries(JSON.parse(JSON.stringify(registries)));
            }
            
            func.setToast(true, false, "MCP Registry settings updated successfully");
        } catch (error) {
            console.error("Failed to update registry settings:", error);
            const errorMsg = error?.response?.data?.actionErrors?.[0] || "Failed to update registry settings";
            func.setToast(true, true, errorMsg);
        } finally {
            setSaving(false);
        }
    };

    const discardAction = () => {
        setRegistries(JSON.parse(JSON.stringify(originalRegistries)));
        setConnectionStatuses({});
        setNewRegistryName('');
        setNewRegistryUrl('');
        setShowAddForm(false);
        func.setToast(true, true, "Changes Discarded");
    };

    const hasChanges = () => {
        return JSON.stringify(registries) !== JSON.stringify(originalRegistries);
    };

    const component = (
        <LegacyCard
            title="MCP Registry Configuration"
            secondaryFooterActions={[
                { content: 'Discard Changes', destructive: true, onAction: discardAction, disabled: !hasChanges() || saving }
            ]}
            primaryFooterAction={{ 
                content: 'Save Configuration', 
                onAction: saveAction, 
                disabled: !hasChanges() || saving,
                loading: saving
            }}
        >
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <Banner tone="info">
                        <Text variant="bodyMd">
                            Configure MCP (Model Context Protocol) Registries to discover and validate MCP servers.
                            You can add multiple registries to expand your MCP server catalog.
                        </Text>
                    </Banner>

                    {/* Registered Registries List */}
                    <Box>
                        <VerticalStack gap="3">
                            <HorizontalStack align="space-between">
                                <Text variant="headingMd" as="h3">
                                    Configured Registries ({registries.length}/{MAX_REGISTRIES})
                                </Text>
                                <HorizontalStack gap="2">
                                    <Button 
                                        onClick={() => setShowAddForm(!showAddForm)}
                                        disabled={saving || registries.length >= MAX_REGISTRIES}
                                    >
                                        {showAddForm ? 'Cancel' : 'Add Registry'}
                                    </Button>
                                    <Button 
                                        onClick={resetToDefault}
                                        disabled={saving}
                                        tone="critical"
                                    >
                                        Reset to Default
                                    </Button>
                                </HorizontalStack>
                            </HorizontalStack>

                            {/* Add Registry Form */}
                            {showAddForm && (
                                <LegacyCard sectioned>
                                    <VerticalStack gap="3">
                                        <Text variant="headingMd" as="h4">Add New Registry</Text>
                                        <TextField
                                            label="Registry Name"
                                            value={newRegistryName}
                                            onChange={setNewRegistryName}
                                            placeholder="e.g., Internal MCP Registry"
                                            autoComplete="off"
                                            maxLength={MAX_NAME_LENGTH}
                                            showCharacterCount
                                        />
                                    <TextField
                                            label="Registry URL"
                                            value={newRegistryUrl}
                                            onChange={setNewRegistryUrl}
                                            placeholder="https://your-registry.com/v0/servers"
                                            helpText="Enter the full URL of your MCP registry API endpoint"
                                        autoComplete="off"
                                            maxLength={MAX_URL_LENGTH}
                                        />
                                        <HorizontalStack gap="2">
                                            <Button primary onClick={addRegistry}>
                                                Add Registry
                                            </Button>
                                            <Button onClick={() => {
                                                setShowAddForm(false);
                                                setNewRegistryName('');
                                                setNewRegistryUrl('');
                                            }}>
                                                Cancel
                                            </Button>
                                        </HorizontalStack>
                                    </VerticalStack>
                                </LegacyCard>
                            )}

                            {/* Registries List */}
                            <VerticalStack gap="3">
                                {registries.map((registry) => (
                                    <LegacyCard key={registry.id} sectioned>
                                        <VerticalStack gap="3">
                                            <HorizontalStack align="space-between">
                                                <Box>
                                                    <HorizontalStack gap="2" align="center">
                                                        <Text variant="headingMd" as="h4">
                                                            {registry.name}
                                                        </Text>
                                                        {registry.isDefault && (
                                                            <Badge tone="success">Official</Badge>
                                                        )}
                                                    </HorizontalStack>
                                                </Box>
                                                {!registry.isDefault && (
                                                    <Button
                                                        plain
                                                        destructive
                                                        onClick={() => deleteRegistry(registry.id, registry.name)}
                                                        icon={DeleteMinor}
                                                        disabled={saving}
                                                    >
                                                        Remove
                                                    </Button>
                                                )}
                                            </HorizontalStack>

                                            <Box>
                                                <Text variant="bodyMd" color="subdued" breakWord>
                                                    {registry.url}
                                                </Text>
                                            </Box>

                                            <HorizontalStack gap="2">
                                                <Button
                                                    onClick={() => testConnection(registry.id, registry.url)}
                                                    loading={testingConnectionId === registry.id}
                                                    disabled={testingConnectionId !== null || saving}
                                                    size="slim"
                                                >
                                                    Test Connection
                                                </Button>
                                            </HorizontalStack>

                                            {connectionStatuses[registry.id] && (
                                <Banner
                                                    tone={connectionStatuses[registry.id].success ? "success" : "critical"}
                                                    onDismiss={() => {
                                                        const newStatuses = { ...connectionStatuses };
                                                        delete newStatuses[registry.id];
                                                        setConnectionStatuses(newStatuses);
                                                    }}
                                                >
                                                    <Text variant="bodyMd">
                                                        {connectionStatuses[registry.id].message}
                                                    </Text>
                                </Banner>
                            )}
                                        </VerticalStack>
                                    </LegacyCard>
                                ))}
                            </VerticalStack>
                        </VerticalStack>
                    </Box>

                    <Banner tone="info">
                        <VerticalStack gap="2">
                            <Text variant="bodyMd" fontWeight="semibold">
                                Important Notes:
                            </Text>
                            <Text variant="bodyMd">
                                • All registries will be used for MCP server discovery and validation
                            </Text>
                            <Text variant="bodyMd">
                                • Custom registries must follow the MCP Registry API specification
                            </Text>
                            <Text variant="bodyMd">
                                • Ensure your custom registries are accessible from your Akto instance
                            </Text>
                        </VerticalStack>
                    </Banner>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );

    const cardContent = "Configure and manage multiple MCP Registry endpoints for discovering and validating Model Context Protocol servers in your environment.";

    return (
        <IntegrationsLayout
            title="MCP Registry"
            cardContent={cardContent}
            component={component}
            docsUrl="https://registry.modelcontextprotocol.io/docs"
        />
    );
}

export default McpRegistry;

