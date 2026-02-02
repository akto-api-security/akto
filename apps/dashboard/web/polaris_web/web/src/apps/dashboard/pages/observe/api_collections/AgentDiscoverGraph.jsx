
import { useState, useEffect, useCallback } from 'react';
import { Box, Text, VerticalStack, HorizontalStack, Card, Badge, Icon, Avatar, Spinner } from '@shopify/polaris';
import ReactFlow, { Background, Handle, Position, getBezierPath } from 'react-flow-renderer';
import TooltipText from '../../../components/shared/TooltipText';
import { CustomersMinor, AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import api from './api';

// Custom Node Component following ApiDependencyNode pattern
function AgentNode({ data }) {
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
}

// Custom Edge Component following ApiDependencyEdge pattern
function AgentEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, data }) {
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
}

function AgentDiscoverGraph({ apiCollectionId }) {

  const [selectedComponent, setSelectedComponent] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [collectionData, setCollectionData] = useState(null);
  const [serviceGraphEdges, setServiceGraphEdges] = useState({});

  const nodeTypes = { agentNode: AgentNode };
  const edgeTypes = { agentEdge: AgentEdge };

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

  const getCategoryStats = () => {
    if (!serviceGraphEdges || Object.keys(serviceGraphEdges).length === 0) {
      return {};
    }
    const stats = {
      'services': Object.keys(serviceGraphEdges).length + 1 // +1 for source service
    };
    return stats;
  };

  // Fetch collection data
  useEffect(() => {
    const fetchCollectionData = async () => {
      setLoading(true);
      try {
        const response = await api.getCollection(apiCollectionId);
        if (response && response.apiCollection) {
          setCollectionData(response.apiCollection);
          setServiceGraphEdges(response.apiCollection.serviceGraphEdges || {});
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

  // Update nodes and edges when serviceGraphEdges changes
  useEffect(() => {
    const formattedNodes = [];
    const formattedEdges = [];

    if (collectionData && serviceGraphEdges && Object.keys(serviceGraphEdges).length > 0) {
      const sourceServiceName = collectionData.name || collectionData.hostName || 'Current Service';

      // Add source node (current service)
      formattedNodes.push({
        id: 'source',
        type: 'agentNode',
        data: {
          component: {
            id: 'source',
            label: sourceServiceName,
            type: 'Service',
            category: 'agent',
            description: 'Current API Collection',
            status: 'active'
          },
          onNodeClick: handleNodeClick
        },
        position: { x: 50, y: 150 },
        draggable: false
      });

      // Add target nodes and edges from serviceGraphEdges
      let yOffset = 50;
      Object.entries(serviceGraphEdges).forEach(([targetService, edgeInfo], index) => {
        const targetId = `target-${index}`;

        // Add target service node
        formattedNodes.push({
          id: targetId,
          type: 'agentNode',
          data: {
            component: {
              id: targetId,
              label: targetService,
              type: 'Target Service',
              category: 'external',
              description: `Requests: ${edgeInfo.requestCount || 0}`,
              status: 'connected',
              requestCount: edgeInfo.requestCount,
              lastSeen: edgeInfo.lastSeenTimestamp
            },
            onNodeClick: handleNodeClick
          },
          position: { x: 400, y: yOffset + (index * 120) },
          draggable: false
        });

        // Add edge from source to target
        formattedEdges.push({
          id: `edge-${index}`,
          source: 'source',
          target: targetId,
          type: 'agentEdge',
          data: {
            connectionType: `${edgeInfo.requestCount || 0} requests`,
            isExternal: false,
            requestCount: edgeInfo.requestCount
          }
        });
      });
    }

    setNodes(formattedNodes);
    setEdges(formattedEdges);
  }, [serviceGraphEdges, collectionData, handleNodeClick]);

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
            <Text variant="headingMd">Service Dependencies</Text>
            <HorizontalStack gap="2">
              {Object.entries(getCategoryStats()).map(([category, count]) => (
                <Badge key={category} status="info">
                  {count} {category.replace('-', ' ')}
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
                
                {/* Internal System Boundary */}
                <div 
                  style={{
                    position: 'absolute',
                    left: '300px',
                    top: '130px',
                    right: '500px',
                    bottom: '150px',
                    border: '2px dashed #7c3aed',
                    borderRadius: '8px',
                    pointerEvents: 'none',
                    opacity: 0.8,
                    zIndex: 1
                  }}
                />

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