import create from 'zustand';
import {
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
} from 'react-flow-renderer';
import api from '../../../observe/inventory/api';

const useStore = create((set, get) => ({
  endpointsList: [],
  fetchSampleDataFunc: null,
  nodes: [],
  edges: [],
  currentSource: null,
  zoom: 1,
  enteredNode: null,
  nodeEndpointMap: {},
  counter: 1,
  createWorkflowTest: null,
  editWorkflowTest: null,
  editWorkflowNodeDetails: null,
  originalState: null,
  runWorkflowTest: null,
  fetchWorkflowResult: null,
  testingRun: null,
  setUtilityFuncs: (newCreateWorkflowTest, newEditWorkflowTest, newEditWorkflowNodeDetails, runWorkflowTest, fetchWorkflowResult) => {
    set({
      createWorkflowTest: newCreateWorkflowTest, 
      editWorkflowTest: newEditWorkflowTest, 
      editWorkflowNodeDetails: newEditWorkflowNodeDetails,
      runWorkflowTest: runWorkflowTest,
      fetchWorkflowResult: fetchWorkflowResult
    })
  }, 
  setOriginalState: (originalStateFromDb) => {
    let nodes = originalStateFromDb.nodes.map(x => JSON.parse(x))
    let counter = 0;
    nodes.forEach((x) => {
      let id = x["id"];
      if (id.startsWith("x")) {
        let numAsString = id.slice(1,x.length)
        let num = parseInt(numAsString)
        counter = num > counter ? num : counter
      }
    })
    set({
      originalState: originalStateFromDb,
      counter: counter+1,
      nodes: nodes,
      edges: originalStateFromDb.edges.map(x => JSON.parse(x)),
      nodeEndpointMap: {...originalStateFromDb.mapNodeIdToWorkflowNodeDetails}
    })
  },
  incrementCounter: () => {
    set({
      counter: (get().counter + 1)
    })
  },
  addNodeEndpoint: (nodeId, endpointData) => {
    let ret = get().nodeEndpointMap
    ret[nodeId] = endpointData
    set({
      nodeEndpointMap: ret
    })
  },

  setApiType: (nodeId, apiType) => {
    let ret = get().nodeEndpointMap
    let endpointData = ret[nodeId]
    if (!endpointData) return
    endpointData["type"] = apiType
    set({
      nodeEndpointMap: {...ret}
    })
  },

  setRedirect: (nodeId, overrideRedirect) => {
    let ret = get().nodeEndpointMap
    let endpointData = ret[nodeId]
    if (!endpointData) return
    endpointData["overrideRedirect"] = overrideRedirect
    set({
      nodeEndpointMap: {...ret}
    })
  },

  setSleep: (nodeId, waitInSeconds) => {
    let ret = get().nodeEndpointMap
    let endpointData = ret[nodeId]
    if (!endpointData) return
    endpointData["waitInSeconds"] = waitInSeconds
    set({
      nodeEndpointMap: {...ret}
    })
  },

  setValidatorCode: (nodeId, testValidatorCode) => {
    let ret = get().nodeEndpointMap
    let endpointData = ret[nodeId]
    if (!endpointData) return
    endpointData["testValidatorCode"] = testValidatorCode
    set({
      nodeEndpointMap: {...ret}
    })
  },

  setEndpointsList: (newList, newFetchSampleDataFunc) => {
    set({
      endpointsList: newList,
      fetchSampleDataFunc: newFetchSampleDataFunc
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

  fetchWorkflowTestingRun: (workflowId) => {
    api.fetchWorkflowTestingRun(workflowId).then((resp) => {
      if (resp && resp.testingRuns) {
        set({
          testingRun: resp.testingRuns[0]
        })
      }
    })
  },

  scheduleWorkflowTest: (id, recurringDaily, startTimestamp) => {
    api.scheduleWorkflowTest(id, recurringDaily, startTimestamp).then((resp) => {
      if (resp && resp.testingRuns) {
        set({
          testingRun: resp.testingRuns[0]
        })
      }
    })
  },

  deleteScheduledWorkflowTests: (id) => {
    api.deleteScheduledWorkflowTests(id).then((resp) => {
      set({
        testingRun: null
      })
    })
  },

  downloadWorkflowAsJson: (id) => {
    return api.downloadWorkflowAsJson(id)
  }

}));


export default useStore;
