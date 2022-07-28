import create from 'zustand';
import {
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
} from 'react-flow-renderer';

const useStore = create((set, get) => ({
  endpointsList: [],
  nodes: [],
  edges: [],
  currentSource: null,
  zoom: 1,
  enteredNode: null,
  nodeEndpointMap: {},
  edgeVariablesMap: {},
  addNodeEndpoint: (nodeId, endpointData) => {
    let ret = get().nodeEndpointMap
    ret[nodeId] = endpointData
    set({
      nodeEndpointMap: ret
    })
  },
  addEdgeVariable: (edgeId, variablesData) => {
    let ret = {...get().edgeVariablesMap}
    ret[edgeId] = variablesData
    set({
      edgeVariablesMap: ret
    })
  },
  setEndpointsList: (newList) => {
    set({
      endpointsList: newList
    })
  },
  setZoom: (newZoom) => {
    set({
      zoom: newZoom
    })
  },
  setEnteredNode: (newNode) => {
    set({
      enteredNode: newNode
    })
  },
  setInitialState: (initialNodes, initialEdges) => {
    set({
      nodes: initialNodes,
      edges: initialEdges
    })
  },
  addNode: (newNode, newEdge) => {
    set({
      nodes: [
        ...get().nodes,
        newNode
      ]
    })
    set({
      edges: addEdge(
        newEdge,
        get().edges
      )
    })

  },
  setCurrentSource: (newSourceNode) => {
    set({
      currentSource: newSourceNode
    })
  },
  onNodesChange: (changes) => {
    set({
      nodes: applyNodeChanges(changes, get().nodes)
    });
  },
  onEdgesChange: (changes) => {
    set({
      edges: applyEdgeChanges(changes, get().edges)
    });
  },
  onConnect: (connection) => {
    set({
      edges: addEdge(connection, get().edges)
    });
  },
}));


export default useStore;
