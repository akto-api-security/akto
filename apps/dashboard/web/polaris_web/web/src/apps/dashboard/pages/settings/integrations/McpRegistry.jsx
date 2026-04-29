import React, { useEffect, useState } from 'react'
import IntegrationsLayout from './IntegrationsLayout'
import { Box, Button, LegacyCard, TextField, Text, VerticalStack, HorizontalStack, Banner, DataTable, Scrollable, Link, Badge } from '@shopify/polaris'
import { DeleteMinor } from '@shopify/polaris-icons'
import "../settings.css"
import func from "@/util/func"
import api from '../api'

function McpRegistry() {

    const defaultRegistryUrl = 'https://registry.modelcontextprotocol.io/v0/servers';
    const MAX_REGISTRIES = 10;
    const MAX_URL_LENGTH = 500;
    const MAX_HEADER_KEY_LENGTH = 100;
    const MAX_HEADER_VALUE_LENGTH = 500;
    const MAX_HEADERS = 20;

    const [registries, setRegistries] = useState([]);
    const [originalRegistries, setOriginalRegistries] = useState([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [syncingId, setSyncingId] = useState(null);
    const [syncStatuses, setSyncStatuses] = useState({});

    // New registry form state
    const [newRegistryUrl, setNewRegistryUrl] = useState('');
    const [newRegistryHeaders, setNewRegistryHeaders] = useState([{ key: '', value: '' }]);
    const [newRegistryType, setNewRegistryType] = useState('CSV_URL');
    const [showAddForm, setShowAddForm] = useState(false);
    const [adding, setAdding] = useState(false);

    // View endpoints state (per registry)
    const [expandedEndpointsId, setExpandedEndpointsId] = useState(null);
    const [endpointsByRegistryId, setEndpointsByRegistryId] = useState({});
    const [endpointsLoadingId, setEndpointsLoadingId] = useState(null);

    const headersArrayToObject = (arr) => {
        const obj = {};
        (arr || []).forEach(({ key, value }) => {
            const k = (key || '').trim();
            if (k) obj[k] = (value || '').trim();
        });
        return obj;
    };

    const updateHeader = (index, field, val) => {
        setNewRegistryHeaders(prev => prev.map((h, i) => i === index ? { ...h, [field]: val } : h));
    };

    const addHeaderRow = () => {
        if (newRegistryHeaders.length >= MAX_HEADERS) {
            func.setToast(true, true, `Maximum ${MAX_HEADERS} headers allowed`);
            return;
        }
        setNewRegistryHeaders(prev => [...prev, { key: '', value: '' }]);
    };

    const removeHeaderRow = (index) => {
        setNewRegistryHeaders(prev => {
            const next = prev.filter((_, i) => i !== index);
            return next.length > 0 ? next : [{ key: '', value: '' }];
        });
    };

    const buildEndpointRows = (registryId) => {
        const list = endpointsByRegistryId[registryId] || [];
        return list.map((entry) => [
            entry.name || '-',
            entry.url || '-',
            entry.sourceDisplay || 'Registry',
            entry.addedBy || '-',
            entry.createdAt ? func.epochToDateTime(entry.createdAt) : '-',
        ]);
    };

    async function fetchRegistrySettings() {
        setLoading(true);
        try {
            const response = await api.fetchMcpRegistries();

            let registriesList = [];
            if (response && Array.isArray(response.mcpRegistries)) {
                registriesList = response.mcpRegistries;
            } else if (response && response.mcpRegistryConfig && response.mcpRegistryConfig.registries) {
                registriesList = response.mcpRegistryConfig.registries;
            } else {
                registriesList = [];
            }

            setRegistries(registriesList);
            setOriginalRegistries(JSON.parse(JSON.stringify(registriesList)));
        } catch (error) {
            console.error("Failed to load registry settings:", error);
            const errorMsg = error?.response?.data?.actionErrors?.[0] || "Failed to load registry settings";
            func.setToast(true, true, errorMsg);
            setRegistries([]);
            setOriginalRegistries([]);
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        fetchRegistrySettings();
    }, []);

    const syncRegistry = async (registryId) => {
        setSyncingId(registryId);
        setSyncStatuses(prev => ({ ...prev, [registryId]: null }));
        try {
            const response = await api.syncMcpRegistry(registryId);
            const message = (response && response.message) || 'Sync initiated successfully';
            setSyncStatuses(prev => ({
                ...prev,
                [registryId]: { success: true, message }
            }));
        } catch (error) {
            const errorMsg = error?.response?.data?.actionErrors?.[0] || error?.message || 'Failed to initiate sync';
            setSyncStatuses(prev => ({
                ...prev,
                [registryId]: { success: false, message: errorMsg }
            }));
        } finally {
            setSyncingId(null);
        }
    };

    const viewEndpoints = async (registryId) => {
        if (expandedEndpointsId === registryId) {
            setExpandedEndpointsId(null);
            return;
        }
        setExpandedEndpointsId(registryId);

        setEndpointsLoadingId(registryId);
        try {
            const response = await api.fetchMcpAllowlistEntries(registryId);
            const list = (response && Array.isArray(response.mcpAllowlistEntries)) ? response.mcpAllowlistEntries : [];
            setEndpointsByRegistryId(prev => ({ ...prev, [registryId]: list }));
        } catch (error) {
            const errorMsg = error?.response?.data?.actionErrors?.[0] || 'Failed to fetch endpoints';
            func.setToast(true, true, errorMsg);
            setEndpointsByRegistryId(prev => ({ ...prev, [registryId]: [] }));
        } finally {
            setEndpointsLoadingId(null);
        }
    };

    const validateRegistryUrl = (url) => {
        const urlPattern = /^https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)$/;
        return urlPattern.test(url);
    };

    const addRegistry = async () => {
        const trimmedUrl = newRegistryUrl.trim();

        if (!trimmedUrl) {
            func.setToast(true, true, "Please enter a registry URL");
            return;
        }

        if (trimmedUrl.length > MAX_URL_LENGTH) {
            func.setToast(true, true, `Registry URL too long (max ${MAX_URL_LENGTH} characters)`);
            return;
        }

        if (!validateRegistryUrl(trimmedUrl)) {
            func.setToast(true, true, "Invalid URL format. Must be a valid http or https URL");
            return;
        }

        if (registries.length >= MAX_REGISTRIES) {
            func.setToast(true, true, `Maximum ${MAX_REGISTRIES} registries allowed`);
            return;
        }

        const urlLower = trimmedUrl.toLowerCase();
        if (registries.some(r => r.url && r.url.toLowerCase() === urlLower)) {
            func.setToast(true, true, "A registry with this URL already exists");
            return;
        }

        for (const h of newRegistryHeaders) {
            const k = (h.key || '').trim();
            const v = (h.value || '').trim();
            if (!k && !v) continue;
            if (!k) {
                func.setToast(true, true, "Header key cannot be empty");
                return;
            }
            if (k.length > MAX_HEADER_KEY_LENGTH) {
                func.setToast(true, true, `Header key too long (max ${MAX_HEADER_KEY_LENGTH} characters)`);
                return;
            }
            if (v.length > MAX_HEADER_VALUE_LENGTH) {
                func.setToast(true, true, `Header value too long (max ${MAX_HEADER_VALUE_LENGTH} characters)`);
                return;
            }
        }

        const headersObj = headersArrayToObject(newRegistryHeaders);
        const registryType = newRegistryType || 'CSV_URL';

        setAdding(true);
        try {
            await api.addMcpRegistry(trimmedUrl, headersObj, registryType);
            func.setToast(true, false, "Registry added. Endpoints will be ingested on sync.");
            setNewRegistryUrl('');
            setNewRegistryHeaders([{ key: '', value: '' }]);
            setNewRegistryType('CSV_URL');
            setShowAddForm(false);
            await fetchRegistrySettings();
        } catch (error) {
            const errorMsg = error?.response?.data?.actionErrors?.[0] || "Failed to add URL";
            func.setToast(true, true, errorMsg);
            window.location.reload();
        } finally {
            setAdding(false);
        }
    };

    const deleteRegistry = async (registryHexId, registryUrl) => {
        if (!window.confirm(`Are you sure you want to remove the registry "${registryUrl}"?`)) {
            return;
        }

        try {
            await api.deleteMcpRegistry(registryHexId);
            setRegistries(prev => prev.filter(r => r.hexId !== registryHexId));
            func.setToast(true, false, "Registry removed successfully.");
        } catch (error) {
            const errorMsg = error?.response?.data?.actionErrors?.[0] || "Failed to delete registry";
            func.setToast(true, true, errorMsg);
        }
    };

    const resetToDefault = () => {
        // Confirm reset
        if (!window.confirm('Are you sure you want to remove all custom registries and reset to the official registry only? This action will remove all your custom registry configurations.')) {
            return;
        }

        // Reset to only the default registry (local state only)
        setRegistries([]);
        func.setToast(true, false, "Reset to default. Click 'Save Configuration' to apply changes.");
    };

    const saveAction = async () => {
        if (saving) {
            return; // Prevent double-clicking
        }

        setSaving(true);
        try {
            const response = await api.addMcpRegistryIntegration(registries);
            
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
        setSyncStatuses({});
        setNewRegistryUrl('');
        setNewRegistryHeaders([{ key: '', value: '' }]);
        setShowAddForm(false);
        func.setToast(true, true, "Changes Discarded");
    };

    const hasChanges = () => {
        return JSON.stringify(registries) !== JSON.stringify(originalRegistries);
    };

    const component = (
        <LegacyCard
            // secondaryFooterActions={[
            //     { content: 'Discard Changes', destructive: true, onAction: discardAction, disabled: !hasChanges() || saving, }
            // ]}
            // primaryFooterAction={{ 
            //     content: 'Save Configuration', 
            //     onAction: saveAction, 
            //     disabled: !hasChanges() || saving,
            //     loading: saving
            // }}
        >
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingMd" as="h2">MCP Registry Configuration</Text>
                        <HorizontalStack gap="2">
                            {!registries.some(r => !r.isDefault) && (
                                <Button
                                    onClick={() => setShowAddForm(!showAddForm)}
                                    disabled={saving}
                                >
                                    {showAddForm ? 'Cancel' : 'Add URL'}
                                </Button>
                            )}
                            {/* <Button
                                onClick={resetToDefault}
                                disabled={saving}
                            >
                                Reset to Default
                            </Button> */}
                        </HorizontalStack>
                    </HorizontalStack>

                    <Banner tone="info">
                        <Text variant="bodyMd">
                            Configure MCP (Model Context Protocol) Registries to discover and validate MCP servers.
                        </Text>
                    </Banner>

                    {/* Registered Registries List */}
                    <Box>
                        <VerticalStack gap="3">

                            {showAddForm && (
                                <LegacyCard sectioned>
                                    <VerticalStack gap="3">
                                        <Text variant="headingMd" as="h4">Add URL</Text>
                                        <TextField
                                            label="Registry URL"
                                            value={newRegistryUrl}
                                            onChange={setNewRegistryUrl}
                                            placeholder="https://example.com/path/to/mcp_servers.csv"
                                            helpText="The file at this URL will be read to extract MCP endpoints."
                                            autoComplete="off"
                                            maxLength={MAX_URL_LENGTH}
                                        />

                                        <VerticalStack gap="2">
                                            <Text variant="headingSm" as="h5">Request Headers (optional)</Text>
                                            <Text variant="bodySm" color="subdued">
                                                Add headers (e.g., Authorization) used when fetching the file.
                                            </Text>
                                            {newRegistryHeaders.map((header, idx) => (
                                                <HorizontalStack key={idx} gap="2" blockAlign="end">
                                                    <Box width="40%">
                                                        <TextField
                                                            label={idx === 0 ? "Header Key" : ""}
                                                            labelHidden={idx !== 0}
                                                            value={header.key}
                                                            onChange={(v) => updateHeader(idx, 'key', v)}
                                                            placeholder="Authorization"
                                                            autoComplete="off"
                                                            maxLength={MAX_HEADER_KEY_LENGTH}
                                                        />
                                                    </Box>
                                                    <Box width="50%">
                                                        <TextField
                                                            label={idx === 0 ? "Header Value" : ""}
                                                            labelHidden={idx !== 0}
                                                            value={header.value}
                                                            onChange={(v) => updateHeader(idx, 'value', v)}
                                                            placeholder="Bearer token"
                                                            autoComplete="off"
                                                            maxLength={MAX_HEADER_VALUE_LENGTH}
                                                        />
                                                    </Box>
                                                    <Button
                                                        plain
                                                        destructive
                                                        icon={DeleteMinor}
                                                        onClick={() => removeHeaderRow(idx)}
                                                        accessibilityLabel="Remove header"
                                                    />
                                                </HorizontalStack>
                                            ))}
                                            <Box>
                                                <Button onClick={addHeaderRow} disabled={newRegistryHeaders.length >= MAX_HEADERS}>
                                                    Add Header
                                                </Button>
                                            </Box>
                                        </VerticalStack>

                                        <HorizontalStack gap="2">
                                            <Button primary onClick={addRegistry} loading={adding} disabled={adding}>
                                                Add Registry
                                            </Button>
                                            <Button onClick={() => {
                                                setShowAddForm(false);
                                                setNewRegistryUrl('');
                                                setNewRegistryHeaders([{ key: '', value: '' }]);
                                            }} disabled={adding}>
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
                                            <HorizontalStack align="space-between" blockAlign="center">
                                                <Text variant="bodyMd" color="subdued" breakWord>
                                                    {registry.url}
                                                </Text>
                                                {!registry.isDefault && (
                                                    <Button
                                                        plain
                                                        destructive
                                                        onClick={() => deleteRegistry(registry.hexId, registry.url)}
                                                        icon={DeleteMinor}
                                                        disabled={saving}
                                                    >
                                                        Remove
                                                    </Button>
                                                )}
                                            </HorizontalStack>

                                            {!registry.isDefault && (
                                                <HorizontalStack gap="2">
                                                    <Button
                                                        onClick={() => syncRegistry(registry.hexId)}
                                                        loading={syncingId === registry.hexId}
                                                        disabled={syncingId !== null || saving}
                                                        size="slim"
                                                        primary
                                                    >
                                                        Sync now
                                                    </Button>
                                                    <Button
                                                        onClick={() => viewEndpoints(registry.hexId)}
                                                        loading={endpointsLoadingId === registry.hexId}
                                                        disabled={saving}
                                                        size="slim"
                                                    >
                                                        {expandedEndpointsId === registry.hexId ? 'Hide endpoints' : 'View endpoints'}
                                                    </Button>
                                                </HorizontalStack>
                                            )}

                                            {syncStatuses[registry.hexId] && (
                                                <Banner
                                                    tone={syncStatuses[registry.hexId].success ? "success" : "critical"}
                                                    onDismiss={() => {
                                                        const newStatuses = { ...syncStatuses };
                                                        delete newStatuses[registry.hexId];
                                                        setSyncStatuses(newStatuses);
                                                    }}
                                                >
                                                    <Text variant="bodyMd">
                                                        {syncStatuses[registry.hexId].message}
                                                    </Text>
                                                </Banner>
                                            )}

                                            {expandedEndpointsId === registry.hexId && (
                                                <Box>
                                                    {endpointsLoadingId === registry.hexId ? (
                                                        <Text variant="bodyMd" color="subdued">Loading endpoints...</Text>
                                                    ) : (endpointsByRegistryId[registry.hexId] && endpointsByRegistryId[registry.hexId].length > 0) ? (
                                                        <VerticalStack gap="2">
                                                            <Text variant="bodySm" color="subdued">
                                                                {endpointsByRegistryId[registry.hexId].length} endpoints
                                                            </Text>
                                                            <Scrollable style={{ maxHeight: '480px' }} shadow focusable>
                                                                <DataTable
                                                                    columnContentTypes={['text', 'text', 'text', 'text', 'text']}
                                                                    headings={['Name', 'URL', 'Source', 'Added By', 'Created At']}
                                                                    rows={buildEndpointRows(registry.hexId)}
                                                                    increasedTableDensity
                                                                    hoverable
                                                                />
                                                            </Scrollable>
                                                        </VerticalStack>
                                                    ) : (
                                                        <Text variant="bodyMd" color="subdued">
                                                            No endpoints found. Click "Sync now" to ingest endpoints from the registry.
                                                        </Text>
                                                    )}
                                                </Box>
                                            )}
                                        </VerticalStack>
                                    </LegacyCard>
                                ))}
                            </VerticalStack>
                        </VerticalStack>
                    </Box>

                    <LegacyCard sectioned>
                        <VerticalStack gap="4">
                            <Text variant="headingMd">How to add a URL</Text>
                            <VerticalStack gap="2">
                                <Text variant="bodyMd" fontWeight="semibold">1. Open the form</Text>
                                <Text variant="bodyMd" color="subdued">Click <b>Add URL</b> to open the form.</Text>
                                <Text variant="bodyMd" fontWeight="semibold">2. Enter the URL</Text>
                                <Text variant="bodyMd" color="subdued">Enter the URL pointing to your CSV file (e.g. <code>https://example.com/path/to/mcp_servers.csv</code>).</Text>
                                <Link url="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/dashboard/src/main/resources/sample_mcp_registry.csv" target="_blank">Download sample CSV</Link>
                                <Text variant="bodyMd" fontWeight="semibold">3. Add authentication (if required)</Text>
                                <Text variant="bodyMd" color="subdued">If the file requires authentication, add a header — key: <code>Authorization</code>, value: <code>Bearer &lt;your_token&gt;</code>.</Text>
                                <Text variant="bodyMd" fontWeight="semibold">4. Submit</Text>
                                <Text variant="bodyMd" color="subdued">Click <b>Add Registry</b> — MCP server entries will be ingested automatically from the CSV.</Text>
                            </VerticalStack>
                        </VerticalStack>
                    </LegacyCard>

                    <LegacyCard sectioned>
                        <VerticalStack gap="4">
                            <Text variant="headingMd">Important Notes</Text>
                            <VerticalStack gap="2">
                                <Text variant="bodyMd" fontWeight="semibold">1. Only one registry URL is supported.</Text>
                                <Text variant="bodyMd" fontWeight="semibold">2. CSV format</Text>
                                <Text variant="bodyMd" color="subdued">Your CSV must have a header row with a <code>mcp_server_name</code> column. Each row is one MCP server name.</Text>
                                <pre style={{ margin: 0, fontSize: '12px', fontFamily: 'monospace', lineHeight: '1.8', background: '#f6f6f7', border: '1px solid #e1e3e5', borderRadius: '6px', padding: '10px 14px' }}>{'mcp_server_name\napi.githubcopilot.com\nmcp.notion.com\nfilesystem-local\nmy-postgres'}</pre>
                                <Text variant="bodyMd" color="subdued">For remote MCP servers, use the domain name (e.g. <code>api.example.com</code>). For local MCP servers, use the name your team has given it (e.g. <code>filesystem-local</code>, <code>my-postgres</code>).</Text>
                                <Link url="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/dashboard/src/main/resources/sample_mcp_registry.csv" target="_blank">Download sample CSV</Link>
                                <Text variant="bodyMd" fontWeight="semibold">3. Syncing</Text>
                                <Text variant="bodyMd" color="subdued">Updated your CSV? Wait 5 minutes for changes to propagate, then click <b>Sync now</b> to pull in the latest entries.</Text>
                            </VerticalStack>
                        </VerticalStack>
                    </LegacyCard>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );

    const cardContent = "Configure and manage MCP servers from a remote registry URL for discovering and validating Model Context Protocol servers in your environment.";

    return (
        <IntegrationsLayout
            title={<HorizontalStack gap="2" blockAlign="center"><span>MCP Registry</span><Badge status="info">Beta</Badge></HorizontalStack>}
            cardContent={cardContent}
            component={component}
            docsUrl="https://registry.modelcontextprotocol.io/docs"
        />
    );
}

export default McpRegistry;

