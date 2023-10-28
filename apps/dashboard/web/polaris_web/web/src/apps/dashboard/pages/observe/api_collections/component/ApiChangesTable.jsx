import React, { useState } from 'react'
import transform from '../../transform';
import apiChangesData from '../data/apiChanges';

function ApiChangesTable(props) {

  const { handleRowClick, loading, startTimeStamp, endTimeStamp, newEndpoints, parametersCount } = props ;
  const [selectedTab, setSelectedTab] = useState("endpoints") ;
  const [selected, setSelected] = useState(0) ;

  const tableTabs = [
    {
      content: 'New endpoints',
      index: 0,
      badge: transform.formatNumberWithCommas(newEndpoints.length),
      onAction: ()=> {setSelectedTab('endpoints')},
      id: 'endpoints',
    },
    {
      content: 'New parameters',
      index: 0,
      badge: transform.formatNumberWithCommas(parametersCount),
      onAction: ()=> {setSelectedTab('param')},
      id: 'param',
    },
  ]

  const tableDataObj = apiChangesData.getData(selectedTab);

  const handleRow = (data) => {
      handleRowClick(data,tableDataObj.headers)
  }

  return (
    <div>ApiChangesTable</div>
  )
}

export default ApiChangesTable