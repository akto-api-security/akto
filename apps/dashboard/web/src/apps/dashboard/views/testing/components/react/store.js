import create from 'zustand';
import {
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
} from 'react-flow-renderer';

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


export default useStore;
