import React, { useCallback } from 'react';
import ReactFlow, {
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
  MiniMap,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
} from 'react-flow-renderer';
import create from 'zustand';

const useStore = create((set, get) => ({
  nodes: [],
  edges: [],
  setInitialState: (initialNodes, initialEdges) => {
    set({
      nodes: initialNodes,
      edges: initialEdges
    })
  },
  onNodesChange: (changes) => {
    set({
      nodes: applyNodeChanges(changes, get().nodes),
    });
  },
  onEdgesChange: (changes) => {
    set({
      edges: applyEdgeChanges(changes, get().edges),
    });
  },
  onConnect: (connection) => {
    set({
      edges: addEdge(connection, get().edges),
    });
  },
}));


const onInit = (reactFlowInstance) => console.log('flow loaded:', reactFlowInstance);

const HelloReactChild = () => {
  const nodes = useStore((state) => state.nodes)
  const edges = useStore((state) => state.edges)

  const onNodesChange = useStore((state) => state.onNodesChange)
  const onEdgesChange = useStore((state) => state.onEdgesChange)
  const onConnect = useStore((state) => state.onConnect)

  console.log(nodes, edges)
 
  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      onInit={onInit}
      fitView
      attributionPosition="top-right"
    >
      <MiniMap
        nodeStrokeColor={(n) => {
          if (n.style?.background) return n.style.background;
          if (n.type === 'input') return '#0041d0';
          if (n.type === 'output') return '#ff0072';
          if (n.type === 'default') return '#1a192b';

          return '#eee';
        }}
        nodeColor={(n) => {
          if (n.style?.background) return n.style.background;

          return '#fff';
        }}
        nodeBorderRadius={2}
      />
      <Controls />
      <Background color="#aaa" gap={16} />
    </ReactFlow>
  );
};

const HelloReact = ({endpointsList, initialNodes, initialEdges}) => {
  console.log({endpointsList, initialNodes, initialEdges})
  
  const setInitialState = useStore((state) => state.setInitialState)

  setInitialState(initialNodes, initialEdges)
  
  return (
    <HelloReactChild />
  )
}
export default HelloReact