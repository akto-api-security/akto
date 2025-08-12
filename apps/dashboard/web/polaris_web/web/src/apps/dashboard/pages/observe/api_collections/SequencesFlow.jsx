import React, { useState, useCallback, useEffect } from 'react';
import ReactFlow, {
  Background,
  MarkerType,
  getBezierPath,
} from 'react-flow-renderer';
import 'react-flow-renderer/dist/style.css';
import api from '../api';
import { Card, Text, VerticalStack, Box, HorizontalStack } from '@shopify/polaris';
import GetPrettifyEndpoint from '../GetPrettifyEndpoint';

const initialNodes = [
  {
    id: '1',
    data: { label: 'Start' },
    position: { x: 250, y: 25 },
    style: {
      background: '#5c6ac4',
      color: 'white',
      border: 'none',
      borderRadius: '8px',
      padding: '10px 15px',
      fontSize: '14px',
      fontWeight: '500',
    },
  },
];

const initialEdges = [];

// Custom edge component with arrows
const SequencesEdge = ({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, data }) => {
  const edgePath = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
  const { label } = data;

  return (
    <g>
      <svg>
        <defs>
          <marker id="arrow" markerWidth="14" markerHeight="14" refX="9" refY="7" orient="auto" markerUnits="strokeWidth">
            <path d="M0,0 L9,7 L0,14" fill='none' stroke='grey' />
          </marker>
        </defs>
      </svg>
      <path 
        id={id} 
        style={{ ...style }} 
        className="react-flow__edge-path" 
        d={edgePath} 
        markerEnd="url(#arrow)" 
      />
      {label && (
        <text>
          <textPath href={`#${id}`} style={{ fontSize: '12px', fill: '#666' }} startOffset="50%" textAnchor="middle">
            {label}
          </textPath>
        </text>
      )}
    </g>
  );
};

// Custom node component with colored method names
const SequencesNode = ({ data }) => {
  const { method, url, methodColor } = data;
  
  return (
    <div style={{ 
      background: '#ffffff', 
      border: '1px solid #ccc', 
      borderRadius: '4px', 
      padding: '10px',
      minWidth: '200px',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
    }}>
      <div style={{ 
        display: 'flex', 
        flexDirection: 'column', 
        gap: '4px' 
      }}>
        <GetPrettifyEndpoint 
          method={method} 
          url={url} 
          isNew={false}
          maxWidth="180px"
          methodBoxWidth="54px"
        />
      </div>
    </div>
  );
};

const nodeTypes = { sequencesNode: SequencesNode };
const edgeTypes = { sequencesEdge: SequencesEdge };

function SequencesFlow({ apiCollectionId }) {
  const [nodes, setNodes] = useState(initialNodes);
  const [edges, setEdges] = useState(initialEdges);
  const [loading, setLoading] = useState(true);
  const [apiSequences, setApiSequences] = useState([]);

  useEffect(() => {
    if (apiCollectionId) {
      fetchApiSequences();
    } else {
      // Test with simple mock data for development
      console.log('Testing with simple mock data');
      const testNodes = [
        {
          id: '1',
          type: 'sequencesNode',
          data: { method: 'GET', url: '/test1' },
          position: { x: 100, y: 100 }
        },
        {
          id: '2', 
          type: 'sequencesNode',
          data: { method: 'POST', url: '/test2' },
          position: { x: 400, y: 100 }
        }
      ];
      
      const testEdges = [
        {
          id: 'e1-2',
          source: '1',
          target: '2',
          type: 'sequencesEdge',
          data: { label: 'Test Edge' }
        }
      ];
      
      setNodes(testNodes);
      setEdges(testEdges);
    }
  }, [apiCollectionId]);

  const fetchApiSequences = async () => {
    try {
      setLoading(true);
      const response = await api.getApiSequences(apiCollectionId);
      console.log('API Response:', response);
      
      if (response && response.apiSequences) {
        setApiSequences(response.apiSequences);
        renderSequencesGraph(response.apiSequences);
      } else {
        // Test with mock data if no real data
        console.log('No API sequences found, testing with mock data');
        const mockSequences = [
          {
            paths: [
              "111111999 https://api.example.com/login GET",
              "111111999 https://api.example.com/auth POST"
            ],
            transitionCount: 5,
            probability: 0.8
          },
          {
            paths: [
              "111111999 https://api.example.com/auth POST",
              "111111999 https://api.example.com/dashboard GET"
            ],
            transitionCount: 3,
            probability: 0.6
          }
        ];
        setApiSequences(mockSequences);
        renderSequencesGraph(mockSequences);
      }
    } catch (error) {
      console.error('Error fetching API sequences:', error);
    } finally {
      setLoading(false);
    }
  };

  const renderSequencesGraph = (sequences) => {
    console.log('renderSequencesGraph called with:', sequences);
    
    if (!sequences || sequences.length === 0) {
      console.log('No sequences found, using initial nodes/edges');
      setNodes(initialNodes);
      setEdges(initialEdges);
      return;
    }

    const newNodes = [];
    const newEdges = [];
    const nodeMap = new Map();
    let nodeId = 1;

    // Process all sequences and create nodes
    sequences.forEach((sequence, sequenceIndex) => {
      console.log(`Processing sequence ${sequenceIndex}:`, sequence);
      
      if (sequence.paths && sequence.paths.length > 0) {
        // Create nodes for each path in the sequence
        sequence.paths.forEach((path, pathIndex) => {
          const nodeKey = path;
          
          if (!nodeMap.has(nodeKey)) {
            // Extract method and URL from path structure "apiCollectionId url method"
            const pathParts = path.split(' ');
            const method = pathParts[pathParts.length - 1]; // Last part is method
            const url = pathParts.slice(1, -1).join(' '); // Middle parts are URL
            
            const node = {
              id: nodeId.toString(),
              type: 'sequencesNode',
              data: { 
                method: method,
                url: url,
                transitionCount: sequence.transitionCount,
                probability: sequence.probability
              },
              position: { x: sequenceIndex * 300, y: pathIndex * 200 }, // Simple positioning
            };
            newNodes.push(node);
            nodeMap.set(nodeKey, nodeId.toString());
            console.log(`Created node ${nodeId} for path:`, path, '->', node);
            nodeId++;
          } else {
            console.log(`Node already exists for path:`, path, '->', nodeMap.get(nodeKey));
          }
        });
      }
    });

    // Create edges between consecutive paths in each sequence
    console.log('NodeMap:', nodeMap);
    console.log('All nodes created:', newNodes.map(n => ({ id: n.id, data: n.data })));
    
    const createdEdges = new Set(); // Track created edges to avoid duplicates
    
    sequences.forEach((sequence, sequenceIndex) => {
      if (sequence.paths && sequence.paths.length > 1) {
        // Create edges between consecutive paths in the sequence
        for (let i = 0; i < sequence.paths.length - 1; i++) {
          const sourcePath = sequence.paths[i];
          const targetPath = sequence.paths[i + 1];
          const sourceNodeId = nodeMap.get(sourcePath);
          const targetNodeId = nodeMap.get(targetPath);
          
          console.log(`Edge ${i}: ${sourcePath} -> ${targetPath}`);
          console.log(`Node IDs: ${sourceNodeId} -> ${targetNodeId}`);
          
          // Verify that both nodes exist in the nodes array
          const sourceNodeExists = newNodes.find(n => n.id === sourceNodeId);
          const targetNodeExists = newNodes.find(n => n.id === targetNodeId);
          
          if (sourceNodeExists && targetNodeExists) {
            const edgeId = `e${sourceNodeId}-${targetNodeId}`;
            
            // Check if this edge already exists
            if (!createdEdges.has(edgeId)) {
              const edge = {
                id: edgeId,
                source: sourceNodeId,
                target: targetNodeId,
                type: 'sequencesEdge',
                animated: false,
                data: {
                  label: `${sequence.transitionCount} (${(sequence.probability * 100).toFixed(1)}%)`
                }
              };
              newEdges.push(edge);
              createdEdges.add(edgeId);
              console.log('Created edge:', edge);
            } else {
              console.log('Edge already exists:', edgeId);
            }
          } else {
            console.log('Missing nodes for edge:', {
              sourcePath,
              targetPath,
              sourceNodeId,
              targetNodeId,
              sourceNodeExists: !!sourceNodeExists,
              targetNodeExists: !!targetNodeExists
            });
          }
        }
      }
    });

    console.log('Final nodes:', newNodes);
    console.log('Final edges:', newEdges);

    setNodes(newNodes);
    setEdges(newEdges);
  };

  const SequencesBanner = () => {
    const totalSequences = apiSequences.length;
    const totalTransitions = apiSequences.reduce((sum, seq) => sum + seq.transitionCount, 0);
    const avgProbability = totalSequences > 0 ? (apiSequences.reduce((sum, seq) => sum + seq.probability, 0) / totalSequences * 100).toFixed(1) : 0;

    return (
      <Card padding="6">
        <VerticalStack gap="4">
          <HorizontalStack align="space-between">
            <Text variant="headingMd" fontWeight="semibold">API Sequences Overview</Text>
          </HorizontalStack>
          <HorizontalStack gap="24">
            <Box>
              <Text variant="bodyMd" color="subdued">Total Sequences</Text>
              <Text variant="headingLg" fontWeight="bold">{totalSequences}</Text>
            </Box>
            <Box>
              <Text variant="bodyMd" color="subdued">Total Transitions</Text>
              <Text variant="headingLg" fontWeight="bold">{totalTransitions}</Text>
            </Box>
            <Box>
              <Text variant="bodyMd" color="subdued">Avg Probability</Text>
              <Text variant="headingLg" fontWeight="bold">{avgProbability}%</Text>
            </Box>
          </HorizontalStack>
        </VerticalStack>
      </Card>
    );
  };

  if (loading) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <p>Loading API sequences...</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px' }}>
      <SequencesBanner />
      
      {apiSequences.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>No API sequences found for this collection</p>
        </div>
      ) : (
        <div style={{ height: "600px", marginTop: '20px' }}>
          <ReactFlow 
            nodes={nodes} 
            edges={edges}
            fitView
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            defaultEdgeOptions={{
              type: 'sequencesEdge',
              animated: false,
            }}
          >
            <Background color="#aaa" gap={6} />
          </ReactFlow>
        </div>
      )}
    </div>
  );
}

export default SequencesFlow;
