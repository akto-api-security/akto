import React, { useEffect, useState } from 'react'
import GithubSimpleTable from '../../tables/GithubSimpleTable'
import func from '@/util/func'
import { IndexFiltersMode } from '@shopify/polaris'
import useTable from '../../tables/TableContext'
import treeViewFunc from './transform'
import PersistStore from '../../../../main/PersistStore'
import { CellType } from '../../tables/rows/GithubRow'
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper'

const groupObj =  {
    title: "Group name",
    text: "Group name",
    value: "displayNameComp",
    filterKey:"displayName",
    textValue: 'displayName',
    showFilter:true,
    type: CellType.COLLAPSIBLE,
    boxWidth: '210px'
}

function TreeViewTable({collectionsArr, sortOptions, resourceName, tableHeaders, promotedBulkActions}) {
    const apiCollectionMap = PersistStore(state => state.collectionsMap)
    const headers = [...[groupObj],...tableHeaders]
    const [selected, setSelected] = useState(0)
    const [selectedTab, setSelectedTab] = useState('hostname')

    const definedTableTabs = ['Hostname','Custom']

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
    }

    const [data, setData] = useState({'custom': [] ,'hostname': []})

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const prettifyAndRenderData = () => {
        let normalDataHost = treeViewFunc.buildTree(collectionsArr.filter((c) => c.hostName !== null && c.hostName !== undefined), "displayName", ".", true, true, ":", headers, true)
        normalDataHost = func.sortFunc(normalDataHost, sortOptions[0].sortKey, -1)
        const prettifyDataHost = treeViewFunc.prettifyTreeViewData(normalDataHost, headers)

        let normalDataCustom = treeViewFunc.buildTree(collectionsArr.filter((c) => c.hostName === null || c.hostName === undefined), "displayName", ".", true, true, ":", headers, true)
        normalDataCustom = func.sortFunc(normalDataCustom, sortOptions[0].sortKey, -1)
        const prettifyDataCustom = treeViewFunc.prettifyTreeViewData(normalDataCustom, headers)

        setData({hostname: prettifyDataHost, custom: prettifyDataCustom});
    }
    
    const filters = [{
        key: 'apiCollectionId',
        label: mapLabel("API collection name", getDashboardCategory()),
        title: mapLabel("API collection name", getDashboardCategory()),
        choices: collectionsArr.map((x) => {
            return {
                label: x.displayName,
                value: x.id
            }
        })
    }]

    const filterDataOnCollections = (collectionIds, sortKey, sortOrder) => {
        const filteredData = collectionsArr.filter((x) => collectionIds.includes(x.id))
        let newData = treeViewFunc.buildTree(filteredData, "displayName", ".", true, true, ":", headers, true);
        newData = func.sortFunc(newData, sortKey, sortOrder, true)
        newData = treeViewFunc.prettifyTreeViewData(newData, headers)
        return newData
    }

    const modifyData = (filters, sortKey, sortOrder) => {
        const collectionIds = filters.apiCollectionId;
        if(collectionIds && collectionIds.length > 0){
            const filteredData = filterDataOnCollections(collectionIds,sortKey, sortOrder)
            return filteredData
        }else{
            return func.sortFunc(data[selectedTab],sortKey, sortOrder, true)
        }
    }

    useEffect(() => {
        prettifyAndRenderData();
    },[collectionsArr.length])

    return (
        <GithubSimpleTable
            data={data[selectedTab]} 
            sortOptions={sortOptions} 
            resourceName={resourceName} 
            filters={filters}
            disambiguateLabel={disambiguateLabel} 
            headers={headers}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={(x) => setSelected(x)}
            selected={selected}
            csvFileName={"Inventory"}
            treeView={true}
            modifyData={(filters, sortKey, sortOrder) => modifyData(filters, sortKey, sortOrder)}
            customFilters={true}
        />
    )
}

export default TreeViewTable