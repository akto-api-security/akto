import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { Box, Button, Modal } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import SampleDataList from "../../../components/shared/SampleDataList";
import { useEffect, useState } from "react";
import api from "../api";
import ApiSchema from "./ApiSchema";
import ObserveStore from "../observeStore"
import dashboardFunc from "../../transform";
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout";
import func from "@/util/func"

function ApiDetails(props) {

    const { showDetails, setShowDetails, apiDetail, headers, getStatus, isGptActive } = props

    const [sampleData, setSampleData] = useState([])
    const [paramList, setParamList] = useState([])
    const setSamples = ObserveStore(state => state.setSamples)
    const [selectedUrl,setSelectedUrl] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)

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

    const runTests = async(testsList) => {
        setIsGptScreenActive(false)
        const apiKeyInfo={
            apiCollectionId: apiDetail.apiCollectionId,
            url: selectedUrl.url,
            method: selectedUrl.method
        }
        await api.scheduleTestForCustomEndpoints(apiKeyInfo,func.timNow(),false,testsList,"akto_gpt_test",-1,-1)
        func.setToast(true,false,"Triggered tests successfully!")
    }

    useEffect(() => {
        fetchData();
    }, [apiDetail])

    function displayGPT(){
        setIsGptScreenActive(true)
        let requestObj = {key: "PARAMETER",jsonStr: sampleData[0]?.message,apiCollectionId: Number(apiDetail.apiCollectionId)}
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }


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
            width="35vw"
            nameWidth="32vw"
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

    const aktoGptButton = (
        <div className={"gpt-button-fixed"}>
            <Button onClick={displayGPT}>Ask AktoGPT</Button> 
        </div>
    )

    const currentComponents = isGptActive ? [...components, aktoGptButton] : components

    return (
        <div>
            <FlyLayout
                title="API details"
                show={showDetails}
                setShow={setShowDetails}
                components={currentComponents}
            />
            <Modal large open={isGptScreenActive} onClose={()=> setIsGptScreenActive(false)} title="Akto GPT">
                <Modal.Section flush>
                    <AktoGptLayout prompts={prompts} closeModal={()=> setIsGptScreenActive(false)} runCustomTests={(tests)=> runTests(tests)}/>
                </Modal.Section>
            </Modal>
        </div>
    )
}

export default ApiDetails