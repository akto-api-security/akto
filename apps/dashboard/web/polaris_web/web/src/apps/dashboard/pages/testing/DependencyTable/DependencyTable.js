import { Button, HorizontalStack, Icon, Link, Popover, Spinner, Text } from "@shopify/polaris";
import { useState } from "react";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import { CellType } from "../../../components/tables/rows/GithubRow";
import api from "../api";
import { TickMinor, CancelMinor } from "@shopify/polaris-icons"
import TableExpand from "./TableExpand";
import func from "../../../../../util/func";
import EditModal from "./EditModal";
import GlobalVarModal from "./GlobalVarModal";
import { useLocation } from "react-router-dom";

const headers = [
    {
        value: "success",
        title: '',
        type: CellType.TEXT,
    },
    {
        value: "url",
        title: 'Endpoint',
        type: CellType.TEXT,
    },
    {
        value: "totalParameters",
        title: 'Total Parameters',
        type: CellType.TEXT,
    },
    {
        value: "dependentParameters",
        title: 'Dependent parameters',
        type: CellType.TEXT,
    },
    {
        value: "level",
        title: 'Level',
        type: CellType.TEXT,
    },
    {
        title: '  ',
        type: CellType.COLLAPSIBLE,
    }
]

const resourceName = {
    singular: 'Dependency table',
    plural: 'Dependency table',
};


const generateKeyForReplaceDetailMap = (apiCollectionId, url, method, key, isHeader, isUrlParam) => {
    return apiCollectionId + "#" + url + "#" + method + "#" + key + "#" + Boolean(isHeader) + "#" + Boolean(isUrlParam)
}

function DependencyTable() {
    const [loading, setLoading] = useState(false)
    const [runResults, setRunResults] = useState({})
    const [refresh, setRefresh] = useState(false)
    const [invokeLoading, setInvokeLoading] = useState(false)
    const [invokeLoadingSecond, setInvokeLoadingSecond] = useState(false)

    const [active, setActive] = useState(false);
    const [editApiCollectionId, setEditApiCollectionId] = useState(null)
    const [editUrl, setEditUrl] = useState(null)
    const [editMethod, setEditMethod] = useState(null)
    const [editData, setEditData] = useState([])

    const [globalVarActive, setGlobalVarActive] = useState(false)
    const location = useLocation()

    const queryParams = new URLSearchParams(location.search);
    const apiCollectionIdsString = queryParams.get('col_ids')
    const apiCollectionIds = JSON.parse(apiCollectionIdsString)

    function connectionToCollapsibleText(childApiCollectionId, childUrl, childMethod, connections, params, replaceDetailMap) {
        let res = []
        let store = new Set()
        Object.keys(connections).forEach((ele) => {
            let edges = connections[ele]["edges"]
            if (!edges ||edges.length === 0) return
            let edge = edges[0]
            store.add(ele)

            let key = generateKeyForReplaceDetailMap(childApiCollectionId, childUrl, childMethod, ele, Boolean(connections[ele]["isHeader"], Boolean(connections[ele]["isUrlParam"])))
            let val = replaceDetailMap.get(key)

            console.log(edge, ele)
            res.push({
                "parentUrl": edge["url"],
                "parentMethod": edge["method"],
                "parentParam": edge["param"],
                "childParam": ele,
                "childParamIsUrlParam": Boolean(connections[ele]["isUrlParam"]),
                "childParamIsHeader": Boolean(connections[ele]["isHeader"]),
                "value": val
            })
        })

        params.forEach(param => {
            if (store.has(param)) return

            let key = generateKeyForReplaceDetailMap(childApiCollectionId, childUrl, childMethod, param, false, false) // todo
            let val = replaceDetailMap.get(key)

            res.push({
                "parentUrl": null,
                "parentMethod": null,
                "parentParam": null,
                "childParam": param,
                "childParamIsUrlParam": false, // todo
                "childParamIsHeader": false,
                "value": val
            })
        })

        res = res.sort((a, b) => {
            return a.childParam.localeCompare(b.childParam)
        })

        return res
    }


    async function fetchTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
        setLoading(true)

        let result = await api.buildDependencyTable(apiCollectionIds, skip)
        let dependencyTableList = result["dependencyTableList"]
        let replaceDetails = result["replaceDetails"]

        let replaceDetailMap = new Map()
        replaceDetails.forEach((replaceDetail) => {
            let kvPairs = replaceDetail["kvPairs"]
            kvPairs.forEach((kvPair) => {
                let fullKey = generateKeyForReplaceDetailMap(replaceDetail["apiCollectionId"], replaceDetail["url"], replaceDetail["method"], kvPair["key"], kvPair["isHeader"], kvPair["isUrlParam"])
                replaceDetailMap.set(fullKey, kvPair["value"])
            })
        })

        let total = result["total"]
        let final = []

        dependencyTableList.forEach((val) => {
            let node = val["node"]
            let params = val["params"]
            let connections = node["connections"]
            let data = connectionToCollapsibleText(node["apiCollectionId"], node["url"], node["method"], connections, params, replaceDetailMap)
            let icon = null
            let key = node["method"] + " " + node["url"]
            let runResult = runResults[key]
            if (runResult) {
                if (runResult["success"]) {
                    icon = <Icon source={TickMinor} color="success" />
                } else {
                    icon = <Icon source={CancelMinor} color="critical" />
                }
            }

            let headerCount = 0;
            Object.values(connections).forEach(x => {
                if (x["isHeader"]) headerCount += 1;
            })

            let totalParams = params.length + headerCount

            final.push({
                "name": node["method"] + " " + node["url"],
                "id": node["method"] + " " + node["url"],
                "success": icon,
                "url": node["method"] + " " + node["url"],
                "level": node["maxDepth"],
                "totalParameters": totalParams,
                "dependentParameters": Object.keys(connections).length,
                "urls": data,
                "collapsibleRow": <TableExpand data={data} childApiCollectionId={node["apiCollectionId"]} childUrl={node["url"]} childMethod={node["method"]} showEditModal={showEditModal}/>
            })
        })

        setLoading(false)
        return { value: final, total: total };
    }

    const showEditModal = (apiCollectionId, url, method, data) => {
        setActive(true)
        setEditApiCollectionId(apiCollectionId)
        setEditUrl(url)
        setEditMethod(method)
        setEditData(data)
    }

    const modifyEditData = (childParam, value) => {
        const newEditData = editData.map((item) => {
            if (item.childParam === childParam) {
                return { ...item, value: value };
            }
            return item;
        });
        setEditData(newEditData);
    }

    function isBoolean(value) {
        return (
            typeof value === "boolean" || 
            (typeof value === "string" && (value.toLowerCase() === "true" || value.toLowerCase() === "false"))
        );
    }

    const isInvalidNumber = (value) => value.trim() === "" || isNaN(value);

    const convertDataToKVPairList = (data) => {
        let kvPairs = []
        data.forEach((x) => {
            if (!x["value"]) return
            kvPairs.push({
                "key": x["childParam"],
                "isHeader": x["childParamIsHeader"],
                "isUrlParam": x["childParamIsUrlParam"],
                "value": x["value"],
                "type": isInvalidNumber(x["value"]) ? (isBoolean(x["value"]) ? "BOOLEAN" : "STRING") : "INTEGER"
            })
        })

        return kvPairs
    }

    const saveEditData = async () => {
        let kvPairs = convertDataToKVPairList(editData)
        let resp = await api.saveReplaceDetails(editApiCollectionId, editUrl, editMethod, kvPairs)
        setActive(false)
        func.setToast(true, false, "Data updated successfully")
        setRefresh(!refresh)
    }

    const resultTable = (
        <GithubServerTable
            key={refresh}
            pageLimit={50}
            fetchData={fetchTableData}
            sortOptions={[]}
            resourceName={resourceName}
            filters={[]}
            hideQueryField={true}
            calenderFilter={false}
            headers={headers}
            loading={loading}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            tableId="dependency-table"
        />
    )

    const modalComponent = (
        <EditModal
            key="edit-modal"
            editData={editData} active={active} setActive={setActive} saveEditData={saveEditData} modifyEditData={modifyEditData}
        />
    )

    const globalVarModalComponent = (
        <GlobalVarModal
            key="global-var-modal"
            active={globalVarActive} setActive={setGlobalVarActive} apiCollectionIds={apiCollectionIds}
        />
    )

    const components = [resultTable, modalComponent, globalVarModalComponent]

    const invokeDependencyTable = (sourceCodeApis, updateFunc) => {
        if (invokeLoading || invokeLoadingSecond) return
        updateFunc(true)
        api.invokeDependencyTable(apiCollectionIds, sourceCodeApis).then((resp) => {
            let newCollectionId = resp["newCollectionId"]
            // let temp = {}
            // runResultList.forEach((runResult) => {
            //     let apiInfoKey = runResult["apiInfoKey"]
            //     temp[apiInfoKey["method"] + " " + apiInfoKey["url"]] = runResult
            // })

            updateFunc(false)
            // setRunResults(temp)
            // setRefresh(!refresh)

        if(!sourceCodeApis){

            const url = "/dashboard/observe/inventory/" + newCollectionId

            const forwardLink = (
                <HorizontalStack gap={1}>
                    <Text> API collection created successfully. Click </Text>
                    <Link url={url}>here</Link>
                    <Text> to view collection.</Text>
                </HorizontalStack>
            )

            func.setToast(true, false, forwardLink)
        }
        })
    }

    const [moreActions, setMoreActions] = useState(false)

    const secondaryActionsComponent = (
        <Popover
            active={moreActions}
            activator={(
                <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                    Invoke
                </Button>
            )}
            autofocusTarget="first-node"
            onClose={() => { setMoreActions(false) }}
            preferredAlignment="right"
        >
            <Popover.Pane fixed>
                <Popover.Section>
                    <Button plain monochrome onClick={() => invokeDependencyTable(false, setInvokeLoading)} removeUnderline>
                        {invokeLoading ? <Spinner size="small" /> : "Invoke"}
                    </Button>
                </Popover.Section>
                <Popover.Section>
                    <Button plain monochrome onClick={() => invokeDependencyTable(true, setInvokeLoadingSecond)} removeUnderline>
                        {invokeLoadingSecond ? <Spinner size="small" /> : "Invoke for source code APIs"}
                    </Button>
                </Popover.Section>
            </Popover.Pane>
        </Popover>
    )

    const globalVarsComponent = (
        <Button onClick={() => { setGlobalVarActive(true) }} >
            Edit Global vars
        </Button>
    )

    return (
        <PageWithMultipleCards
            title={
                <Text variant='headingLg'>
                    Dependency Table
                </Text>
            }
            isFirstPage={true}
            components={components}
            secondaryActions={secondaryActionsComponent}
            primaryAction={globalVarsComponent}
        />
    )
}

export default DependencyTable	