import React, { useState, useEffect, useCallback } from 'react';
import api from '../api';
import FlowGraphLayout from './FlowGraphLayout';
import { createCustomEdge, createCustomNode } from './FlowGraphComponents';

const initialNodes = [];
const initialEdges = [];

const SwaggerDependenciesEdge = createCustomEdge('swagger-arrow', '#5c6ac4', true); // Enable color variation
const SwaggerDependenciesNode = createCustomNode('#5c6ac4', '2px', '#5c6ac4', '0 2px 8px rgba(92,106,196,0.2)');

const nodeTypes = { swaggerNode: SwaggerDependenciesNode };
const edgeTypes = { swaggerEdge: SwaggerDependenciesEdge };

function SwaggerDependenciesFlow({ apiCollectionId }) {
  const [nodes, setNodes] = useState(initialNodes);
  const [edges, setEdges] = useState(initialEdges);
  const [loading, setLoading] = useState(true);
  const [dependencies, setDependencies] = useState([]);

  const renderDependenciesGraph = useCallback((dependenciesData) => {
    const newNodes = [];
    const newEdges = [];
    const nodeMap = new Map();
    let nodeId = 1;
    const createdEdges = new Set();

    // Layout configuration for better spacing with many nodes
    const HORIZONTAL_SPACING = 400;
    const VERTICAL_SPACING = 250;
    const NODES_PER_ROW = 6; // Limit nodes per row for better readability
    const MAX_DEPENDENCIES_TO_SHOW = 2; // Show only max 2 dependencies per API

    // Track incoming and outgoing counts for each node
    const nodeCounts = new Map(); // Map<nodeKey, {incoming: number, outgoing: number}>

    // First pass: collect all unique nodes and count dependencies
    const allNodeKeys = new Set();
    dependenciesData.forEach((dependency) => {
      const sourceKey = `${dependency.apiIdentifier.method} ${dependency.apiIdentifier.url}`;
      allNodeKeys.add(sourceKey);

      if (!nodeCounts.has(sourceKey)) {
        nodeCounts.set(sourceKey, { incoming: 0, outgoing: 0 });
      }

      if (dependency.dependencies && Array.isArray(dependency.dependencies)) {
        const totalDeps = dependency.dependencies.length;
        nodeCounts.get(sourceKey).outgoing = totalDeps;

        dependency.dependencies.forEach((dep) => {
          const targetKey = `${dep.id.method} ${dep.id.url}`;
          allNodeKeys.add(targetKey);

          if (!nodeCounts.has(targetKey)) {
            nodeCounts.set(targetKey, { incoming: 0, outgoing: 0 });
          }
          nodeCounts.get(targetKey).incoming += 1;
        });
      }
    });

    // Create nodes with grid layout
    const nodeKeysArray = Array.from(allNodeKeys);
    nodeKeysArray.forEach((nodeKey, index) => {
      const [method, ...urlParts] = nodeKey.split(' ');
      const url = urlParts.join(' ');
      const row = Math.floor(index / NODES_PER_ROW);
      const col = index % NODES_PER_ROW;
      const counts = nodeCounts.get(nodeKey) || { incoming: 0, outgoing: 0 };

      nodeMap.set(nodeKey, nodeId.toString());
      newNodes.push({
        id: nodeId.toString(),
        type: 'swaggerNode',
        data: {
          method,
          url,
          incomingCount: counts.incoming,
          outgoingCount: counts.outgoing,
          showCounts: true
        },
        position: {
          x: col * HORIZONTAL_SPACING,
          y: row * VERTICAL_SPACING
        }
      });
      nodeId++;
    });

    // Create edges - limit to MAX_DEPENDENCIES_TO_SHOW per source
    dependenciesData.forEach((dependency) => {
      const sourceKey = `${dependency.apiIdentifier.method} ${dependency.apiIdentifier.url}`;
      const sourceId = nodeMap.get(sourceKey);

      if (dependency.dependencies && Array.isArray(dependency.dependencies)) {
        dependency.dependencies
          .sort((a, b) => a.order - b.order)
          .slice(0, MAX_DEPENDENCIES_TO_SHOW) // Limit to first 2 dependencies
          .forEach((dep) => {
            const targetKey = `${dep.id.method} ${dep.id.url}`;
            const targetId = nodeMap.get(targetKey);

            if (sourceId && targetId) {
              const edgeId = `e${sourceId}-${targetId}`;
              if (!createdEdges.has(edgeId)) {
                newEdges.push({
                  id: edgeId,
                  source: sourceId,
                  target: targetId,
                  sourceHandle: 'b',
                  targetHandle: 't',
                  type: 'swaggerEdge',
                  animated: false,
                  data: {
                    label: dep.param || '',
                    order: dep.order,
                    param: dep.param
                  }
                });
                createdEdges.add(edgeId);
              }
            }
          });
      }
    });

    setNodes(newNodes);
    setEdges(newEdges);
  }, []);

  const fetchSwaggerDependencies = useCallback(async () => {
    try {
      setLoading(true);
      const response = await api.getSwaggerDependencies(apiCollectionId);

      // Extract data from response - handle both direct array and nested data property
      const data = Array.isArray(response) ? response : (response?.data || response?.apiDependenciesList || []);

      if (Array.isArray(data) && data.length > 0) {
        setDependencies(data);
        renderDependenciesGraph(data);
      } else {
        setDependencies([]);
        setNodes([]);
        setEdges([]);
      }
    } catch (error) {
      console.error('Error fetching Swagger dependencies:', error);
      setDependencies([]);
      setNodes([]);
      setEdges([]);
    } finally {
      setLoading(false);
    }
  }, [apiCollectionId, renderDependenciesGraph]);

  useEffect(() => {
    if (apiCollectionId) {
      fetchSwaggerDependencies();
    } else {
      setNodes([]);
      setEdges([]);
      setLoading(false);
    }
  }, [apiCollectionId, fetchSwaggerDependencies]);

  const totalDependencies = dependencies.length;
  const totalConnections = dependencies.reduce((sum, dep) => {
    return sum + (dep.dependencies ? dep.dependencies.length : 0);
  }, 0);
  const shownConnections = edges.length;

  const bannerStats = [
    { label: 'Total APIs', value: totalDependencies },
    { label: 'Dependencies (Shown/Total)', value: `${shownConnections}/${totalConnections}` },
    { label: 'Unique Endpoints', value: nodes.length }
  ];

  return (
    <FlowGraphLayout
      loading={loading}
      loadingMessage="Loading API dependencies..."
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      defaultEdgeType="swaggerEdge"
      emptyMessage="No API dependencies found for this collection"
      bannerTitle="API Dependencies Overview"
      bannerStats={bannerStats}
      showData={dependencies.length > 0}
      miniMapNodeColor="#5c6ac4"
    />
  );
}

export default SwaggerDependenciesFlow;
