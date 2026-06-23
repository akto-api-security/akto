import React, { useEffect, useState, useCallback, useRef } from 'react';
import { Card, Text, VerticalStack, HorizontalStack, Spinner, Box, Button, Divider, Tabs } from '@shopify/polaris';
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
    const [openApiSchema, setOpenApiSchema] = useState(null);
    const [graphqlSchema, setGraphqlSchema] = useState(null);
    const [schemaStats, setSchemaStats] = useState({ servers: [], totalPaths: 0 });
    const [editorInstance, setEditorInstance] = useState(null);
    const [selectedTab, setSelectedTab] = useState(0);

    const editorRef = useRef(null);

    const calculateStats = useCallback((data) => {
        if (!data) return { servers: [], totalPaths: 0 };
        const servers = data.servers ? data.servers.map(s => s.url) : [];
        const totalPaths = data.paths ? Object.keys(data.paths).length : 0;
        return { servers, totalPaths };
    }, []);

    useEffect(() => {
        async function fetchSchemas() {
            setLoading(true);
            const [openApiResult, graphqlResult] = await Promise.allSettled([
                api.fetchOpenApiSchema(apiCollectionId),
                api.fetchGraphQLSchema(apiCollectionId),
            ]);

            if (openApiResult.status === 'fulfilled' && openApiResult.value?.openApiSchema) {
                try {
                    const parsed = JSON.parse(openApiResult.value.openApiSchema);
                    setOpenApiSchema(parsed);
                    setSchemaStats(calculateStats(parsed));
                } catch (_) {}
            }

            if (graphqlResult.status === 'fulfilled' && graphqlResult.value?.graphqlSchema) {
                setGraphqlSchema(graphqlResult.value.graphqlSchema);
            }

            setLoading(false);
        }

        if (apiCollectionId) {
            fetchSchemas();
        }
    }, [apiCollectionId, calculateStats]);

    const hasBoth = openApiSchema && graphqlSchema;
    const activeIsGraphql = hasBoth ? selectedTab === 1 : !!graphqlSchema;

    useEffect(() => {
        if (editorInstance) {
            editorInstance.dispose();
            setEditorInstance(null);
        }
    }, [activeIsGraphql]);

    useEffect(() => {
        if (!loading && editorRef.current && !editorInstance) {
            if (!editor.languages.getLanguages().some(l => l.id === 'graphql')) {
                editor.languages.register({ id: 'graphql' });
                editor.languages.setMonarchTokensProvider('graphql', {
                    tokenizer: {
                        root: [
                            [/#.*$/, 'comment'],
                            [/"""/, 'string', '@tripleString'],
                            [/"([^"\\]|\\.)*"/, 'string'],
                            [/@[a-zA-Z_]\w*/, 'annotation'],
                            [/\b(query|mutation|subscription|fragment|on|schema|scalar|enum|input|interface|union|extend|directive|implements|type|repeatable|true|false|null)\b/, 'keyword'],
                            [/[0-9]+(\.[0-9]+)?/, 'number'],
                            [/[a-zA-Z_]\w*/, 'type.identifier'],
                            [/[{}()\[\]:!|&]/, 'delimiter'],
                        ],
                        tripleString: [
                            [/"""/, 'string', '@pop'],
                            [/./, 'string'],
                        ],
                    },
                });
                editor.languages.setLanguageConfiguration('graphql', {
                    brackets: [['{', '}'], ['(', ')'], ['[', ']']],
                    comments: { lineComment: '#' },
                });
            }

            const content = activeIsGraphql
                ? (graphqlSchema || '')
                : (openApiSchema ? JSON.stringify(openApiSchema, null, 2) : '');
            const language = activeIsGraphql ? 'graphql' : 'json';

            editor.defineTheme('schemaTheme', {
                base: 'vs',
                inherit: true,
                rules: [
                    // JSON tokens
                    { token: 'string.key.json', foreground: 'A31515' },
                    { token: 'string.value.json', foreground: '0451A5' },
                    { token: 'number', foreground: '098658' },
                    { token: 'keyword.json', foreground: '0000FF' },
                    // GraphQL tokens
                    { token: 'keyword', foreground: '0000FF', fontStyle: 'bold' },
                    { token: 'type.identifier', foreground: '267F99' },
                    { token: 'string', foreground: 'A31515' },
                    { token: 'comment', foreground: '6A9955', fontStyle: 'italic' },
                    { token: 'annotation', foreground: '795E26' },
                    { token: 'number', foreground: '098658' },
                    { token: 'delimiter', foreground: '666666' },
                ],
                colors: {
                    'editor.background': '#FAFBFB',
                    'editorLineNumber.foreground': '#999999',
                    'editorLineNumber.activeForeground': '#000000',
                    'editorIndentGuide.background': '#D3D3D3',
                }
            });

            const instance = editor.create(editorRef.current, {
                value: content,
                language,
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
            });
            setEditorInstance(instance);
        }

        return () => {
            if (editorInstance) {
                editorInstance.dispose();
            }
        };
    }, [loading, activeIsGraphql, editorInstance]);

    const handleCollapseAll = () => {
        if (editorInstance) editorInstance.getAction('editor.foldAll').run();
    };

    const handleExpandAll = () => {
        if (editorInstance) editorInstance.getAction('editor.unfoldAll').run();
    };

    if (loading) {
        return (
            <Box padding="5">
                <Card padding="6">
                    <HorizontalStack align="center" gap="2">
                        <Spinner size="small" />
                        <Text>Loading schema...</Text>
                    </HorizontalStack>
                </Card>
            </Box>
        );
    }

    if (!openApiSchema && !graphqlSchema) {
        return (
            <Box padding="5">
                <Card padding="6">
                    <Text>No schema data available</Text>
                </Card>
            </Box>
        );
    }

    const tabs = hasBoth ? [
        { id: 'openapi', content: 'OpenAPI' },
        { id: 'graphql', content: 'GraphQL' },
    ] : [];

    return (
        <Box padding="5">
            <VerticalStack gap="4">
                {hasBoth && (
                    <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
                )}

                {activeIsGraphql ? (
                    <Card padding="0">
                        <VerticalStack>
                            <Box padding="2" background="bg-surface-secondary">
                                <HorizontalStack gap="2">
                                    <Text variant="headingSm" fontWeight="semibold">GraphQL Schema</Text>
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
                ) : (
                    <>
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

                        <HorizontalStack gap="4" wrap={false}>
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
                                                spec={openApiSchema}
                                                docExpansion="list"
                                                defaultModelsExpandDepth={-1}
                                                displayRequestDuration={true}
                                                filter={true}
                                                showExtensions={true}
                                                showCommonExtensions={true}
                                                tryItOutEnabled={false}
                                            />
                                            <style>{`
                                                .swagger-ui .info { display: none; }
                                                .swagger-ui .filter-container { display: none; }
                                            `}</style>
                                        </div>
                                    </VerticalStack>
                                </Card>
                            </Box>
                        </HorizontalStack>
                    </>
                )}
            </VerticalStack>
        </Box>
    );
}

export default SchemaView;
