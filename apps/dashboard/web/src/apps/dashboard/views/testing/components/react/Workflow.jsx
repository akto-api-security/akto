import React from 'react'

import useStore from './store'

import ReactFlow, {
  MiniMap,
  Controls,
  Background
} from 'react-flow-renderer';

const onInit = (reactFlowInstance) => console.log('flow loaded:', reactFlowInstance);

const Workflow = () => {
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

export default Workflow