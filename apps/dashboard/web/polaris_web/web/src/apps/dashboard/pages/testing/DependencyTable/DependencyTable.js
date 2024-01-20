import { Button, Icon, Spinner, Text, VerticalStack } from "@shopify/polaris";
import { useState } from "react";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import { CellType } from "../../../components/tables/rows/GithubRow";
import api from "../api";
import transform from "../transform";
import { TickMinor, CancelMinor} from "@shopify/polaris-icons"

const headers = [
    {
        value: "success",
        title: '',
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
        value: "missingParameters",
        title: 'Missing parameters',
        type: CellType.TEXT,
    },
    {
        value: "level",
        title: 'Level',
        type: CellType.TEXT,
    },
    {
        title: '',
        type: CellType.COLLAPSIBLE
    }
]

const resourceName = {
    singular: 'Dependency table',
    plural: 'Dependency table',
};

function connectionToCollapsibleText(connections, params) {
    let res = []
    let store = new Set()
    Object.keys(connections).forEach((ele) => {
        let edges = connections[ele]["edges"]
        let edge = edges[0]
        let data =  ele + " = " + edge["method"] + " " + edge["url"] + " " + edge["param"]
        store.add(ele)
        res.push({"url": data})
    })

    params.forEach(x => {
        if (store.has(x)) return
        res.push({"url": x + " = ?" })
    })

    return res
}

function DependencyTable() {
    const [loading, setLoading] = useState(false)
    const [runResults, setRunResults] = useState({})
    const [refresh, setRefresh] = useState(false)
    const [invokeLoading, setInvokeLoading] = useState(false)

    const queryParams = new URLSearchParams(location.search);
    const apiCollectionIdsString = queryParams.get('col_ids')
    const apiCollectionIds = JSON.parse(apiCollectionIdsString)

    async function fetchTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
        setLoading(true)

        let result = await api.buildDependencyTable(apiCollectionIds, skip)
        let dependencyTableList = result["dependencyTableList"]
        let total = result["total"]
        let final = []

        dependencyTableList.forEach((val) => {
            let node = val["node"]
            let params = val["params"]
            let connections = node["connections"]
            let data = connectionToCollapsibleText(connections, params)
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
            final.push({
                "success": icon,
                "url": node["method"] + " " + node["url"],
                "level": node["maxDepth"],
                "totalParameters": params.length,
                "missingParameters": params.length - Object.keys(connections).length,
                "urls": data,
                "collapsibleRow": transform.getCollapisbleRowDependencyTable(data)
            })
        })

        setLoading(false)
        return { value: final, total: total };
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
        /> 
    )

    const components = [resultTable]

    const invokeDependencyTable = () => {
        setInvokeLoading(true)
        api.invokeDependencyTable().then((resp) => {
            let runResultList = resp["runResults"]
            let temp = {}
            runResultList.forEach((runResult) => {
                let apiInfoKey = runResult["apiInfoKey"]
                temp[apiInfoKey["method"] + " " + apiInfoKey["url"]] = runResult
            })

            setInvokeLoading(false)
            setRunResults(temp)
            setRefresh(!refresh)
        })
    }

    const secondaryActionsComponent = (
        <Button onClick={invokeDependencyTable}  primary  >
            {invokeLoading ? <Spinner size="small" /> : "Invoke"}
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
        />
    )
}

export default DependencyTable	