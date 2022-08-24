import React, { useState, useEffect, useRef} from 'react'
import ReactFlow, {
  Background,
  getRectOfNodes
} from 'react-flow-renderer';
import { faEye, faEyeSlash, faSave } from '@fortawesome/free-regular-svg-icons';
import { faPlayCircle } from '@fortawesome/free-regular-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import IconButton from "@mui/material/IconButton"

import useStore from './store'
import StartNode from './StartNode.jsx';
import BlankNode from './BlankNode.jsx';
import EndNode from './EndNode.jsx';

import Drawer from '@mui/material/Drawer';
import { AppBar } from '@material-ui/core';
import { makeStyles } from "@material-ui/core/styles";
import WorkflowResultsDrawer from './WorkflowResultsDrawer.jsx';


const onInit = (reactFlowInstance) => console.log('flow loaded:', reactFlowInstance);

const nodeTypes = { startNode: StartNode, blankNode: BlankNode, endNode: EndNode };

const useStyles = makeStyles({
  drawer: {
    position: "relative",
    marginLeft: "auto",
    "& .MuiBackdrop-root": {
      display: "none"
    },
    "& .MuiDrawer-paper": {
      position: "absolute",
      height: (props) => props.height,
    }
  }
});

const Workflow = ({apiCollectionId}) => {
  const nodes = useStore((state) => state.nodes)
  const originalState = useStore((state) => state.originalState)
  const edges = useStore((state) => state.edges)
  const nodeEndpointMap = useStore((state) => state.nodeEndpointMap)
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

  const counter = useStore(state => state.counter)
  const incrementCounter = useStore(state => state.incrementCounter)


  const [open, setOpen] = useState(false);
  const [height, setHeight] = useState(0);
  const [workflowTestResult, setWorkflowTestResult] = useState(null)
  const [workflowTestingRun, setWorkflowTestingRun] = useState(null);
  const containerRef = useRef();

  const classes = useStyles({ height: height });

  useEffect(() => {
    open ? setHeight(containerRef.current.clientHeight - 64) : setHeight(0)
  }, [open]);

  const getId = () => {
    incrementCounter()
    return 'x'+counter
  }



    function getCycle(graph) {
      let queue = Object.keys(graph).map( node => [node] );
      while (queue.length) {
        const batch = [];
        for (const path of queue) {
            const parents = graph[path[0]] || [];
            for (const node of parents) {
                if (node === path[path.length-1]) return [node, ...path];
                batch.push([node, ...path]);
            }
        }
        queue = batch;
    }
  }

  const cyclesPresent = (newEdge) => {
    let graph = {}
    for (const edge of [...edges, newEdge]) {
      let source = edge.source
      let target = edge.target
      if (!graph[target]) {
        graph[target] = []
      }

      graph[target].push(source)
    }
    return getCycle(graph)
  }

  const onConnectStop = (event) => {
    const refNode = nodes.find(x => x.id === currentSource.nodeId)

    if (enteredNode && enteredNode.id !== refNode.id) {
      const newEdge = {source: refNode.id, target: enteredNode.id, id: getId(), selected: true, markerEnd: {type: 'arrow'}}

      if (refNode.id !== '3' && !cyclesPresent(newEdge) ) {
         onConnect(newEdge)
      }
      
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

  const createWorkflowTest = useStore(state => state.createWorkflowTest)
  const editWorkflowTest = useStore(state => state.editWorkflowTest)
  const setOriginalState = useStore(state => state.setOriginalState)
  const runWorkflowTest = useStore(state => state.runWorkflowTest)
  const fetchWorkflowResult = useStore(state => state.fetchWorkflowResult)
  const onSave = () => {
    if (originalState.id) {
      editWorkflowTest(originalState.id, nodes.map(JSON.stringify), edges.map(JSON.stringify), nodeEndpointMap)
    } else {
      createWorkflowTest(nodes.map(JSON.stringify), edges.map(JSON.stringify), nodeEndpointMap, "DRAFT", apiCollectionId).then(resp => {
        setOriginalState(resp.workflowTests[0])
      })
    }
  }

  const fetchResult = () => {
    if (!originalState.id) return
    return fetchWorkflowResult(originalState.id).then((resp) => {
      if (!resp) return false

      setWorkflowTestingRun(resp["workflowTestingRun"])

      let w = resp["workflowTestResult"]

      if (!w) return false

      let testResultMap = w["testResultMap"]
      let keys = Object.keys(testResultMap)

      let finalResult = keys.map((x) => {
        return {"key": x, ...testResultMap[x]}
      })

      setWorkflowTestResult(finalResult)

      return true
    })
  }


  const runTest = () => {
      if (!originalState.id) {
          console.log("Please save test first")
          return;
      }

      runWorkflowTest(originalState.id)

      setWorkflowTestingRun(null)
      setWorkflowTestResult(null)

      let interval = setInterval(() => {
        fetchResult().then((result) => {
          if (result) clearInterval(interval)
        })
      }, 5000)

  }

  const showResult = () => setOpen(!open);
  React.useEffect(() => {fetchResult()}, []);

  return (
    <div style={{height: "800px"}} ref={containerRef}>
      <IconButton onClick={onSave} style={{float : "right"}}>
        <FontAwesomeIcon icon={faSave} className="workflow-button"  size="sm"/>
      </IconButton>

      <IconButton onClick={runTest} style={{float : "right"}}>
        <FontAwesomeIcon icon={faPlayCircle} className="workflow-button"  size="sm"/>
      </IconButton>

      <IconButton onClick={showResult} style={{float : "right"}}>
        <FontAwesomeIcon icon={open ? faEyeSlash : faEye} className="workflow-button"  size="sm"/>
      </IconButton>

      <AppBar position="static">
      </AppBar>

      <Drawer
          open={open}
          variant="persistent"
          className={classes.drawer}
          anchor="right"
          PaperProps={{
            sx: { width: "60%" ,border: 0.5, borderRadius: 2, borderColor: "grey" },
          }}
        >
          <WorkflowResultsDrawer 
            workflowTestingRun={workflowTestingRun}
            workflowTestResult={workflowTestResult}
          />
      </Drawer>

      <ReactFlow
        nodes={nodes}
        edges={edges}
        defaultPosition={[200, 0]}
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
    </div>
  );
};

export default Workflow