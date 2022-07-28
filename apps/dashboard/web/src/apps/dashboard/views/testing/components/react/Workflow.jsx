import React, { useState } from 'react'
import ReactFlow, {
  Background,
  getRectOfNodes
} from 'react-flow-renderer';

import useStore from './store'
import StartNode from './StartNode.jsx';
import BlankNode from './BlankNode.jsx';

const onInit = (reactFlowInstance) => console.log('flow loaded:', reactFlowInstance);

const nodeTypes = { startNode: StartNode, blankNode: BlankNode };

const Workflow = () => {
  const nodes = useStore((state) => state.nodes)
  const edges = useStore((state) => state.edges)
  const currentSource = useStore((state) => state.currentSource)

  const onNodesChange = useStore((state) => state.onNodesChange)
  const onEdgesChange = useStore((state) => state.onEdgesChange)
  const onConnect = useStore((state) => state.onConnect)
  
  const setCurrentSource = useStore((state) => state.setCurrentSource)
  const addNode = useStore((state) => state.addNode)

  const enteredNode = useStore(state => state.enteredNode)
  const setEnteredNode = useStore(state => state.setEnteredNode)

  const onConnectStart = (event, {nodeId, handleType}) => {
    setCurrentSource({x: event.screenX, y: event.screenY, nodeId, handleType})
  } 

  const getId = () => {
    return ''+parseInt(Math.random() * 10000000)
  }

  const onConnectStop = (event) => {
    const refNode = nodes.find(x => x.id === currentSource.nodeId)

    if (enteredNode && enteredNode.id !== refNode.id) {
      const newEdge = {source: refNode.id, target: enteredNode.id, id: getId(), selected: true, markerEnd: {type: 'arrow'}}
      onConnect(newEdge)
    } else {
      const deltaX = event.screenX - currentSource.x
      const deltaY = event.screenY - currentSource.y
      const lastNode = {...nodes[1]}

      const refNodeHeight = getRectOfNodes([refNode]).height

      lastNode.position = {
        x: refNode.position.x + deltaX/zoom,
        y: refNode.position.y + deltaY/zoom + refNodeHeight/2
      }
      lastNode.hidden = false
      lastNode.id = getId()
      const newEdge = {source: refNode.id, target: lastNode.id, id: getId(), selected: true, markerEnd: {type: 'arrow'}}
      addNode(lastNode, newEdge)
    }
  } 

  const setZoom = useStore(state => state.setZoom)
  const zoom = useStore(state => state.zoom)
  const onMoveEnd = (event, viewport) => {
    setZoom(viewport.zoom)
  }

  const onNodeMouseEnter = (event, node) => {
    setEnteredNode(node)
  }
 
  const onNodeMouseLeave = (event, node) => {
    setEnteredNode(null)
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      defaultPosition={[0, -90]}
      defaultZoom={1}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      onInit={onInit}
      nodeTypes={nodeTypes}
      onConnectStart={onConnectStart}
      onConnectStop={onConnectStop}
      onMoveEnd={onMoveEnd}
      onNodeMouseEnter={onNodeMouseEnter}
      onNodeMouseLeave={onNodeMouseLeave}
      attributionPosition="top-right"
    >
      <Background color="#aaa" gap={16} />
    </ReactFlow>
  );
};

export default Workflow