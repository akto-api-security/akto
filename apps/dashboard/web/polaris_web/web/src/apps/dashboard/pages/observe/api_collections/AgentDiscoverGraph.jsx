
import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { Box, Text, VerticalStack, HorizontalStack, Card, Badge, Icon, Avatar, Spinner } from '@shopify/polaris';
import ReactFlow, { Background, Handle, Position, getBezierPath } from 'react-flow-renderer';
import TooltipText from '../../../components/shared/TooltipText';
import { CustomersMinor, AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import api from './api';

// Map metadata type to node category and display info
const getNodeCategoryFromType = (type) => {
  const typeMap = {
    // LLM/AI Model types
    'lmChatAnthropic': { category: 'ai-model', type: 'LLM Call', description: 'Anthropic Chat Model' },
    'lmChatOpenAI': { category: 'ai-model', type: 'LLM Call', description: 'OpenAI Chat Model' },
    'lmChat': { category: 'ai-model', type: 'LLM Call', description: 'Language Model Call' },

    // MCP types
    'mcpClientTool': { category: 'mcp', type: 'MCP Server', description: 'MCP Client Tool' },
    'mcpServer': { category: 'mcp', type: 'MCP Server', description: 'MCP Server' },

    // Agent/Service types
    'respondToWebhook': { category: 'external', type: 'Target Service', description: 'Webhook Response' },
    'agenticComponent': { category: 'external', type: 'Agentic Component', description: 'Agentic Component Server' },

    // Default
    'default': { category: 'external', type: 'Target Service', description: 'External Service' }
  };

  return typeMap[type] || typeMap['default'];
};

// Custom Node Component following ApiDependencyNode pattern - memoized to prevent re-renders
const AgentNode = memo(function AgentNode({ data }) {
  const { component, onNodeClick } = data;

  const getComponentColors = (category) => {
    switch (category) {
      case 'external':
        return { borderColor: '#3b82f6', backgroundColor: '#eff6ff' }; // Blue
      case 'agent':
        return { borderColor: '#f97316', backgroundColor: '#fff7ed' }; // Orange
      case 'ai-model':
        return { borderColor: '#ec4899', backgroundColor: '#fdf2f8' }; // Pink
      case 'mcp':
        return { borderColor: '#4cbebbff', backgroundColor: '#ecfdf5' }; // Yellow-Green
      default:
        return { borderColor: '#6b7280', backgroundColor: '#f9fafb' }; // Gray
    }
  };

  const getComponentIcon = (category) => {
    switch (category) {
      case 'external':
        return CustomersMinor; // User icon
      case 'agent':
        return AutomationMajor; // AI Agent icon - automation/intelligent systems
      case 'ai-model':
        return MagicMajor; // LLM/AI Model icon - AI magic/processing
      case "mcp":
        return MCPIcon; // Custom MCP icon
      default:
        return CustomersMinor;
    }
  };

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
  const [show, setShow] = useState(false);
  const { connectionType, isExternal } = data || {};

  // Use getBezierPath for proper edge rendering
  const edgePath = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });

  return (
    <>
      <g
        onMouseEnter={() => setShow(true)}
        onMouseLeave={() => setShow(false)}
      >
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
            <path d="M0,0 L6,4 L0,8" fill='none' stroke={isExternal ? '#dc2626' : '#6b7280'} />
          </marker>
        </defs>
        <path 
          id={id} 
          style={{ 
            ...style, 
            stroke: isExternal ? '#dc2626' : '#6b7280',
            strokeWidth: isExternal ? '3' : '2',
            strokeDasharray: isExternal ? '5,5' : 'none',
            fill: 'none'
          }} 
          className="react-flow__edge-path" 
          d={edgePath} 
          markerEnd={`url(#arrow-${id})`} 
        />
        {show && (
          <foreignObject
            x={sourceX - 75}
            y={sourceY - 30}
            width={150}
            height={60}
          >
            <div xmlns="http://www.w3.org/1999/xhtml" style={{
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              height: '100%',
              width: '100%'
            }}>
              <Card padding={3}>
                <VerticalStack gap={1}>
                  <Box width='150px'>
                    <Text color='subdued' variant='bodySm'>
                      Connection
                    </Text>
                  </Box>
                  <Box width='150px'>
                    <Text variant='bodySm'>
                      {connectionType || 'Data Flow'}
                    </Text>
                  </Box>
                </VerticalStack>
              </Card>
            </div>
          </foreignObject>
        )}
      </g>
    </>
  );
});

function AgentDiscoverGraph({ apiCollectionId }) {

  console.log("apiCollectionId", apiCollectionId);

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
      // Extract source service name from edges
      const firstEdge = Object.values(serviceGraphEdges)[0];
      const sourceServiceName = firstEdge?.sourceService || collectionData.name || collectionData.hostName || 'AI Agent';

      // Add source node (AI Agent) - positioned in CENTER inside the boundary box
      nodes.push({
        id: 'source',
        type: 'agentNode',
        data: {
          component: {
            id: 'source',
            label: sourceServiceName,
            type: 'AI Agent',
            category: 'agent',
            description: 'Central AI agent processing requests',
            status: 'active'
          },
          onNodeClick: handleNodeClick
        },
        position: { x: 100, y: 140 },
        draggable: false
      });

      // Add target nodes in linear layout to the right of AI Agent
      let yOffset = 50;
      Object.entries(serviceGraphEdges).forEach(([targetService, edgeInfo], index) => {
        const targetId = `target-${index}`;
        const metadataType = edgeInfo?.metadata?.type || 'default';
        const nodeInfo = getNodeCategoryFromType(metadataType);

        // Shorten label if it's too long (for cleaner display)
        const label = targetService.length > 30 ? targetService.substring(0, 27) + '...' : targetService;

        // Add target service node with proper category from type
        // MCP servers at x:500, others at x:800 (to the right of AI Agent)
        nodes.push({
          id: targetId,
          type: 'agentNode',
          data: {
            component: {
              id: targetId,
              label: label,
              type: nodeInfo.type,
              category: nodeInfo.category,
              description: nodeInfo.description,
              status: 'connected',
              requestCount: edgeInfo.requestCount,
              lastSeen: edgeInfo.lastSeenTimestamp
            },
            onNodeClick: handleNodeClick
          },
          position: { x: 500 + (nodeInfo.category === 'mcp' ? 0 : 150), y: yOffset + (index * 120) },
          draggable: false
        });

        // Add edge from source to target
        edges.push({
          id: `edge-${index}`,
          source: 'source',
          target: targetId,
          type: 'agentEdge',
          data: {
            connectionType: metadataType,
            isExternal: nodeInfo.category === 'external',
            requestCount: edgeInfo.requestCount
          }
        });
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
            <div style={{ height: "400px", border: '1px solid #e1e5e9', borderRadius: '8px', position: 'relative' }}>
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
                {nodes.some(n => n.data?.component?.category === 'agent') && (
                  <div
                    style={{
                      position: 'absolute',
                      left: '50px',
                      top: '80px',
                      width: '300px',
                      height: '180px',
                      border: '2px dashed #7c3aed',
                      borderRadius: '8px',
                      pointerEvents: 'none',
                      opacity: 0.6,
                      zIndex: 0
                    }}
                  />
                )}

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