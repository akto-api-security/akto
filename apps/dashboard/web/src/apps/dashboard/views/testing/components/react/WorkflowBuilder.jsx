import React from 'react';

import Workflow from './Workflow.jsx';

import useStore from './store'

const WorkflowBuilder = ({endpointsList, initialNodes, initialEdges}) => {  
  const setInitialState = useStore((state) => state.setInitialState);
  const setEndpointsList = useStore((state) => state.setEndpointsList);

  setInitialState(initialNodes, initialEdges);
  setEndpointsList(endpointsList);
  
  return (
    <Workflow />
  )
}
export default WorkflowBuilder