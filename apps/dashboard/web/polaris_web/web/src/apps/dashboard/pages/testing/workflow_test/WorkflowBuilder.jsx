import React, {useState, useEffect} from 'react';
import { createTheme } from '@mui/material/styles';

import Workflow from './Workflow.jsx';

import useStore from './store.js'
import './start-node.css'

const theme = createTheme({
  palette: {
    secondary: {
      main: '#47466A'
    },
    primary: {
      main: '#6200EA'
    },
    warning: {
      main: "rgba(243, 107, 107)"
    }
  }
});

const WorkflowBuilder = ({endpointsList, originalStateFromDb, fetchSampleDataFunc, createWorkflowTest, editWorkflowTest, editWorkflowNodeDetails, apiCollectionId, runWorkflowTest, fetchWorkflowResult, defaultOpenResult}) => {
  const setOriginalState = useStore((state) => state.setOriginalState);
  const setEndpointsList = useStore((state) => state.setEndpointsList);
  const setUtilityFuncs = useStore((state) => state.setUtilityFuncs);
  const [renderNow, setRenderNow ]= useState(false)

  useEffect(() => {
    setOriginalState(originalStateFromDb);
    setUtilityFuncs(createWorkflowTest, editWorkflowTest, editWorkflowNodeDetails, runWorkflowTest, fetchWorkflowResult);
    setRenderNow(true) // this was done because useEffect function inside workflow.jsx was called before this one's useEffect. This way it only loads after this.
  }, [originalStateFromDb]);


  useEffect(() => {
    endpointsList.forEach(x => {
      x.method = x.method.toUpperCase()
    })
    setEndpointsList(endpointsList, fetchSampleDataFunc);
  }, [endpointsList]);


  if (renderNow) {
    return (
      <Workflow theme={theme} apiCollectionId={apiCollectionId} defaultOpenResult={defaultOpenResult}/>
    )
  } else {
    return (
      <div>Loading....</div>
    )
  }

}
export default WorkflowBuilder