import React, { useState, useEffect, useRef} from 'react'
import ReactFlow, {
  Background,
  getRectOfNodes
} from 'react-flow-renderer';
import { faEye, faEyeSlash, faSave } from '@fortawesome/free-regular-svg-icons';
import { faPlayCircle, faCalendarPlus, faArrowAltCircleDown} from '@fortawesome/free-regular-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import IconButton from "@mui/material/IconButton"

import useStore from './store'
import StartNode from './StartNode.jsx';
import BlankNode from './BlankNode.jsx';
import EndNode from './EndNode.jsx';

import Drawer from '@mui/material/Drawer';
import { AppBar, Tooltip } from '@material-ui/core';
import { makeStyles } from "@material-ui/core/styles";
import WorkflowResultsDrawer from './WorkflowResultsDrawer.jsx';
import ScheduleBox from './ScheduleBox.jsx';
import Menu from '@mui/material/Menu';
import { saveAs } from 'file-saver'



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

const Workflow = ({apiCollectionId, defaultOpenResult}) => {
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

  const testingRun = useStore(state => state.testingRun)
  const fetchWorkflowTestingRun = useStore(state => state.fetchWorkflowTestingRun)
  const deleteScheduledWorkflowTests = useStore(state => state.deleteScheduledWorkflowTests)
  const scheduleWorkflowTest = useStore(state => state.scheduleWorkflowTest)
  const downloadWorkflowAsJsonFn = useStore(state => state.downloadWorkflowAsJson)

  const onConnectStart = (event, {nodeId, handleType}) => {
    setCurrentSource({x: event.screenX, y: event.screenY, nodeId, handleType})
  }

  const counter = useStore(state => state.counter)
  const incrementCounter = useStore(state => state.incrementCounter)


  const [open, setOpen] = useState(defaultOpenResult);
  const [height, setHeight] = useState(0);
  const [workflowTestResult, setWorkflowTestResult] = useState(null)
  const [workflowTestingRun, setWorkflowTestingRun] = useState(null);
  const [testRunning, setTestRunning] = useState(false);
  const containerRef = useRef();
  const [scheduleBoxOpenFlag, setScheduleBoxOpenFlag] = useState(false)
  const [anchorEl, setAnchorEl] = React.useState(null);

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
      editWorkflowTest(originalState.id, nodes.map(JSON.stringify), edges.map(JSON.stringify), nodeEndpointMap).then((resp) => {
          window._AKTO.$emit('SHOW_SNACKBAR', {
              show: true,
              text: "Workflow saved",
              color: 'green'
          })
      })
    } else {
      createWorkflowTest(nodes.map(JSON.stringify), edges.map(JSON.stringify), nodeEndpointMap, "DRAFT", apiCollectionId).then(resp => {
        setOriginalState(resp.workflowTests[0])
        window._AKTO.$emit('SHOW_SNACKBAR', {
            show: true,
            text: "Workflow saved",
            color: 'green'
        })
      })
    }
  }

  const saveWorkflowFn = (recurring, startTimestamp) => {
      if (!originalState.id) {
          saveWorkflowEmitSnackbar()
          return;
      }
      scheduleWorkflowTest(originalState.id, recurring, startTimestamp)
  }

  const deleteWorkflowScheduleFn = () => {
      if (!originalState.id) {
          saveWorkflowEmitSnackbar()
          return;
      }
      deleteScheduledWorkflowTests(originalState.id)
  }

  const fetchResult = () => {
      if (!originalState.id) {
          saveWorkflowEmitSnackbar()
          return;
      }
      return fetchWorkflowResult(originalState.id).then((resp) => {
      if (!resp) return false

      setWorkflowTestingRun(resp["workflowTestingRun"])

      let w = resp["workflowTestResult"]

      if (!w) return false

      let testResultMap = w["nodeResultMap"]
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
          saveWorkflowEmitSnackbar()
          return;
      }

      setTestRunning(true)

      runWorkflowTest(originalState.id).then((resp) => {
          window._AKTO.$emit('SHOW_SNACKBAR', {
              show: true,
              text: "Running test",
              color: 'green'
          })
      })

      setWorkflowTestingRun(null)
      setWorkflowTestResult(null)

      let interval = setInterval(() => {
        fetchResult().then((result) => {
          if (result) {
              window._AKTO.$emit('SHOW_SNACKBAR', {
                  show: true,
                  text: "Test completed",
                  color: 'green'
              })
            setTestRunning(false)
            clearInterval(interval)
          }
        })
      }, 5000)

  }

  const openScheduleBox= (event) => {
    let v = scheduleBoxOpenFlag ? null : event.currentTarget
    setAnchorEl(v);
    setScheduleBoxOpenFlag(!scheduleBoxOpenFlag)
  }

  const showResult = () => setOpen(!open);

  const downloadWorkflowAsJson = () => {
      let workflowId = originalState.id
      if (!workflowId) {
          saveWorkflowEmitSnackbar()
          return;
      }
      downloadWorkflowAsJsonFn(workflowId).then((resp) => {
          let workflowTestJson = resp["workflowTestJson"]
          var blob = new Blob([workflowTestJson], {
              type: "application/json",
          });
          const fileName = "workflow_"+workflowId+".json";
          saveAs(blob, fileName);
          window._AKTO.$emit('SHOW_SNACKBAR', {
              show: true,
              text: fileName + " downloaded !",
              color: 'green'
          })
      })
  }

  const saveWorkflowEmitSnackbar = () => {
      window._AKTO.$emit('SHOW_SNACKBAR', {
          show: true,
          text: "Please save the workflow first",
          color: 'red'
      })
  }

    React.useEffect(() => {
    if (originalState.id) {
      fetchResult()
      fetchWorkflowTestingRun(originalState.id)
    }
  }, []);

  const iconData = [
    {
      title: "Schedule Test",
      onClick: openScheduleBox,
      icon: faCalendarPlus
    },
    {
      title: "Save workflow",
      onClick: onSave,
      icon: faSave
    },
    {
      title: "Run test",
      onClick: runTest,
      icon: faPlayCircle
    },
    {
      title: "Show test results",
      onClick: showResult,
      icon: open ? faEyeSlash : faEye
    },
    {
      title: "Download workflow",
      onClick: downloadWorkflowAsJson,
      icon: faArrowAltCircleDown
    }
  ];

  return (
    <div style={{height: "800px"}} ref={containerRef}>
      {
        iconData.map((icon) =>
          <Tooltip title={icon.title} key={icon.title}>
            <IconButton onClick={icon.onClick} style={{ float: "right" }}>
              <FontAwesomeIcon icon={icon.icon} className="workflow-button" size="sm" />
            </IconButton>
          </Tooltip>
        )
      }
      <Menu
        id="basic-menu"
        anchorEl={anchorEl}
        open={scheduleBoxOpenFlag}
        onClose={openScheduleBox}
        MenuListProps={{
          'aria-labelledby': 'basic-button',
        }}
      >
        <ScheduleBox 
          saveFn={saveWorkflowFn}
          testingRun={testingRun}
          deleteFn={deleteWorkflowScheduleFn}
        />
      </Menu>

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
            testRunning={testRunning}
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