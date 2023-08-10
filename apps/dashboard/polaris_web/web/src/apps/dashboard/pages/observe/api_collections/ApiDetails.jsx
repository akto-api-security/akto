import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { Box } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import SampleDataList from "../../../components/shared/SampleDataList";
import { useEffect, useState } from "react";
import api from "../api";
import ApiSchema from "./ApiSchema";
import ObserveStore from "../observeStore"

function ApiDetails(props) {

    const { showDetails, setShowDetails, apiDetail, headers, getStatus } = props

    const [sampleData, setSampleData] = useState([])
    const [paramList, setParamList] = useState([])
    const setSamples = ObserveStore(state => state.setSamples)
    const setSelectedUrl = ObserveStore(state => state.setSelectedUrl)

    async function fetchData() {
        const { apiCollectionId, endpoint, method } = apiDetail
        setSelectedUrl({url: endpoint, method: method})
        await api.fetchSampleData(endpoint, apiCollectionId, method).then((res) => {
            if (res.sampleDataList.length > 0) {
                setSampleData(res.sampleDataList[0].samples.map((sample) => ({ message: sample, highlightPaths: [] })))
                setSamples(res.sampleDataList[0].samples[0])
            }else{
                setSamples("")
            }
        })

        await api.loadParamsOfEndpoint(apiCollectionId, endpoint, method).then(resp => {
            api.loadSensitiveParameters(apiCollectionId, endpoint, method).then(allSensitiveFields => {
                allSensitiveFields.data.endpoints.filter(x => x.sensitive).forEach(sensitive => {
                    let index = resp.data.params.findIndex(x =>
                        x.param === sensitive.param &&
                        x.isHeader === sensitive.isHeader &&
                        x.responseCode === sensitive.responseCode
                    )

                    if (index > -1 && !sensitive.subType) {
                        resp.data.params[index].savedAsSensitive = true
                        if (!resp.data.params[index].subType) {
                            resp.data.params[index].subType = { "name": "CUSTOM" }
                        } else {
                            resp.data.params[index].subType = JSON.parse(JSON.stringify(resp.data.params[index].subType))
                        }
                    }

                })
                setParamList(resp.data.params)
            })
        })

    }

    useEffect(() => {
        fetchData();
    }, [apiDetail])


    const SchemaTab = {
        id: 'schema',
        content: "Schema",
        component: paramList.length > 0 && <Box paddingBlockStart={"4"}> 
        <ApiSchema
            data={paramList} 
        />
        </Box>
    }
    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: sampleData.length > 0 && <Box paddingBlockStart={"4"}>
            <SampleDataList
                key="Sample values"
                sampleData={sampleData}
                heading={"Sample values"}
                minHeight={"35vh"}
                vertical={true}
            />
        </Box>,
    }

    const components = [
            <GithubCell
            key="heading"
            width="30vw"
            data={apiDetail}
            headers={headers}
            getStatus={getStatus}
        />,
        <LayoutWithTabs
            key="tabs"
            tabs={[SchemaTab, ValuesTab]}
            currTab={() => { }}
        />
    ]

    return (
        <FlyLayout
            title="API details"
            show={showDetails}
            setShow={setShowDetails}
            components={components}
        />
    )
}

export default ApiDetails