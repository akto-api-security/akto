import React, { useEffect, useState } from 'react'
import GithubSimpleTable from '../../tables/GithubSimpleTable'
import dummyDataObj from './dummyData'
import func from '@/util/func'
import { IndexFiltersMode } from '@shopify/polaris'
import useTable from '../../tables/TableContext'
import treeViewFunc from './transform'
import PersistStore from '../../../../main/PersistStore'

function TreeViewTable() {

    const {dummyData, sortOptions, resourceName, headers, promotedBulkActions} = dummyDataObj
    const apiCollectionMap = PersistStore(state => state.collectionsMap)

    const [selectedTab, setSelectedTab] = useState("custom")
    const [selected, setSelected] = useState(1)
    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
    }

    const definedTableTabs = ['Hostname', 'Custom']
    const { selectedItems } = useTable();
    const [data, setData] = useState({'custom': [] ,'hostname': []})

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)
    const normalData = treeViewFunc.buildTree(dummyData, "displayName", ".", false, true, ":", headers, true)

    const prettifyAndRenderData = () => {
        const prettifyData = treeViewFunc.prettifyTreeViewData(normalData, headers)
        setData({custom: prettifyData, hostname: []});
    }

    const filters = [{
        key: 'apiCollectionId',
        label: 'Api collection name',
        title: 'Api collection name',
        choices: dummyData.map((x) => {
            return {
                label: x.displayName,
                value: x.id
            }
        })
    }]

    const filterDataOnCollections = (collectionIds) => {
        const filteredData = dummyData.filter((x) => collectionIds.includes(x.id))
        let newData = treeViewFunc.buildTree(filteredData, "displayName", ".", false, true, ":", headers, false);
        newData = treeViewFunc.prettifyTreeViewData(newData, headers)
        return newData
    }

    const modifyData = (filters) => {
        const collectionIds = filters.apiCollectionId;
        if(collectionIds && collectionIds.length > 0){
            const filteredData = filterDataOnCollections(collectionIds)
            return filteredData
        }
        return data[selectedTab];
    }

    useEffect(() => {
        prettifyAndRenderData();
    },[])
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
            modifyData={(filters) => modifyData(filters)}
            customFilters={true}
        />
    )
}

export default TreeViewTable