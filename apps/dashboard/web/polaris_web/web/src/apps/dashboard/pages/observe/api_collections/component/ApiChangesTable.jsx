import React, { useEffect, useState } from 'react'
import transform from '../../transform';
import apiChangesData from '../data/apiChanges';
import PersistStore from '../../../../../main/PersistStore';
import func from '@/util/func';
import { IndexFiltersMode } from '@shopify/polaris';
import useTable from '../../../../components/tables/TableContext';
import GithubSimpleTable from '../../../../components/tables/GithubSimpleTable';

function ApiChangesTable(props) {

  const { handleRowClick, tableLoading, startTimeStamp, endTimeStamp, newEndpoints, newParams } = props ;
  const [selectedTab, setSelectedTab] = useState("new_endpoints") ;
  const [selected, setSelected] = useState(0) ;
  const apiCollectionMap = PersistStore(state => state.collectionsMap)
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState({"new_endpoints": [], "new_params": []})

  const definedTableTabs = ['New endpoints', 'New params']

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, data)
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

  const tableDataObj = apiChangesData.getData(selectedTab);

  const handleRow = (data) => {
      let headers = []
      if(selectedTab.includes('param')){
        headers = transform.getParamHeaders() ;
      }else{
        headers = transform.getDetailsHeaders() ;
      }
      handleRowClick(data,headers)
  }

  function disambiguateLabel(key, value) {
    if(selectedTab.includes('param')){
      switch (key) {
          case "apiCollectionId": 
              return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 3)
          default:
              return value;
      }
    }else{
      return func.convertToDisambiguateLabelObj(value, null, 2);
    }
  }

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
  }

  const fetchData = async() => {
    setLoading(true)
    setData({new_params: newParams, new_endpoints: newEndpoints})
    setLoading(false)
  }

  useEffect(()=>{
    fetchData()
  },[newEndpoints, newParams])

  const key = selectedTab + startTimeStamp + endTimeStamp

  return (
    <GithubSimpleTable 
      key={key}
      pageLimit={50}
      data={data[selectedTab]}
      headers={tableDataObj.headers}
      resourceName={tableDataObj.resourceName}
      sortOptions={tableDataObj.sortOptions}
      disambiguateLabel={disambiguateLabel}
      loading={loading || tableLoading}
      onRowClick={(data) => handleRow(data)}
      filters={[]}
      selected={selected}
      onSelect={handleSelectedTab}
      mode={IndexFiltersMode.Default}
      headings={tableDataObj.headings}
      useNewRow={true}
      condensedHeight={true}
      tableTabs={tableTabs}
    />
  )
}

export default ApiChangesTable