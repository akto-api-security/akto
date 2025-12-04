import { useState, useEffect } from 'react';
import { Box, Text, VerticalStack, HorizontalStack, Card, Badge, Avatar } from '@shopify/polaris';
import ReactFlow, { Background, Handle, Position, getBezierPath } from 'react-flow-renderer';
import 'react-flow-renderer/dist/style.css';
import 'react-flow-renderer/dist/theme-default.css';
import { AutomationMajor, ApiMajor } from "@shopify/polaris-icons";
import api from '../api';
import MCPIcon from "@/assets/MCP_Icon.svg";
import func from '@/util/func';

// Custom Node Component for MCP Tools and APIs
function McpNode({ data }) {
  const { component, onNodeClick } = data;

  const getComponentColors = (type) => {
    switch (type) {
      case 'tool':
        return { borderColor: '#4cbebbff', backgroundColor: '#ecfdf5' }; // Teal for MCP tools
      case 'api':
        return { borderColor: '#3b82f6', backgroundColor: '#eff6ff' }; // Blue for APIs
      default:
        return { borderColor: '#6b7280', backgroundColor: '#f9fafb' }; // Gray
    }
  };

  const getComponentIcon = (type) => {
    switch (type) {
      case 'tool':
        return MCPIcon;
      case 'api':
        return ApiMajor;
      default:
        return AutomationMajor;
    }
  };

  const colors = getComponentColors(component.nodeType);
  const IconComponent = getComponentIcon(component.nodeType);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <Box style={{ cursor: "pointer" }}>
        <VerticalStack gap={2}>
          <Card padding={0}>
            <Box style={{
              border: `1px solid ${colors.borderColor}`,
              borderRadius: '8px',
              backgroundColor: colors.backgroundColor
            }}>
              <Box padding={3}>
                <VerticalStack gap={1}>
                  <Box width='150px'>
                    <Text color="subdued" variant="bodySm">
                      {component.type}
                    </Text>
                  </Box>
                  <HorizontalStack gap={2} align="center">
                    {typeof IconComponent === 'string' ? (
                      <img src={IconComponent} alt="icon" style={{ width: '16px', height: '16px' }} />
                    ) : component.nodeType === 'api' ? (
                      <Box style={{
                        borderRadius: '4px',
                        backgroundColor: '#3b82f6',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        padding: '3px 4px'
                      }}>
                        <Text variant='bodySm' color='text-inverse' fontWeight='bold' as="span">API</Text>
                      </Box>
                    ) : (
                      <AutomationMajor style={{ width: '16px', height: '16px' }} />
                    )}
                    <Box width="140px">
                      <Text variant="bodySm" color="base">
                        {component.label}
                      </Text>
                    </Box>
                  </HorizontalStack>
                </VerticalStack>
              </Box>
            </Box>
          </Card>
        </VerticalStack>
      </Box>
      <Handle type="source" position={Position.Right} id="b" />
    </>
  );
}

// Custom Edge Component
function McpEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, data }) {
  const [show, setShow] = useState(false);
  const { connectionType } = data || {};

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
        {show && (
          <foreignObject
            x={sourceX - 75}
            y={sourceY - 30}
            width={150}
            height={60}
          >
            <Box xmlns="http://www.w3.org/1999/xhtml" style={{
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              height: '100%',
              width: '100%'
            }}>
            </Box>
          </foreignObject>
        )}
      </g>
    </>
  );
}

function McpToolsGraph({ apiCollectionId }) {
  const [selectedComponent, setSelectedComponent] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toolApiCallsMap, setToolApiCallsMap] = useState({});
  const [toolsInGraphCount, setToolsInGraphCount] = useState(0);
  const [uniqueApisCount, setUniqueApisCount] = useState(0);

  const nodeTypes = { mcpNode: McpNode };
  const edgeTypes = { mcpEdge: McpEdge };

  const handleNodeClick = (event, node) => {
    const component = node.data?.component;
    if (component) {
      setSelectedComponent(selectedComponent?.id === component.id ? null : component);
    }
  };

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await api.fetchMcpToolsApiCalls(apiCollectionId);
        const data = response?.toolApiCallsMap || {};
        setToolApiCallsMap(data);
        formatNodesAndEdges(data);
      } catch (error) {
        func.setToast(true, true, "Error fetching MCP tools data");
      } finally {
        setLoading(false);
      }
    };

    if (apiCollectionId) {
      fetchData();
    }
  }, [apiCollectionId]);

  const formatNodesAndEdges = (data) => {
    const formattedNodes = [];
    const formattedEdges = [];

    // Convert the data object to array for easier processing
    const toolsArray = Object.entries(data);

    if (toolsArray.length === 0) {
      return;
    }

    // First, collect all unique APIs and which tools call them
    const apiToToolsMap = new Map(); // Map of API key -> array of tool info
    const toolsWithApiCalls = []; // Only tools that have API calls

    toolsArray.forEach(([toolKey, apiCalls], toolIndex) => {

      // Skip tools with no API calls
      if (!apiCalls || !Array.isArray(apiCalls) || apiCalls.length === 0) {
        return;
      }

      let toolKeyObj;

      if (typeof toolKey === 'string') {
        // Parse string format: "collectionId url method"
        const parts = toolKey.split(' ');
        if (parts.length >= 3) {
          const method = parts[parts.length - 1]; // Last part is method
          const url = parts.slice(1, -1).join(' '); // Middle parts are URL
          const collectionId = parts[0]; // First part is collection ID

          toolKeyObj = {
            url: url,
            method: method,
            collectionId: collectionId
          };
        } else {
          return; // Skip this tool
        }
      } else if (typeof toolKey === 'object') {
        toolKeyObj = toolKey;
      } else {
        try {
          // Try parsing as JSON
          toolKeyObj = JSON.parse(toolKey);
        } catch (e) {
          return; // Skip this tool
        }
      }

      const toolName = toolKeyObj.url ? toolKeyObj.url.split('/').pop() : 'Unknown Tool';

      toolsWithApiCalls.push({
        toolIndex,
        toolName,
        toolKeyObj,
        apiCalls
      });

      // Map APIs to tools
      apiCalls.forEach((apiCall) => {
        const apiKey = `${apiCall.method} ${apiCall.url} ${apiCall.apiCollectionId}`;
        if (!apiToToolsMap.has(apiKey)) {
          apiToToolsMap.set(apiKey, { apiCall, tools: [] });
        }
        apiToToolsMap.get(apiKey).tools.push({ toolIndex, toolName });
      });
    });

    // Create tool nodes on the left
    let toolYOffset = 100;
    const xSpacing = 400;
    const verticalSpacing = 120;

    toolsWithApiCalls.forEach(({ toolIndex, toolName, toolKeyObj }) => {
      const toolNodeId = `tool-${toolIndex}`;
      formattedNodes.push({
        id: toolNodeId,
        type: 'mcpNode',
        data: {
          component: {
            id: toolNodeId,
            label: toolName,
            fullUrl: toolKeyObj.url || 'Unknown',
            type: 'MCP Tool',
            nodeType: 'tool',
            status: 'active',
            method: 'TOOL'
          }
        },
        position: { x: 100, y: toolYOffset },
        draggable: false
      });

      toolYOffset += verticalSpacing;
    });

    // Create API nodes on the right (unique APIs only)
    let apiYOffset = 100;
    let apiIndex = 0;

    apiToToolsMap.forEach(({ apiCall, tools }) => {
      const apiNodeId = `api-${apiIndex}`;

      // Create a list of tool names for the tooltip
      const toolNames = tools.map(t => t.toolName).join(', ');

      formattedNodes.push({
        id: apiNodeId,
        type: 'mcpNode',
        data: {
          component: {
            id: apiNodeId,
            label: (apiCall.url || 'Unknown').substring(0, 30) + ((apiCall.url || '').length > 30 ? '...' : ''),
            fullUrl: apiCall.url || 'Unknown',
            type: 'API Call',
            description: `Called by: ${toolNames}`,
            nodeType: 'api',
            status: 'connected',
            method: apiCall.method
          }
        },
        position: { x: 100 + xSpacing, y: apiYOffset },
        draggable: false
      });

      // Create edges from each tool to this API
      tools.forEach(({ toolIndex }) => {
        formattedEdges.push({
          id: `edge-tool-${toolIndex}-api-${apiIndex}`,
          source: `tool-${toolIndex}`,
          target: apiNodeId,
          type: 'mcpEdge',
          data: {
            connectionType: 'API Call'
          }
        });
      });

      apiYOffset += verticalSpacing;
      apiIndex++;
    });

    // Update counts for badges
    setToolsInGraphCount(toolsWithApiCalls.length);
    setUniqueApisCount(apiToToolsMap.size);

    setNodes(formattedNodes);
    setEdges(formattedEdges);
  };

  if (loading) {
    return null;
  }

  if (Object.keys(toolApiCallsMap).length === 0) {
    return null;
  }

  // Don't show graph if no tools have API calls
  if (nodes.length === 0) {
    return null;
  }



  const getBadgeComponentColor = (type) => {
    switch (type) {
      case 'tool':
        return 'info';
      case 'api':
        return 'critical';
      default:
        return 'gray';
    }
  };

  return (
    <Card>
      <Box padding="4">
        <VerticalStack gap="4">
          <HorizontalStack align="space-between">
            <Text variant="headingMd">MCP Tools & API Calls</Text>
            <HorizontalStack gap="2">
              <Badge status="info">
                {toolsInGraphCount} {toolsInGraphCount === 1 ? 'Tool' : 'Tools'}
              </Badge>
              <Badge status="info">
                {uniqueApisCount} Unique {uniqueApisCount === 1 ? 'API' : 'APIs'}
              </Badge>
            </HorizontalStack>
          </HorizontalStack>

          <VerticalStack gap="2">
            <Box style={{ height: "500px", border: '1px solid #e1e5e9', borderRadius: '8px', position: 'relative' }}>
              <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                onNodeClick={handleNodeClick}
                fitView={true}
                attributionPosition="bottom-left"
                style={{ background: '#f8fafc' }}
                nodesDraggable={false}
                nodesConnectable={false}
                panOnDrag={true}
                zoomOnScroll={true}
                panOnScroll={false}
                zoomOnPinch={true}
                zoomOnDoubleClick={false}
                preventScrolling={true}
              >
                <Background color="#e1e5e9" gap={16} />
              </ReactFlow>
            </Box>
          </VerticalStack>

          {/* Selected Component Details */}
          {selectedComponent && (
            <Card background="surface-neutral">
              <Box padding="4">
                <VerticalStack gap="3">
                  <HorizontalStack align="space-between">
                    <Text variant="headingMd">
                      {selectedComponent.nodeType === 'api' ? 'API Endpoint Details' : 'MCP Tool Details'}
                    </Text>
                  </HorizontalStack>

                  {selectedComponent.fullUrl && (
                    <Box>
                      <VerticalStack gap="1">
                        <Text variant="bodySm" color="subdued">
                          <Text as="span" fontWeight="bold">Full URL:</Text>
                        </Text>
                        <Box
                          padding="2"
                          background="surface"
                          borderRadius="2"
                          style={{
                            wordBreak: 'break-all',
                            fontFamily: 'monospace',
                            fontSize: '12px'
                          }}
                        >
                          <Text variant="bodySm" as="span">
                            {selectedComponent.method && (
                              <Badge status={getBadgeComponentColor(selectedComponent.nodeType)} tone={getBadgeComponentColor(selectedComponent.nodeType)}>{selectedComponent.method}</Badge>
                            )}{' '}
                            {selectedComponent.fullUrl}
                          </Text>
                        </Box>
                      </VerticalStack>
                    </Box>
                  )}

                  <HorizontalStack gap="4">
                    <Text variant="bodySm">
                      <Text as="span" fontWeight="bold">Type:</Text> {selectedComponent.type}
                    </Text>
                    <Text variant="bodySm">
                      <Text as="span" fontWeight="bold">Node Type:</Text> {selectedComponent.nodeType}
                    </Text>
                  </HorizontalStack>
                </VerticalStack>
              </Box>
            </Card>
          )}
        </VerticalStack>
      </Box>
    </Card>
  );
}

export default McpToolsGraph;
