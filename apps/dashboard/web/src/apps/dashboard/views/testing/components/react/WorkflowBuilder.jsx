import React from 'react';

import Workflow from './Workflow.jsx';

import useStore from './store'

import { createTheme } from '@mui/material/styles';

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

const WorkflowBuilder = ({endpointsList, initialNodes, initialEdges}) => {  
  const setInitialState = useStore((state) => state.setInitialState);
  const setEndpointsList = useStore((state) => state.setEndpointsList);

  setInitialState(initialNodes, initialEdges);
  endpointsList.forEach(x => {
    x.method = x.method.toUpperCase()
  })
  setEndpointsList(endpointsList);
  
  return (
    <Workflow theme={theme}/>
  )
}
export default WorkflowBuilder