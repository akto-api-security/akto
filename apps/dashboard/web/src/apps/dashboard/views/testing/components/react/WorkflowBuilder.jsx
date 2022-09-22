import React, {useEffect} from 'react';
import { createTheme } from '@mui/material/styles';

import Workflow from './Workflow.jsx';

import useStore from './store'
import './start-node.css'


// use default theme
// const theme = createTheme();

// Or Create your Own theme:
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

const WorkflowBuilder = ({endpointsList, originalStateFromDb, fetchSampleDataFunc, createWorkflowTest, editWorkflowTest, editWorkflowNodeDetails, apiCollectionId, runWorkflowTest, fetchWorkflowResult}) => {
  const setOriginalState = useStore((state) => state.setOriginalState);
  const setEndpointsList = useStore((state) => state.setEndpointsList);
  const setUtilityFuncs = useStore((state) => state.setUtilityFuncs);
  const originalState = useStore((state) => state.originalState)

  useEffect(() => {
    setOriginalState(originalStateFromDb);
    setUtilityFuncs(createWorkflowTest, editWorkflowTest, editWorkflowNodeDetails, runWorkflowTest, fetchWorkflowResult);
  }, [originalStateFromDb]);


  useEffect(() => {
    endpointsList.forEach(x => {
      x.method = x.method.toUpperCase()
    })
    setEndpointsList(endpointsList, fetchSampleDataFunc);
  }, [endpointsList]);


  if (originalState) {
    return (
      <Workflow theme={theme} apiCollectionId={apiCollectionId}/>
    )
  } else {
    return (
      <div>Loading....</div>
    )
  }

}
export default WorkflowBuilder