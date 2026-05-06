
import { useState, useEffect, useCallback, useMemo, memo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Box, Text, VerticalStack, HorizontalStack, Card, Badge, Icon, Avatar, Spinner } from '@shopify/polaris';
import ReactFlow, { Background, Handle, Position, getBezierPath } from 'react-flow-renderer';
import TooltipText from '../../../components/shared/TooltipText';
import ShowListInBadge from '../../../components/shared/ShowListInBadge';
import api from './api';
import {
  getNodeCategoryFromType,
  getComponentColors,
  getComponentIcon,
  buildArcadeGraph,
  buildVSCodeGraph,
} from './agentGraphUtils';

// Hover panel for MCP and Agent Tool nodes
const McpHoverPanel = ({ metadata }) => {
  const tools = metadata?.toolsList || [];
  const lastTool = metadata?.lastToolInvoked;
  const endpointUrl = metadata?.endpointUrl;
  const responseData = metadata?.edgeParam?.data;
  const toolName = metadata?.toolName;
  const description = metadata?.description;

  return (

    <Card>
      <Box maxWidth='400px'>
        <VerticalStack gap="3">
          {endpointUrl && (
            <HorizontalStack gap="2">
              <Text variant="bodySm" fontWeight="semibold" color="subdued">Endpoint URL:</Text>
              <Text variant="bodySm" breakWord>{endpointUrl}</Text>
            </HorizontalStack>
          )}
          {toolName && (
            <HorizontalStack gap="2">
              <Text variant="bodySm" fontWeight="semibold" color="subdued">Tool Name:</Text>
              <Text variant="bodySm" color="success">{toolName}</Text>
            </HorizontalStack>
          )}
          {description && (
            <HorizontalStack gap="2">
              <Text variant="bodySm" fontWeight="semibold" color="subdued">Description:</Text>
              <Text variant="bodySm">{description}</Text>
            </HorizontalStack>
          )}
          {lastTool && (
            <HorizontalStack gap="2">
              <Text variant="bodySm" fontWeight="semibold" color="subdued">Last Tool Invoked:</Text>
              <Text variant='bodySm' color="success">{lastTool}</Text>
            </HorizontalStack>
          )}
          {responseData && (
            <VerticalStack gap="1">
              <Text variant="bodySm" fontWeight="semibold" color="subdued">Response From server:</Text>
              <Box background="bg-subdued" padding="1" borderRadius='1' overflowY="scroll" maxHeight="10rem">
                <Text variant="bodySm" breakWord>{responseData}</Text>
              </Box>
            </VerticalStack>
          )}
          {tools.length > 0 && (
            <VerticalStack gap="1">
              <Text variant="bodySm" fontWeight="semibold" color="subdued">
                Tools List({tools.length}):
              </Text>
              <HorizontalStack gap="1">
                {tools.map((tool) => (
                  <Text key={tool} variant="bodySm">{tool}, </Text>
                ))}
              </HorizontalStack>
            </VerticalStack>
          )}
        </VerticalStack>
      </Box>
    </Card>
  );
};

// Custom Node Component following ApiDependencyNode pattern - memoized to prevent re-renders
export const AgentNode = memo(function AgentNode({ data }) {
  const { component, onNodeClick } = data;
  const [panelPos, setPanelPos] = useState(null);
  const nodeRef = useRef(null);

  const colors = getComponentColors(component.category);
  const IconComponent = getComponentIcon(component.category);
  const isArcadeMcp = component.category === 'arcade-mcp';
  const isMcp = (component.category === 'mcp' || component.category === 'ai-tool') && component.metadata;

  const handleMouseEnter = useCallback(() => {
    if (!isMcp || !nodeRef.current) return;
    const rect = nodeRef.current.getBoundingClientRect();
    const panelWidth = 420;
    const panelHeight = 300;
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    // Use viewport-relative coords (position: fixed)
    let left = rect.right + 8;
    let top = rect.top;

    // Clamp horizontally: if panel would overflow right edge, show to the left of node
    if (left + panelWidth > viewportWidth) {
      left = rect.left - panelWidth - 8;
    }
    // Clamp left edge
    if (left < 8) left = 8;
    // Clamp vertically
    if (top + panelHeight > viewportHeight) {
      top = Math.max(8, viewportHeight - panelHeight);
    }

    setPanelPos({ top, left });
  }, [isMcp]);

  const handleMouseLeave = useCallback(() => {
    setPanelPos(null);
  }, []);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <Handle type="target" position={Position.Top} id="top" />
      <div style={{ position: 'relative' }}>
        <div
          onClick={() => onNodeClick && onNodeClick(component)}
          style={{ cursor: isMcp ? "pointer" : "default" }}
        >
          <VerticalStack gap={2}>
            <Card padding={0}>
              <div
                ref={nodeRef}
                onMouseEnter={handleMouseEnter}
                onMouseLeave={handleMouseLeave}
                style={{
                  border: `1px solid ${colors.borderColor}`,
                  borderRadius: '8px',
                  backgroundColor: colors.backgroundColor,
                  pointerEvents: 'all',
                }}
              >
                <Box padding={3}>
                  <VerticalStack gap={1}>
                    <Box width='140px'>
                      <TooltipText
                        tooltip={component.description}
                        text={component.type}
                        textProps={{ color: "subdued", variant: "bodySm" }}
                      />
                    </Box>
                    <HorizontalStack gap={1} align="center">
                      {typeof IconComponent === 'string' ?
                      <Avatar source={IconComponent} size={"extraSmall"} /> :
                      <Icon source={IconComponent} />}
                      <Box width="140px">
                        <Text variant="bodySm" color="base" breakWord>
                          {component.label}
                        </Text>
                      </Box>
                    </HorizontalStack>
                    {isArcadeMcp && component.mcpServers && component.mcpServers.length > 0 && (
                      <Box paddingBlockStart="1" width="140px">
                        <ShowListInBadge
                          itemsArr={component.mcpServers}
                          maxItems={2}
                          status="info"
                          maxWidth="140px"
                          itemWidth="35px"
                          useTooltip={true}
                          wrap
                        />
                      </Box>
                    )}
                  </VerticalStack>
                </Box>
              </div>
            </Card>
          </VerticalStack>
        </div>

        {isMcp && panelPos && createPortal(
          <div
            style={{
              position: 'fixed',
              top: panelPos.top,
              left: panelPos.left,
              zIndex: 9999,
              maxWidth: '420px',
            }}
          >
            <McpHoverPanel metadata={component.metadata} />
          </div>,
          document.body
        )}
      </div>
      <Handle type="source" position={Position.Right} id="b" />
      <Handle type="source" position={Position.Bottom} id="bottom" />
    </>
  );
});

// Custom Edge Component following ApiDependencyEdge pattern - memoized to prevent re-renders
export const AgentEdge = memo(function AgentEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, data }) {
  const { edgeParam } = data || {};
  let displayData = edgeParam;
  
  if(edgeParam instanceof Object) {
    displayData = edgeParam?.type 
  }

  // getBezierPath in react-flow-renderer v9 returns a plain string path
  const edgePath = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
  const pathD = Array.isArray(edgePath) ? edgePath[0] : edgePath;

  const midX = (sourceX + targetX) / 2;
  const midY = (sourceY + targetY) / 2;

  return (
    <g>
      <svg>
        <defs>
          <marker
            id={`arrow-${id}`}
            markerWidth="8"
            markerHeight="8"
            refX="6"
            refY="4"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path d="M0,0 L6,4 L0,8" fill="none" stroke="#6b7280" />
          </marker>
        </defs>
      </svg>
      <path
        id={id}
        style={{ ...style, stroke: '#6b7280', strokeWidth: 2, fill: 'none' }}
        className="react-flow__edge-path"
        d={pathD}
        markerEnd={`url(#arrow-${id})`}
      />
      {displayData && (
        <foreignObject x={midX - 95} y={midY - 12} width={190} height={28}>
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <Badge size="small" status="new">
              <TooltipText tooltip={displayData} text={displayData} />
            </Badge>
          </div>
        </foreignObject>
      )}
    </g>
  );
});

function AgentDiscoverGraph({ apiCollectionId }) {

  const [selectedComponent, setSelectedComponent] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [collectionData, setCollectionData] = useState(null);
  const [serviceGraphEdges, setServiceGraphEdges] = useState({});
  const [arcadeGraphData, setArcadeGraphData] = useState(null);
  const [vscodeGraphData, setVSCodeGraphData] = useState(null);

  // Memoize node and edge types to prevent re-creation on each render
  const nodeTypes = useMemo(() => ({ agentNode: AgentNode }), []);
  const edgeTypes = useMemo(() => ({ agentEdge: AgentEdge }), []);

  const handleNodeClick = useCallback((component) => {
    setSelectedComponent(prev => prev?.id === component.id ? null : component);
  }, []);

  const getStatusBadge = (status) => {
    const statusColors = {
      active: 'success',
      warning: 'warning',
      connected: 'info'
    };
    return <Badge status={statusColors[status] || 'info'}>{status}</Badge>;
  };

  // Memoize category stats calculation
  const categoryStats = useMemo(() => {
    if (!serviceGraphEdges || Object.keys(serviceGraphEdges).length === 0) {
      return {};
    }

    const stats = {
      'AI Agents': 1, // Source service
      'MCP Servers': 0,
      'AI Models': 0,
      'Agentic Components': 0
    };

    Object.values(serviceGraphEdges).forEach((edgeInfo) => {
      const metadataType = edgeInfo?.metadata?.type || 'default';
      const nodeInfo = getNodeCategoryFromType(metadataType);

      if (nodeInfo.category === 'mcp') {
        stats['MCP Servers']++;
      } else if (nodeInfo.category === 'ai-model') {
        stats['AI Models']++;
      } else if (nodeInfo.category === 'external') {
        stats['Agentic Components']++;
      }
    });

    // Filter out zero counts
    return Object.fromEntries(Object.entries(stats).filter(([_, count]) => count > 0));
  }, [serviceGraphEdges]);

  // Fetch collection data
  useEffect(() => {
    const fetchCollectionData = async () => {
      setLoading(true);
      try {
        const response = await api.getCollection(apiCollectionId);
        if (response && response.length > 0) {
          const apiCollection = response[0];
          setCollectionData(apiCollection);
          setServiceGraphEdges(apiCollection.serviceGraphEdges || {});

          // Check if this is an arcade.dev collection — metadata lives on serviceGraphEdges.ARCADE
          const arcadeEdge = (apiCollection.serviceGraphEdges || {})['ARCADE'];
          if (arcadeEdge) {
            const metadata = arcadeEdge.metadata || {};
            const mcpServers = metadata['mcp-server-names'] || [];
            const agentName = metadata['user-agent'] || 'AI Agent';
            setArcadeGraphData({ mcpServers, agentName });
            setVSCodeGraphData(null);
          } else {
            const tagsList = apiCollection?.tagsList || [];
            const hasMcpServer = tagsList.some(tag => tag.keyName === 'mcp-server');
            const hasBrowserLlm = tagsList.some(tag => tag.keyName === 'browser-llm');
            const hasGenAiOrAiAgent = tagsList.some(tag => tag.keyName === 'gen-ai' || tag.keyName === 'ai-agent');
            const source = (tagsList.find(tag => tag.keyName === 'source') || {}).value || null;
            const copilotEdge = (apiCollection.serviceGraphEdges || {})['Tool'];
            const agentLabel = copilotEdge?.sourceService || '';
            const shouldRenderAtlasGraph = copilotEdge && source === 'ENDPOINT';


            if (shouldRenderAtlasGraph) {
              setVSCodeGraphData({
                agentLabel,
                hasMcpServer,
                hasBrowserLlm,
                hasGenAiOrAiAgent,
              });
              setArcadeGraphData(null);
            } else {
              setVSCodeGraphData(null);
              setArcadeGraphData(null);
            }
          }
        }
      } catch (error) {
        console.error('Error fetching collection data:', error);
      } finally {
        setLoading(false);
      }
    };

    if (apiCollectionId) {
      fetchCollectionData();
    }
  }, [apiCollectionId]);

  const arcadeFormattedGraph = useMemo(() => {
    if (!arcadeGraphData) return null;
    return buildArcadeGraph({ ...arcadeGraphData, onNodeClick: handleNodeClick });
  }, [arcadeGraphData, handleNodeClick]);

  const vscodeFormattedGraph = useMemo(() => {
    if (!vscodeGraphData) return null;
    return buildVSCodeGraph({
      onNodeClick: handleNodeClick,
      agentLabel: vscodeGraphData.agentLabel,
      hasMcpServer: vscodeGraphData.hasMcpServer,
      hasBrowserLlm: vscodeGraphData.hasBrowserLlm,
      hasGenAiOrAiAgent: vscodeGraphData.hasGenAiOrAiAgent,
    });
  }, [vscodeGraphData, handleNodeClick]);

  // Memoize nodes and edges transformation to prevent unnecessary re-renders
  const { nodes: formattedNodes, edges: formattedEdges } = useMemo(() => {
    // If arcade graph is active, use it (linear chain)
    if (arcadeFormattedGraph) return arcadeFormattedGraph;
    // If VSCode graph is active, use hub-and-spoke data flow
    if (vscodeFormattedGraph) return vscodeFormattedGraph;

    const nodes = [];
    const edges = [];

    if (collectionData && serviceGraphEdges && Object.keys(serviceGraphEdges).length > 0) {
      const edgeKeys = Object.keys(serviceGraphEdges);

      // Collect all unique services (sources and targets) and map keys to service names
      const allServices = new Set();
      const serviceInfo = {}; // Store info about each service
      const serviceToKey = {}; // Map service name to key name (for display)

      // First pass: collect all services and map targetServices to keys
      const targetServices = new Set();
      Object.entries(serviceGraphEdges).forEach(([key, edgeInfo]) => {
        const source = edgeInfo.sourceService;
        const target = edgeInfo.targetService;

        allServices.add(source);
        allServices.add(target);

        // If key matches target, it's a direct mapping
        if (key === target) {
          targetServices.add(target);
          serviceToKey[target] = key;

          const metadataType = edgeInfo?.metadata?.type || 'default';
          serviceInfo[target] = {
            ...getNodeCategoryFromType(metadataType),
            displayName: key,
            isKey: true,
            requestCount: edgeInfo.requestCount,
            lastSeen: edgeInfo.lastSeenTimestamp,
            metadata: edgeInfo?.metadata,
          };
        }
      });

      // Second pass: map sourceServices to keys if they're not targetServices elsewhere
      Object.entries(serviceGraphEdges).forEach(([key, edgeInfo]) => {
        const source = edgeInfo.sourceService;
        const target = edgeInfo.targetService;

        // If source is not a targetService anywhere and not already mapped, map it to this key
        if (!targetServices.has(source) && !serviceToKey[source] && key !== target) {
          serviceToKey[source] = key;

          const metadataType = edgeInfo?.metadata?.type || 'default';
          serviceInfo[source] = {
            ...getNodeCategoryFromType(metadataType),
            displayName: key,
            isKey: true,
            requestCount: edgeInfo.requestCount,
            lastSeen: edgeInfo.lastSeenTimestamp,
            metadata: edgeInfo?.metadata,
          };
        }
      });

      // Build adjacency lists
      const outgoingEdges = {};
      const incomingEdges = {};

      Object.values(serviceGraphEdges).forEach((edgeInfo) => {
        const source = edgeInfo.sourceService;
        const target = edgeInfo.targetService;

        if (!outgoingEdges[source]) outgoingEdges[source] = [];
        if (!incomingEdges[target]) incomingEdges[target] = [];

        outgoingEdges[source].push(target);
        incomingEdges[target].push(source);
      });

      // Find central agent (not in keys, has both incoming and outgoing edges)
      const centralAgent = Array.from(allServices).find(service =>
        !edgeKeys.includes(service) &&
        incomingEdges[service]?.length > 0 &&
        outgoingEdges[service]?.length > 0
      ) || collectionData.name || 'AI Agent';

      // Organize services into three columns
      const leftNodes = [];  // Services that feed into agent
      const centerNodes = []; // Agent
      const rightNodes = []; // Services agent calls

      const serviceToNodeId = {};
      let nodeIndex = 0;

      // Process all services
      Array.from(allServices).forEach((service) => {
        const info = serviceInfo[service] || {
          category: 'internal',
          type: 'Internal Service',
          description: 'Internal Service',
          displayName: service,
          isKey: false
        };

        const nodeData = {
          serviceName: service,
          displayName: info.displayName || service,
          info,
          isAgent: service === centralAgent
        };

        if (service === centralAgent) {
          centerNodes.push(nodeData);
        } else if (outgoingEdges[service]?.includes(centralAgent)) {
          leftNodes.push(nodeData); // Feeds into agent
        } else {
          rightNodes.push(nodeData); // Agent calls this
        }
      });

      // Layout configuration
      const COLUMN_WIDTH = 350;
      const NODE_HEIGHT = 140;
      const VERTICAL_SPACING = 40;

      // Calculate positions for each column
      const createNodesForColumn = (columnNodes, xPosition, startY) => {
        columnNodes.forEach((nodeData, index) => {
          const { serviceName, displayName, info, isAgent } = nodeData;
          const nodeId = isAgent ? 'agent-0' : `node-${nodeIndex++}`;
          serviceToNodeId[serviceName] = nodeId;

          const yPosition = startY + (index * (NODE_HEIGHT + VERTICAL_SPACING));

          nodes.push({
            id: nodeId,
            type: 'agentNode',
            data: {
              component: {
                id: nodeId,
                label: displayName,
                type: isAgent ? 'AI Agent' : info.type,
                category: isAgent ? 'agent' : info.category,
                description: isAgent ? 'Central AI agent processing requests' : info.description,
                status: isAgent ? 'active' : 'connected',
                requestCount: info.requestCount,
                lastSeen: info.lastSeen,
                showBoundary: isAgent,
                boundaryColor: '#7c3aed',
                boundaryBg: 'rgba(124, 58, 237, 0.05)',
                metadata: info.metadata,
              },
              onNodeClick: handleNodeClick
            },
            position: { x: xPosition, y: yPosition },
            draggable: false
          });
        });
      };

      // Calculate starting Y positions to center each column
      const containerHeight = 400;
      const leftHeight = leftNodes.length * (NODE_HEIGHT + VERTICAL_SPACING) - VERTICAL_SPACING;
      const centerHeight = NODE_HEIGHT;
      const rightHeight = rightNodes.length * (NODE_HEIGHT + VERTICAL_SPACING) - VERTICAL_SPACING;

      const leftStartY = Math.max(40, (containerHeight - leftHeight) / 2);
      const centerStartY = Math.max(40, (containerHeight - centerHeight) / 2);
      const rightStartY = Math.max(40, (containerHeight - rightHeight) / 2);

      // Create nodes in three columns
      createNodesForColumn(leftNodes, 80, leftStartY);           // Left column
      createNodesForColumn(centerNodes, 80 + COLUMN_WIDTH, centerStartY);  // Center column (agent)
      createNodesForColumn(rightNodes, 80 + (COLUMN_WIDTH * 2), rightStartY); // Right column

      // Create edges based on sourceService -> targetService relationships
      let edgeIndex = 0;
      Object.values(serviceGraphEdges).forEach((edgeInfo) => {
        const sourceService = edgeInfo.sourceService;
        const targetService = edgeInfo.targetService;

        const sourceNodeId = serviceToNodeId[sourceService];
        const targetNodeId = serviceToNodeId[targetService];

        if (sourceNodeId && targetNodeId) {
          edges.push({
            id: `edge-${edgeIndex}`,
            source: sourceNodeId,
            target: targetNodeId,
            type: 'agentEdge',
            data: {
              connectionType: edgeInfo?.metadata?.type || 'default',
              edgeParam: edgeInfo?.metadata?.edgeParam,
              requestCount: edgeInfo.requestCount,
              metadata: edgeInfo?.metadata
            }
          });
          edgeIndex++;
        }
      });
    }

    return { nodes, edges };
  }, [arcadeFormattedGraph, vscodeFormattedGraph, serviceGraphEdges, collectionData, handleNodeClick]);

  // Update state only when memoized values change
  useEffect(() => {
    setNodes(formattedNodes);
    setEdges(formattedEdges);
  }, [formattedNodes, formattedEdges]);

  // Show loading spinner
  if (loading) {
    return (
      <Card>
        <Box padding="4">
          <HorizontalStack align="center" blockAlign="center">
            <Spinner size="large" />
            <Text variant="bodyMd" color="subdued">Loading service graph...</Text>
          </HorizontalStack>
        </Box>
      </Card>
    );
  }

  // Don't render if no data at all
  if (!arcadeGraphData && !vscodeGraphData && (!serviceGraphEdges || Object.keys(serviceGraphEdges).length === 0)) {
    return null;
  }

  // Calculate dynamic height (VSCode needs more vertical space for elongated edges)
  const dynamicHeight = arcadeGraphData
    ? 400
    : vscodeGraphData
      ? 520
      : Math.max(420, Math.min(900, Object.keys(serviceGraphEdges).length * 110 + 200));

  return (
    <Card>
      <Box padding="4">
        <VerticalStack gap="4">
          <HorizontalStack align="space-between">
            <Text variant="headingMd">Architecture</Text>
            <HorizontalStack gap="2">
              {arcadeGraphData ? (
                <>
                  <Badge status="info">1 AI Agent</Badge>
                  <Badge status="info">{arcadeGraphData.mcpServers.length} MCP Servers</Badge>
                </>
              ) : vscodeGraphData ? (
                <>
                  <Badge status="info">1 User</Badge>
                  <Badge status="info">1 LLM</Badge>
                  <Badge status="info">1 Guardrail</Badge>
                  <Badge status="info">1 Tool Call</Badge>
                </>
              ) : (
                Object.entries(categoryStats).map(([category, count]) => (
                  <Badge key={category} status="info">
                    {count} {category}
                  </Badge>
                ))
              )}
            </HorizontalStack>
          </HorizontalStack>

          <VerticalStack gap="2">
            <div style={{ height: `${dynamicHeight}px`, border: '1px solid #e1e5e9', borderRadius: '8px', position: 'relative' }}>
              <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                fitView={false}
                attributionPosition="bottom-left"
                style={{ background: '#f8fafc' }}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                panOnDrag={false}
                zoomOnScroll={false}
                panOnScroll={false}
                zoomOnPinch={false}
                zoomOnDoubleClick={false}
                preventScrolling={true}
                defaultViewport={{ x: 0, y: 0, zoom: 1 }}
              >
                <Background color="#e1e5e9" gap={16} />

                {/* Dashed boundary — rendered for any node with component.showBoundary = true */}
                {nodes.filter(n => n.data?.component?.showBoundary).map(n => (
                  <div
                    key={`boundary-${n.id}`}
                    style={{
                      position: 'absolute',
                      left: `${n.position.x - 20}px`,
                      top: `${Math.max(20, n.position.y - 20)}px`,
                      width: '250px',
                      height: '130px',
                      border: `2px dashed ${n.data.component.boundaryColor || '#7c3aed'}`,
                      borderRadius: '12px',
                      pointerEvents: 'none',
                      opacity: 0.5,
                      zIndex: 0,
                      backgroundColor: n.data.component.boundaryBg || 'rgba(124, 58, 237, 0.05)'
                    }}
                  />
                ))}

              </ReactFlow>
            </div>
          </VerticalStack>

          {/* Selected Component Details */}
          {selectedComponent && (
            <Card background="surface-neutral">
              <Box padding="4">
                <VerticalStack gap="3">
                  <HorizontalStack align="space-between">
                    <Text variant="headingSm">
                      {selectedComponent.label}
                    </Text>
                    {getStatusBadge(selectedComponent.status)}
                  </HorizontalStack>
                  
                  <Text variant="bodySm" color="subdued">
                    {selectedComponent.description}
                  </Text>
                  
                  <HorizontalStack gap="4">
                    <Text variant="bodySm">
                      <strong>Type:</strong> {selectedComponent.type}
                    </Text>
                    <Text variant="bodySm">
                      <strong>Category:</strong> {selectedComponent.category?.replace('-', ' ')}
                    </Text>
                    {selectedComponent.isExternal && (
                      <Badge status="warning">External</Badge>
                    )}
                    {selectedComponent.isUntrusted && (
                      <Badge status="critical">Untrusted</Badge>
                    )}
                  </HorizontalStack>

                  {selectedComponent.id === 'ai-agent' && (
                    <Box background="surface-info" padding="3" borderRadius="2">
                      <Text variant="bodySm">
                        <strong>AI Agent:</strong> This component processes user requests and coordinates 
                        with external services like LLM calls. It operates within the internal system 
                        boundary for security.
                      </Text>
                    </Box>
                  )}
                </VerticalStack>
              </Box>
            </Card>
          )}
        </VerticalStack>
      </Box>
    </Card>
  );
}

export default AgentDiscoverGraph;