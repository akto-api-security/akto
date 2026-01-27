import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import FlowGraphLayout from './FlowGraphLayout';
import { createCustomEdge, createCustomNode } from './FlowGraphComponents';

const initialNodes = [];
const initialEdges = [];

const SequencesEdge = createCustomEdge('arrow', 'grey');
const SequencesNode = createCustomNode('#ccc', '1px', '#555', '0 1px 3px rgba(0,0,0,0.1)');

const nodeTypes = { sequencesNode: SequencesNode };
const edgeTypes = { sequencesEdge: SequencesEdge };

function SequencesFlow({ apiCollectionId }) {
  const [nodes, setNodes] = useState(initialNodes);
  const [edges, setEdges] = useState(initialEdges);
  const [loading, setLoading] = useState(true);
  const [apiSequences, setApiSequences] = useState([]);

  const renderSequencesGraph = useCallback((sequences) => {
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

    setNodes(newNodes);
    setEdges(newEdges);
  }, []);

  const fetchApiSequences = useCallback(async () => {
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
  }, [apiCollectionId, renderSequencesGraph]);

  useEffect(() => {
    if (apiCollectionId) {
      fetchApiSequences();
    } else {
      setNodes([]);
      setEdges([]);
      setLoading(false);
    }
  }, [apiCollectionId, fetchApiSequences]);

  const totalSequences = apiSequences.length;
  const totalTransitions = apiSequences.reduce((sum, seq) => sum + seq.transitionCount, 0);
  const avgProbability = totalSequences > 0 ? (apiSequences.reduce((sum, seq) => sum + seq.probability, 0) / totalSequences * 100).toFixed(1) : 0;

  const bannerStats = [
    { label: 'Total Sequences', value: totalSequences },
    { label: 'Total Transitions', value: totalTransitions },
    { label: 'Avg Probability', value: `${avgProbability}%` }
  ];

  return (
    <FlowGraphLayout
      loading={loading}
      loadingMessage="Loading API sequences..."
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      defaultEdgeType="sequencesEdge"
      emptyMessage="No API sequences found for this collection"
      bannerTitle="API Sequences Overview"
      bannerStats={bannerStats}
      showData={apiSequences.length > 0}
    />
  );
}

export default SequencesFlow;
