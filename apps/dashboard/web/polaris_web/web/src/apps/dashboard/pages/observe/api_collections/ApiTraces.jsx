import { Box, Button, Card, HorizontalStack, Text, VerticalStack, Spinner, EmptyState, Divider } from "@shopify/polaris";
import { ChevronLeftMinor, ChevronRightMinor } from "@shopify/polaris-icons";
import React, { useState, useEffect } from 'react';
import ReactFlow, { Background, Controls } from 'react-flow-renderer';
import TraceSpanNode from "./TraceSpanNode";
import TraceSpanEdge from "./TraceSpanEdge";
import api from "../api";

const nodeTypes = { traceSpanNode: TraceSpanNode };
const edgeTypes = { traceSpanEdge: TraceSpanEdge };

function ApiTraces(props) {
    const { apiCollectionId } = props;

    const [traces, setTraces] = useState([]);
    const [selectedTraceIndex, setSelectedTraceIndex] = useState(0);
    const [nodes, setNodes] = useState([]);
    const [edges, setEdges] = useState([]);
    const [loading, setLoading] = useState(false);
    const [spansLoading, setSpansLoading] = useState(false);

    // Fetch traces on component mount
    const fetchTraces = async () => {
        setLoading(true);
        try {
            const resp = await api.fetchLatestTraces(apiCollectionId);
            if (resp && resp.traces && resp.traces.length > 0) {
                setTraces(resp.traces);
                // Automatically load first trace's spans
                if (resp.traces[0].id) {
                    await fetchSpans(resp.traces[0].id);
                }
            } else {
                setTraces([]);
            }
        } catch (error) {
            console.error("Error fetching traces:", error);
            setTraces([]);
        }
        setLoading(false);
    };

    // Fetch spans for a selected trace
    const fetchSpans = async (traceId) => {
        setSpansLoading(true);
        try {
            const resp = await api.fetchSpansForTrace(traceId);
            if (resp && resp.spans && resp.spans.length > 0) {
                const { nodes: formattedNodes, edges: formattedEdges } = formatSpanData(resp.spans);
                setNodes(formattedNodes);
                setEdges(formattedEdges);
            } else {
                setNodes([]);
                setEdges([]);
            }
        } catch (error) {
            console.error("Error fetching spans:", error);
            setNodes([]);
            setEdges([]);
        }
        setSpansLoading(false);
    };

    // Format span data for React Flow
    const formatSpanData = (spansData) => {
        const nodes = [];
        const edges = [];

        // Group spans by depth
        const depthGroups = {};
        spansData.forEach(span => {
            const depth = span.depth || 0;
            if (!depthGroups[depth]) {
                depthGroups[depth] = [];
            }
            depthGroups[depth].push(span);
        });

        const midPoint = 200;
        const horizontalSpacing = 280;
        const verticalSpacing = 180;

        // Create nodes for each span
        Object.keys(depthGroups).sort((a, b) => parseInt(a) - parseInt(b)).forEach(depth => {
            const spansAtDepth = depthGroups[depth];
            const numSpans = spansAtDepth.length;
            const startX = midPoint - (horizontalSpacing * (numSpans - 1)) / 2;

            spansAtDepth.forEach((span, index) => {
                const xPosition = startX + (index * horizontalSpacing);
                const yPosition = parseInt(depth) * verticalSpacing;

                // Check if span has children
                const hasChildren = spansData.some(s => s.parentSpanId === span.id);

                nodes.push({
                    id: span.id,
                    type: 'traceSpanNode',
                    data: {
                        name: span.name || 'Unnamed Span',
                        spanKind: span.spanKind || 'unknown',
                        output: span.output || {},
                        isRootNode: !span.parentSpanId,
                        hasChildren: hasChildren
                    },
                    position: { x: xPosition, y: yPosition }
                });

                // Create edge from parent to this span
                if (span.parentSpanId) {
                    edges.push({
                        id: `${span.parentSpanId}-${span.id}`,
                        source: span.parentSpanId,
                        target: span.id,
                        type: 'traceSpanEdge',
                        animated: false,
                        data: {
                            input: span.input || {}
                        }
                    });
                }
            });
        });

        return { nodes, edges };
    };

    // Handle trace navigation
    const handlePrevTrace = () => {
        if (selectedTraceIndex > 0) {
            const newIndex = selectedTraceIndex - 1;
            setSelectedTraceIndex(newIndex);
            fetchSpans(traces[newIndex].id);
        }
    };

    const handleNextTrace = () => {
        if (selectedTraceIndex < traces.length - 1) {
            const newIndex = selectedTraceIndex + 1;
            setSelectedTraceIndex(newIndex);
            fetchSpans(traces[newIndex].id);
        }
    };

    useEffect(() => {
        fetchTraces();
    }, [apiCollectionId]);

    const selectedTrace = traces[selectedTraceIndex];

    if (loading) {
        return (
            <Box padding="6">
                <VerticalStack gap="4" align="center">
                    <Spinner size="large" />
                    <Text variant="bodyMd">Loading traces...</Text>
                </VerticalStack>
            </Box>
        );
    }

    if (traces.length === 0) {
        return (
            <Box padding="6">
                <EmptyState
                    heading="No traces found"
                    image="https://cdn.shopify.com/s/files/1/0262/4071/2726/files/emptystate-files.png"
                >
                    <p>There are no execution traces available for this API collection yet.</p>
                </EmptyState>
            </Box>
        );
    }

    return (
        <VerticalStack gap="4">
            {/* Trace Navigation and Info */}
            <Card>
                <Box padding="4">
                    <VerticalStack gap="4">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingMd" as="h3">
                                Trace {selectedTraceIndex + 1} of {traces.length}
                            </Text>
                            <HorizontalStack gap="2">
                                <Button
                                    icon={ChevronLeftMinor}
                                    disabled={selectedTraceIndex === 0}
                                    onClick={handlePrevTrace}
                                />
                                <Button
                                    icon={ChevronRightMinor}
                                    disabled={selectedTraceIndex === traces.length - 1}
                                    onClick={handleNextTrace}
                                />
                            </HorizontalStack>
                        </HorizontalStack>

                        <Divider />

                        {selectedTrace && (
                            <VerticalStack gap="3">
                                <HorizontalStack gap="6" wrap>
                                    <Box minWidth="200px">
                                        <VerticalStack gap="1">
                                            <Text variant="bodySm" color="subdued" as="span">
                                                Trace Name
                                            </Text>
                                            <Text variant="bodyMd" fontWeight="semibold" as="p">
                                                {selectedTrace.name || 'Unnamed Trace'}
                                            </Text>
                                        </VerticalStack>
                                    </Box>

                                    <Box minWidth="200px">
                                        <VerticalStack gap="1">
                                            <Text variant="bodySm" color="subdued" as="span">
                                                Agent Name
                                            </Text>
                                            <Text variant="bodyMd" fontWeight="medium" as="p">
                                                {selectedTrace.aiAgentName || 'N/A'}
                                            </Text>
                                        </VerticalStack>
                                    </Box>

                                    <Box minWidth="120px">
                                        <VerticalStack gap="1">
                                            <Text variant="bodySm" color="subdued" as="span">
                                                Total Spans
                                            </Text>
                                            <Text variant="bodyMd" as="p">
                                                {selectedTrace.totalSpans || 0}
                                            </Text>
                                        </VerticalStack>
                                    </Box>

                                    <Box minWidth="200px">
                                        <VerticalStack gap="1">
                                            <Text variant="bodySm" color="subdued" as="span">
                                                Root Span ID
                                            </Text>
                                            <Text variant="bodySm" as="p" fontFamily="monospace">
                                                {selectedTrace.rootSpanId || 'N/A'}
                                            </Text>
                                        </VerticalStack>
                                    </Box>
                                </HorizontalStack>
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </Box>
            </Card>

            {/* Span Graph */}
            {spansLoading ? (
                <Box padding="6">
                    <VerticalStack gap="4" align="center">
                        <Spinner size="large" />
                        <Text variant="bodyMd">Loading span graph...</Text>
                    </VerticalStack>
                </Box>
            ) : nodes.length > 0 ? (
                <Card>
                    <div style={{ height: '600px', width: '100%', position: 'relative', border: '1px solid #e1e5e9', borderRadius: '8px' }}>
                        <ReactFlow
                            nodes={nodes}
                            edges={edges}
                            nodeTypes={nodeTypes}
                            edgeTypes={edgeTypes}
                            fitView
                            minZoom={0.1}
                            maxZoom={1.5}
                            style={{ background: '#f8fafc' }}
                        >
                            <Background color="#e1e5e9" gap={16} />
                            <Controls />
                        </ReactFlow>
                    </div>
                </Card>
            ) : (
                <Card>
                    <Box padding="6">
                        <EmptyState
                            heading="No spans available"
                            image="https://cdn.shopify.com/s/files/1/0262/4071/2726/files/emptystate-files.png"
                        >
                            <p>No spans were found for this trace.</p>
                        </EmptyState>
                    </Box>
                </Card>
            )}
        </VerticalStack>
    );
}

export default ApiTraces;
