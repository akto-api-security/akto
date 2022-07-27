import React from 'react';

import Workflow from './Workflow.jsx';

import useStore from './store'

const WorkflowBuilder = ({endpointsList, initialNodes, initialEdges}) => {
  console.log({endpointsList, initialNodes, initialEdges})
  
  const setInitialState = useStore((state) => state.setInitialState)

  setInitialState(initialNodes, initialEdges)
  
  return (
    <Workflow />
  )
}
export default WorkflowBuilder