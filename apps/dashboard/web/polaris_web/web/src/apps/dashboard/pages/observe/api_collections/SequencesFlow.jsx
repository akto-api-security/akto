import React, { useState, useCallback, useEffect } from 'react';
import ReactFlow, {
  Background,
  MarkerType,
  getBezierPath,
  Handle,
  Position
} from 'react-flow-renderer';
import 'react-flow-renderer/dist/style.css';
import api from '../api';
import { Card, Text, VerticalStack, Box, HorizontalStack } from '@shopify/polaris';
import GetPrettifyEndpoint from '../GetPrettifyEndpoint';

const initialNodes = [];
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
  const { method, url } = data;
  return (
    <div style={{ 
      background: '#ffffff', 
      border: '1px solid #ccc', 
      borderRadius: '4px', 
      padding: '10px',
      minWidth: '200px',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
    }}>
      <Handle type="target" position={Position.Top} id="t" style={{ background: '#555' }} />
      <GetPrettifyEndpoint
        method={method}
        url={url}
        isNew={false}
        maxWidth="180px"
        methodBoxWidth="54px"
      />
      <Handle type="source" position={Position.Bottom} id="b" style={{ background: '#555' }} />
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
      setNodes([]);
      setEdges([]);
      setLoading(false);
    }
  }, [apiCollectionId]);

  const fetchApiSequences = async () => {
    try {
      setLoading(true);
      const response = await api.getApiSequences(apiCollectionId);
      if (response && Array.isArray(response.apiSequences) && response.apiSequences.length > 0) {
        setApiSequences(response.apiSequences);
        renderSequencesGraph(response.apiSequences);
      } else {
        setApiSequences([]);
        setNodes([]);
        setEdges([]);
      }
    } catch (error) {
      console.error('Error fetching API sequences:', error);
    } finally {
      setLoading(false);
    }
  };

  const renderSequencesGraph = (sequences) => {
    const newNodes = [];
    const newEdges = [];
    const nodeMap = new Map();
    let nodeId = 1;
    const createdEdges = new Set();

    sequences.forEach((sequence, seqIndex) => {
      sequence.paths.forEach((path, pathIndex) => {
        if (!nodeMap.has(path)) {
          const parts = path.trim().split(' ');
          const method = parts[parts.length - 1];
          const url = parts.slice(1, -1).join(' ');
          nodeMap.set(path, nodeId.toString());
          newNodes.push({
            id: nodeId.toString(),
            type: 'sequencesNode',
            data: { method, url },
            position: { x: seqIndex * 300, y: pathIndex * 200 }
          });
          nodeId++;
        }
      });
      for (let i = 0; i < sequence.paths.length - 1; i++) {
        const sourcePath = sequence.paths[i];
        const targetPath = sequence.paths[i + 1];
        const sourceId = nodeMap.get(sourcePath);
        const targetId = nodeMap.get(targetPath);
        if (sourceId && targetId) {
          const edgeId = `e${sourceId}-${targetId}`;
          if (!createdEdges.has(edgeId)) {
            newEdges.push({
              id: edgeId,
              source: sourceId,
              target: targetId,
              sourceHandle: 'b',
              targetHandle: 't',
              type: 'sequencesEdge',
              animated: false,
              data: {
                label: `${sequence.transitionCount} (${(sequence.probability * 100).toFixed(1)}%)`
              }
            });
            createdEdges.add(edgeId);
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
