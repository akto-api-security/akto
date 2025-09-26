
import { useState, useEffect } from 'react';
import { Box, Text, VerticalStack, HorizontalStack, Card, Badge, Button, Icon, Avatar } from '@shopify/polaris';
import ReactFlow, { Background, Handle, Position, getBezierPath } from 'react-flow-renderer';
import TooltipText from '../../../components/shared/TooltipText';
import { CustomersMinor, AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import agentDiscoveryData from './AgentDiscoveryDummyData';
import MCPIcon from "@/assets/MCP_Icon.svg"

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

  // Simplified dummy data with only 3 components
  const dummyData = agentDiscoveryData[apiCollectionId + ""] || {};

  if (dummyData?.components == undefined ||
    dummyData?.components.length === 0) {
    return <></>
  }

  const [selectedComponent, setSelectedComponent] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);

  const nodeTypes = { agentNode: AgentNode };
  const edgeTypes = { agentEdge: AgentEdge };

  const handleNodeClick = (component) => {
    setSelectedComponent(selectedComponent?.id === component.id ? null : component);
  };

  const getStatusBadge = (status) => {
    const statusColors = {
      active: 'success',
      warning: 'warning',
      connected: 'info'
    };
    return <Badge status={statusColors[status] || 'info'}>{status}</Badge>;
  };

  const getCategoryStats = () => {
    const stats = {};
    dummyData.components.forEach(component => {
      stats[component.type] = (stats[component.type] || 0) + 1;
    });
    return stats;
  };

  const formatNodesAndEdges = () => {
    const formattedNodes = [];
    const formattedEdges = [];

    // Add all component nodes with fixed positions
    dummyData.components.forEach(component => {
      formattedNodes.push({
        id: component.id,
        type: 'agentNode',
        data: { 
          component: component, 
          onNodeClick: handleNodeClick 
        },
        position: { x: component.x, y: component.y },
        draggable: false // Make nodes non-draggable for fixed positioning
      });
    });

    // Create simple linear connections: User -> AI Agent -> LLM Call
    formattedEdges.push({
      id: 'user-to-ai',
      source: 'user',
      target: 'ai-agent',
      type: 'agentEdge',
      data: {
        connectionType: 'User Request',
        isExternal: false
      }
    });

    formattedEdges.push({
      id: 'ai-to-llm',
      source: 'ai-agent',
      target: 'llm-call',
      type: 'agentEdge',
      data: {
        connectionType: 'LLM Request',
        isExternal: false
      }
    });

    formattedEdges.push({
      id: 'ai-to-mcp',
      source: 'ai-agent',
      target: 'mcp',
      type: 'agentEdge',
      data: {
        connectionType: 'MCP',
        isExternal: false
      }
    });

    formattedEdges.push({
      id: 'mcp-to-api',
      source: 'mcp',
      target: 'external',
      type: 'agentEdge',
      data: {
        connectionType: 'MCP',
        isExternal: false
      }
    });

    return { nodes: formattedNodes, edges: formattedEdges };
  };

  useEffect(() => {
    const { nodes: formattedNodes, edges: formattedEdges } = formatNodesAndEdges();
    setNodes(formattedNodes);
    setEdges(formattedEdges);
  }, []);

  return (
    <Card>
      <Box padding="4">
        <VerticalStack gap="4">
          <HorizontalStack align="space-between">
            <Text variant="headingMd">Architecture</Text>
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