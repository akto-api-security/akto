import React, { useEffect, useState, useCallback, useRef } from 'react';
import { Card, Text, VerticalStack, HorizontalStack, Spinner, Box, Button, Divider } from '@shopify/polaris';
import { editor } from "monaco-editor/esm/vs/editor/editor.api";
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/language/json/monaco.contribution';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import api from '../api';

function SchemaView({ apiCollectionId }) {
    const [loading, setLoading] = useState(true);
    const [schemaData, setSchemaData] = useState(null);
    const [schemaStats, setSchemaStats] = useState({ servers: [], totalPaths: 0 });
    const [editorInstance, setEditorInstance] = useState(null);

    const editorRef = useRef(null);

    const calculateStats = useCallback((data) => {
        if (!data) return { servers: [], totalPaths: 0 };

        const servers = data.servers ? data.servers.map(s => s.url) : [];
        const totalPaths = data.paths ? Object.keys(data.paths).length : 0;

        return { servers, totalPaths };
    }, []);

    useEffect(() => {
        async function fetchSchema() {
            setLoading(true);
            try {
                const response = await api.fetchOpenApiSchema(apiCollectionId);
                const parsedSchema = JSON.parse(response.openApiSchema);
                setSchemaData(parsedSchema);
                setSchemaStats(calculateStats(parsedSchema));
            } catch (error) {
                console.error('Error fetching schema:', error);
            } finally {
                setLoading(false);
            }
        }

        if (apiCollectionId) {
            fetchSchema();
        }
    }, [apiCollectionId, calculateStats]);

    useEffect(() => {
        if (!loading && schemaData && editorRef.current && !editorInstance) {
            const content = JSON.stringify(schemaData, null, 2);

            // Define custom theme matching the codebase style
            editor.defineTheme('schemaTheme', {
                base: 'vs',
                inherit: true,
                rules: [
                    { token: 'string.key.json', foreground: 'A31515' },
                    { token: 'string.value.json', foreground: '0451A5' },
                    { token: 'number', foreground: '098658' },
                    { token: 'keyword.json', foreground: '0000FF' },
                ],
                colors: {
                    'editor.background': '#FAFBFB',
                    'editorLineNumber.foreground': '#999999',
                    'editorLineNumber.activeForeground': '#000000',
                    'editorIndentGuide.background': '#D3D3D3',
                }
            });

            const editorOptions = {
                value: content,
                language: 'json',
                readOnly: true,
                minimap: { enabled: true },
                wordWrap: 'on',
                automaticLayout: true,
                scrollBeyondLastLine: false,
                folding: true,
                foldingStrategy: 'indentation',
                showFoldingControls: 'always',
                theme: 'schemaTheme',
                fontSize: 13,
                lineNumbers: 'on',
                renderLineHighlight: 'line',
                tabSize: 2,
            };

            const instance = editor.create(editorRef.current, editorOptions);
            setEditorInstance(instance);
        }

        return () => {
            if (editorInstance) {
                editorInstance.dispose();
            }
        };
    }, [loading, schemaData]);

    const handleCollapseAll = () => {
        if (editorInstance) {
            editorInstance.getAction('editor.foldAll').run();
        }
    };

    const handleExpandAll = () => {
        if (editorInstance) {
            editorInstance.getAction('editor.unfoldAll').run();
        }
    };

    if (loading) {
        return (
            <Box padding="5">
                <Card padding="6">
                    <VerticalStack gap="4">
                        <HorizontalStack align="center" gap="2">
                            <Spinner size="small" />
                            <Text>Loading schema...</Text>
                        </HorizontalStack>
                    </VerticalStack>
                </Card>
            </Box>
        );
    }

    if (!schemaData) {
        return (
            <Box padding="5">
                <Card padding="6">
                    <Text>No schema data available</Text>
                </Card>
            </Box>
        );
    }

    return (
        <Box padding="5">
            <VerticalStack gap="4">
                {/* Banner */}
                <Card padding="4">
                    <VerticalStack gap="4">
                        <Text variant="headingMd" fontWeight="semibold">API Schema Overview</Text>
                        <HorizontalStack gap="8">
                            <Box>
                                <Text variant="bodySm" color="subdued">Servers</Text>
                                <VerticalStack gap="1">
                                    {schemaStats.servers.length > 0 ? (
                                        schemaStats.servers.map((server, index) => (
                                            <Text key={index} variant="bodyMd">{server}</Text>
                                        ))
                                    ) : (
                                        <Text variant="bodyMd" color="subdued">No servers defined</Text>
                                    )}
                                </VerticalStack>
                            </Box>
                            <Box>
                                <Text variant="bodySm" color="subdued">Total Paths</Text>
                                <Text variant="headingLg" fontWeight="bold">{schemaStats.totalPaths}</Text>
                            </Box>
                        </HorizontalStack>
                    </VerticalStack>
                </Card>

                {/* Split View - Monaco Editor (Left) and Swagger UI (Right) */}
                <HorizontalStack gap="4" wrap={false}>
                    {/* Left Panel - Monaco Editor */}
                    <Box width="50%">
                        <Card padding="0">
                            <VerticalStack>
                                <Box padding="2" background="bg-surface-secondary">
                                    <HorizontalStack gap="2">
                                        <Text variant="headingSm" fontWeight="semibold">Raw Schema</Text>
                                        <Box paddingInlineStart="4">
                                            <HorizontalStack gap="2">
                                                <Button size="slim" onClick={handleCollapseAll}>Collapse All</Button>
                                                <Button size="slim" onClick={handleExpandAll}>Expand All</Button>
                                            </HorizontalStack>
                                        </Box>
                                    </HorizontalStack>
                                </Box>
                                <Divider />
                                <div ref={editorRef} style={{ height: 'calc(100vh - 280px)', minHeight: '500px' }} />
                            </VerticalStack>
                        </Card>
                    </Box>

                    {/* Right Panel - Swagger UI */}
                    <Box width="50%">
                        <Card padding="0">
                            <VerticalStack>
                                <Box padding="2" background="bg-surface-secondary">
                                    <HorizontalStack align="start">
                                        <Text variant="headingSm" fontWeight="semibold">Visual Documentation</Text>
                                    </HorizontalStack>
                                </Box>
                                <Divider />
                                <div style={{ height: 'calc(100vh - 280px)', minHeight: '500px', overflow: 'auto' }} className="swagger-ui-container">
                                    <SwaggerUI
                                        spec={schemaData}
                                        docExpansion="list"
                                        defaultModelsExpandDepth={-1}
                                        displayRequestDuration={true}
                                        filter={true}
                                        showExtensions={true}
                                        showCommonExtensions={true}
                                        tryItOutEnabled={false}
                                    />
                                    <style>{`
                                        .swagger-ui .info {
                                            display: none;
                                        }
                                        .swagger-ui .filter-container {
                                            display: none;
                                        }
                                    `}</style>
                                </div>
                            </VerticalStack>
                        </Card>
                    </Box>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    );
}

export default SchemaView;
