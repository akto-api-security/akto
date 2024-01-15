import { useEffect, useState } from "react";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { CellType } from "../../../components/tables/rows/GithubRow";
import api from "../api";
import transform from "../transform";

const headers = [
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
    const [dependencyResults, setDependencyResults] = useState([])
    const [loading, setLoading] = useState(false)

    const queryParams = new URLSearchParams(location.search);
    const apiCollectionIdsString = queryParams.get('col_ids')
    const apiCollectionIds = JSON.parse(apiCollectionIdsString)

    async function buildDependencyTable(apiCollectionIds){
        setLoading(true)
        let result = await api.buildDependencyTable(apiCollectionIds)
        let dependencyTableList = result["dependencyTableList"]
        let av = []
        dependencyTableList.forEach((val) => {
            let node = val["node"]
            let params = val["params"]
            let connections = node["connections"]
            let data = connectionToCollapsibleText(connections, params)
            av.push({
                "url": node["url"],
                "level": node["maxDepth"],
                "totalParameters": params.length,
                "missingParameters": params.length - Object.keys(connections).length,
                "urls": data,
                "collapsibleRow": transform.getCollapisbleRowDependencyTable(data)
            })
        })

        setDependencyResults(av)
        console.log(result);
        setLoading(false)
    }

    useEffect(()=>{
        buildDependencyTable(apiCollectionIds)
    }, [])

    const resultTable = (
        <GithubSimpleTable
            key={"table"}
            data={dependencyResults}
            sortOptions={[]}
            resourceName={resourceName}
            filters={[]}
            headers={headers}
            selectable={false}
            loading={loading}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            notHighlightOnselected={true}
        />
    )
    return resultTable
}

export default DependencyTable	