
import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { Box, Text, VerticalStack, HorizontalStack, Card, Badge, Icon, Avatar, Spinner } from '@shopify/polaris';
import ReactFlow, { Background, Handle, Position, getBezierPath } from 'react-flow-renderer';
import TooltipText from '../../../components/shared/TooltipText';
import api from './api';
import {
  getNodeCategoryFromType,
  getComponentColors,
  getComponentIcon
} from './agentGraphUtils';

// Custom Node Component following ApiDependencyNode pattern - memoized to prevent re-renders
const AgentNode = memo(function AgentNode({ data }) {
  const { component, onNodeClick } = data;

  const colors = getComponentColors(component.category);
  const IconComponent = getComponentIcon(component.category);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <div onClick={() => onNodeClick && onNodeClick(component)} style={{ cursor: "pointer" }}>
        <VerticalStack gap={2}>
          <Card padding={0}>
            <div style={{
              border: `1px solid ${colors.borderColor}`,
              borderRadius: '8px',
              backgroundColor: colors.backgroundColor
            }}>
              <Box padding={3}>
                <VerticalStack gap={1}>
                  <Box width='150px'>
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
                    <Box width={component.category==="ai-model" ? "160px" : "110px"}>
                      <Text variant="bodySm" color="base">
                        {component.label}
                      </Text>
                    </Box>
                  </HorizontalStack>
                </VerticalStack>
              </Box>
            </div>
          </Card>
        </VerticalStack>
      </div>
      <Handle type="source" position={Position.Right} id="b" />
    </>
  );
});

// Custom Edge Component following ApiDependencyEdge pattern - memoized to prevent re-renders
const AgentEdge = memo(function AgentEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, data }) {
  const { edgeParam } = data || {};

  // Use getBezierPath for proper edge rendering
  const edgePath = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });

  // Calculate midpoint for label
  const midX = (sourceX + targetX) / 2;
  const midY = (sourceY + targetY) / 2;

  return (
    <>
      <g>
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
            <path d="M0,0 L6,4 L0,8" fill='none' stroke='#6b7280' />
          </marker>
        </defs>
        <path
          id={id}
          style={{
            ...style,
            stroke: '#6b7280',
            strokeWidth: '2',
            fill: 'none'
          }}
          className="react-flow__edge-path"
          d={edgePath}
          markerEnd={`url(#arrow-${id})`}
        />
        {edgeParam && (
          <foreignObject
            x={midX - 60}
            y={midY - 15}
            width={120}
            height={30}
          >
            <div style={{
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
            }}>
              <Badge size='small' status="new">
                <TooltipText tooltip={edgeParam} text={edgeParam} />
              </Badge>
            </div>
          </foreignObject>
        )}
      </g>
    </>
  );
});

function AgentDiscoverGraph({ apiCollectionId }) {

  const [selectedComponent, setSelectedComponent] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [collectionData, setCollectionData] = useState(null);
  const [serviceGraphEdges, setServiceGraphEdges] = useState({});

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

  // Memoize nodes and edges transformation to prevent unnecessary re-renders
  const { nodes: formattedNodes, edges: formattedEdges } = useMemo(() => {
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
            lastSeen: edgeInfo.lastSeenTimestamp
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
            lastSeen: edgeInfo.lastSeenTimestamp
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
      const COLUMN_WIDTH = 250;
      const NODE_HEIGHT = 140;
      const VERTICAL_SPACING = 40;

      // Calculate positions for each column
      const createNodesForColumn = (columnNodes, xPosition, startY) => {
        columnNodes.forEach((nodeData, index) => {
          const { serviceName, displayName, info, isAgent } = nodeData;
          const nodeId = isAgent ? 'agent-0' : `node-${nodeIndex++}`;
          serviceToNodeId[serviceName] = nodeId;

          const label = displayName.length > 30 ? displayName.substring(0, 27) + '...' : displayName;
          const yPosition = startY + (index * (NODE_HEIGHT + VERTICAL_SPACING));

          nodes.push({
            id: nodeId,
            type: 'agentNode',
            data: {
              component: {
                id: nodeId,
                label: label,
                type: isAgent ? 'AI Agent' : info.type,
                category: isAgent ? 'agent' : info.category,
                description: isAgent ? 'Central AI agent processing requests' : info.description,
                status: isAgent ? 'active' : 'connected',
                requestCount: info.requestCount,
                lastSeen: info.lastSeen
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
              requestCount: edgeInfo.requestCount
            }
          });
          edgeIndex++;
        }
      });
    }

    return { nodes, edges };
  }, [serviceGraphEdges, collectionData, handleNodeClick]);

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

  // Don't render if no serviceGraphEdges data
  if (!serviceGraphEdges || Object.keys(serviceGraphEdges).length === 0) {
    return null;
  }

  // Calculate dynamic height based on number of services
  const serviceCount = Object.keys(serviceGraphEdges).length;
  const dynamicHeight = Math.max(500, Math.min(800, serviceCount * 120 + 200));

  return (
    <Card>
      <Box padding="4">
        <VerticalStack gap="4">
          <HorizontalStack align="space-between">
            <Text variant="headingMd">Architecture</Text>
            <HorizontalStack gap="2">
              {Object.entries(categoryStats).map(([category, count]) => (
                <Badge key={category} status="info">
                  {count} {category}
                </Badge>
              ))}
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

                {/* Internal System Boundary - wraps around the AI Agent */}
                {nodes.some(n => n.data?.component?.category === 'agent') && (() => {
                  const agentNode = nodes.find(n => n.data?.component?.category === 'agent');
                  if (!agentNode) return null;

                  const agentX = agentNode.position.x;
                  const agentY = agentNode.position.y;

                  return (
                    <div
                      style={{
                        position: 'absolute',
                        left: `${agentX - 30}px`,
                        top: `${Math.max(20, agentY - 30)}px`,
                        width: '260px',
                        height: '200px',
                        border: '2px dashed #7c3aed',
                        borderRadius: '12px',
                        pointerEvents: 'none',
                        opacity: 0.5,
                        zIndex: 0,
                        backgroundColor: 'rgba(124, 58, 237, 0.05)'
                      }}
                    />
                  );
                })()}

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