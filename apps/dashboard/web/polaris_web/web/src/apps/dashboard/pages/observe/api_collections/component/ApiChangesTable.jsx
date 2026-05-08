import React, { useState } from 'react'
import transform from '../../transform';
import apiChangesData from '../data/apiChanges';
import PersistStore from '../../../../../main/PersistStore';
import func from '@/util/func';
import { IndexFiltersMode } from '@shopify/polaris';
import useTable from '../../../../components/tables/TableContext';
import GithubServerTable from '../../../../components/tables/GithubServerTable';
import tableFunc from '../../../../components/tables/transform';
import api from '../../api';
import { getDashboardCategory, mapLabel } from '../../../../../main/labelHelper';

function ApiChangesTable(props) {

  const { handleRowClick, tableLoading, startTimeStamp, endTimeStamp, newParams} = props ;
  const [selectedTab, setSelectedTab] = useState("new_endpoints") ;
  const [selected, setSelected] = useState(0) ;
  const apiCollectionMap = PersistStore(state => state.collectionsMap)
  const [loading, setLoading] = useState(false);
  const [newEndpointsCount, setNewEndpointsCount] = useState(0)

  const definedTableTabs = [mapLabel("New endpoints", getDashboardCategory()), 'New params']

  const initialCount = [newEndpointsCount , newParams.length]

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, newParams, initialCount)
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

  const tableDataObj = apiChangesData.getData(selectedTab);

  const handleRow = async (data) => {
      let headers = []
      let transformedData = data
      
      if(selectedTab.includes('param')){
        headers = transform.getParamHeaders() ;
        
        // Fetch the actual API information for this parameter's endpoint
        const apiInfo = await api.fetchEndpoint({
          url: data.endpoint,
          method: data.method,
          apiCollectionId: data.apiCollectionId
        });
        
        // Use the real API data
        transformedData = {
          apiCollectionId: data.apiCollectionId,
          endpoint: data.endpoint,
          method: data.method,
          url: data.url,
          apiCollectionName: data.apiCollectionName,
          added: data.added,
          last_seen: apiInfo.data?.lastSeen ? func.prettifyEpoch(apiInfo.data.lastSeen) : data.added,
          parameterisedEndpoint: data.method + " " + data.endpoint,
          access_type: apiInfo.data?.apiAccessTypes?.includes("PUBLIC") ? "Public" : 
                     apiInfo.data?.apiAccessTypes?.includes("PARTNER") ? "Partner" :
                     apiInfo.data?.apiAccessTypes?.includes("THIRD_PARTY") ? "Third-party" : "Private",
          auth_type: apiInfo.data?.actualAuthType?.join(", ") || "no auth type found",
          sensitiveTags: apiInfo.data?.sensitive || [],
          tags: apiInfo.data?.tags || [],
          changes: data.changes || "No new changes",
          description: apiInfo.data?.description || "",
          severityObj: apiInfo.data?.severityObj || {},
          riskScore: apiInfo.data?.riskScore || 0,
          responseCodes: apiInfo.data?.responseCodes || [],
          violations: apiInfo.data?.violations || {},
          sources: apiInfo.data?.sources || {},
          lastTested: apiInfo.data?.lastTested || 0
        }
      }else{
        headers = transform.getDetailsHeaders() ;
      }
      handleRowClick(transformedData, headers)
  }

  function disambiguateLabel(key, value) {
    switch (key) {
        case "apiCollectionId":
            return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 3)
        default:
            return func.convertToDisambiguateLabelObj(value, null, 2)
    }
  }

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
  }

  const fetchTableData = async(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) =>{ 
    if(!selectedTab.includes('param')){
      setLoading(true);
        let ret = [];
        let total = 0;
        await api.loadRecentEndpoints(startTimeStamp, endTimeStamp, skip, limit, filters, filterOperators, queryValue).then(async(res)=> {
          const apiInfos = res.endpoints
          total = res.totalCount
          try {
            const allSensitiveFields = await api.fetchSensitiveParamsForEndpoints(apiInfos.map((x) => {return x?.id?.url}))
            const sensitiveParams = allSensitiveFields?.data?.endpoints
            const mappedData = transform.fillSensitiveParams(sensitiveParams, apiInfos.map((x)=> {return x?.id}));
            const normalData = func.mergeApiInfoAndApiCollection(mappedData, apiInfos, apiCollectionMap,{});
            ret = transform.prettifyEndpointsData(normalData);
          } catch (error) {
            // If sensitive params fetch fails, still process the data without sensitive params
            const mappedData = transform.fillSensitiveParams(undefined, apiInfos.map((x)=> {return x?.id}));
            const normalData = func.mergeApiInfoAndApiCollection(mappedData, apiInfos, apiCollectionMap,{});
            ret = transform.prettifyEndpointsData(normalData);
          }
        setLoading(false)
      })
      setNewEndpointsCount((prev)=> {
        if(prev === total){
          return prev;
        }
        return total
      })
      return { value: ret, total: total };
    }else{
      const dataObj = {
        "headers": tableDataObj.headers,
        "data": newParams,
        "sortOptions": tableDataObj.sortOptions,
      }
      return tableFunc.fetchDataSync(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, () => {}, dataObj)
    }
  }

  const key = selectedTab + startTimeStamp + endTimeStamp + newParams.length
  const filterOptions = func.getCollectionFilters(tableDataObj?.filters || [])

  return (
    <GithubServerTable 
      key={key}
      pageLimit={50}
      fetchData={fetchTableData}
      headers={tableDataObj.headers}
      resourceName={tableDataObj.resourceName}
      sortOptions={tableDataObj.sortOptions}
      disambiguateLabel={disambiguateLabel}
      loading={loading || tableLoading}
      onRowClick={(data) => handleRow(data)}
      filters={!selectedTab.includes('param') ? filterOptions : []}
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