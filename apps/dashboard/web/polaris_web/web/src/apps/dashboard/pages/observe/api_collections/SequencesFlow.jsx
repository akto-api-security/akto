import React, { useState, useCallback, useEffect } from 'react';
import ReactFlow, {
  Background,
} from 'react-flow-renderer';
import 'react-flow-renderer/dist/style.css';
import api from '../api';

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

function SequencesFlow({ apiCollectionId }) {
  const [nodes, setNodes] = useState(initialNodes);
  const [edges, setEdges] = useState(initialEdges);
  const [loading, setLoading] = useState(true);
  const [apiSequences, setApiSequences] = useState([]);

  useEffect(() => {
    if (apiCollectionId) {
      fetchApiSequences();
    }
  }, [apiCollectionId]);

  const fetchApiSequences = async () => {
    try {
      setLoading(true);
      const response = await api.getApiSequences(apiCollectionId);
      if (response && response.apiSequences) {
        setApiSequences(response.apiSequences);
        renderSequencesGraph(response.apiSequences);
      }
    } catch (error) {
      console.error('Error fetching API sequences:', error);
    } finally {
      setLoading(false);
    }
  };

  const renderSequencesGraph = (sequences) => {
    if (!sequences || sequences.length === 0) {
      setNodes(initialNodes);
      setEdges(initialEdges);
      return;
    }

    const newNodes = [];
    const newEdges = [];
    const nodeMap = new Map();
    let nodeId = 1;

    // Create nodes for each unique path in sequences
    sequences.forEach((sequence, index) => {
      if (sequence.paths && sequence.paths.length > 0) {
        sequence.paths.forEach((path, pathIndex) => {
          const nodeKey = path;
          if (!nodeMap.has(nodeKey)) {
            const node = {
              id: nodeId.toString(),
              data: { 
                label: path,
                transitionCount: sequence.transitionCount,
                probability: sequence.probability
              },
              position: { 
                x: 200 + (pathIndex * 300), 
                y: 100 + (index * 150) 
              },
                             style: {
                 background: '#ffffff',
                 border: '1px solid #ccc',
                 padding: '10px',
                 borderRadius: '4px'
               },
            };
            newNodes.push(node);
            nodeMap.set(nodeKey, nodeId.toString());
            nodeId++;
          }
        });

        // Create edges between consecutive paths
        for (let i = 0; i < sequence.paths.length - 1; i++) {
          const sourceNode = nodeMap.get(sequence.paths[i]);
          const targetNode = nodeMap.get(sequence.paths[i + 1]);
          
          if (sourceNode && targetNode) {
                         const edge = {
               id: `e${sourceNode}-${targetNode}`,
               source: sourceNode,
               target: targetNode,
               label: `${sequence.transitionCount} (${(sequence.probability * 100).toFixed(1)}%)`
             };
            newEdges.push(edge);
          }
        }
      }
    });

    setNodes(newNodes);
    setEdges(newEdges);
  };

  const addNewNode = () => {
    const newNode = {
      id: `${nodes.length + 1}`,
      data: { label: `Step ${nodes.length + 1}` },
      position: { x: 250, y: 100 + (nodes.length * 100) },
    };
    setNodes((nds) => nds.concat(newNode));
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
      <h2>API Sequences</h2>
      <p>Visualize API call sequences and transition probabilities</p>
      <button onClick={fetchApiSequences}>Refresh</button>
      
      {apiSequences.length > 0 && (
        <div style={{ marginBottom: '20px', padding: '10px', background: '#f6f6f7' }}>
          <p>Total Sequences: {apiSequences.length}</p>
          <p>Total Transitions: {apiSequences.reduce((sum, seq) => sum + seq.transitionCount, 0)}</p>
          <p>Avg Probability: {(apiSequences.reduce((sum, seq) => sum + seq.probability, 0) / apiSequences.length * 100).toFixed(1)}%</p>
        </div>
      )}
      
      {apiSequences.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>No API sequences found for this collection</p>
        </div>
      ) : (
        <div style={{ height: "600px" }}>
          <ReactFlow 
            nodes={nodes} 
            edges={edges}
            fitView
          >
            <Background color="#aaa" gap={6} />
          </ReactFlow>
        </div>
      )}
    </div>
  );
}

export default SequencesFlow;
