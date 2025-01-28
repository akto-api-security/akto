import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { Avatar, Box, Button, Popover, Modal, Tooltip } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import SampleDataList from "../../../components/shared/SampleDataList";
import { useEffect, useRef, useState } from "react";
import api from "../api";
import ApiSchema from "./ApiSchema";
import dashboardFunc from "../../transform";
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout";
import func from "@/util/func"
import transform from "../transform";
import ApiDependency from "./ApiDependency";

import { HorizontalDotsMinor } from "@shopify/polaris-icons"

function ApiDetails(props) {

    const { showDetails, setShowDetails, apiDetail, headers, getStatus, isGptActive } = props

    const [sampleData, setSampleData] = useState([])
    const [paramList, setParamList] = useState([])
    const [selectedUrl,setSelectedUrl] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [loading, setLoading] = useState(false)
    const [badgeActive, setBadgeActive] = useState(false)
    const [showDemerge, setShowDemerge] = useState(false)

    const ref = useRef(null)

    const fetchData = async() => {
        if(showDetails){
        setLoading(true)
        const { apiCollectionId, endpoint, method } = apiDetail
        setSelectedUrl({url: endpoint, method: method})
        await api.fetchSampleData(endpoint, apiCollectionId, method).then((res) => {
            api.fetchSensitiveSampleData(endpoint, apiCollectionId, method).then((resp) => {
                if (res.sampleDataList.length > 0) {
                    const commonMessages = transform.getCommonSamples(res.sampleDataList[0].samples,resp)
                    setSampleData(commonMessages)
                }else{
                    setSampleData(transform.prepareSampleData(resp, ''))
                }
            })
        })
        setTimeout(()=>{
            setLoading(false)
        },100)
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
    }}

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

    const badgeClicked = () => {
        setBadgeActive(true)
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

    function isDeMergeAllowed(){
        const { endpoint } = apiDetail
        if(!endpoint || endpoint === undefined){
            return false;
        }
        return (endpoint.includes("STRING") || endpoint.includes("INTEGER") || endpoint.includes("OBJECT_ID"))
    }

    const isDemergingActive = isDeMergeAllowed() ;


    const SchemaTab = {
        id: 'schema',
        content: "Schema",
        component: paramList.length > 0 && <Box paddingBlockStart={"4"}> 
        <ApiSchema
            data={paramList} 
            badgeActive={badgeActive}
            setBadgeActive={setBadgeActive}
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
    const DependencyTab = {
        id: 'dependency',
        content: "Dependency Graph",
        component: <Box paddingBlockStart={"2"}>
            <ApiDependency
                apiCollectionId={apiDetail['apiCollectionId']}
                endpoint={apiDetail['endpoint']}
                method={apiDetail['method']}
            />
        </Box>,
    }

    const deMergeApis = () => {
        const { apiCollectionId, endpoint, method } = apiDetail
        api.deMergeApi(apiCollectionId, endpoint, method).then((resp) => {
            func.setToast(true, false, "De-merging successful!!.")
            window.location.reload()
        })
    }

    const DeMergeButton = () => {
        return(
            <div className="button-fixed" style={{right: '12px', top: "64px"}}>
                <Popover 
                    active={showDemerge}
                    activator={
                        <Tooltip content="More actions" dismissOnMouseOut ><Button plain monochrome icon={HorizontalDotsMinor} onClick={() => setShowDemerge(!showDemerge)} /></Tooltip>
                    }
                    autofocusTarget="first-node"
                    onClose={() => setShowDemerge(false)}
                >
                    <Popover.Pane fixed>
                        <Popover.Section>
                            <Button plain monochrome removeUnderline size="slim" onClick={deMergeApis}>De merge</Button>
                        </Popover.Section>
                    </Popover.Pane>
                </Popover>
            </div>
        )
    }
    const headingComp = (
        <div style={{display: "flex", justifyContent: "space-between"}}  key="heading">
            <GithubCell
                width="40vw"
                data={apiDetail}
                headers={headers}
                getStatus={getStatus}
                isBadgeClickable={true}
                badgeClicked={badgeClicked}
            />
            <Box paddingBlockStart={"05"}>
                <Button plain onClick={() => func.copyToClipboard(apiDetail['method']+ " " + apiDetail['endpoint'], ref, "URL copied")}>
                    <Tooltip content="Copy endpoint" dismissOnMouseOut>
                        <div className="reduce-size">
                            <Avatar size="extraSmall" source="/public/copy_icon.svg" />
                        </div>
                    </Tooltip>
                    <Box ref={ref} />
                </Button>
            </Box>
        </div>
    )

    const components = [
        headingComp
            ,
        <LayoutWithTabs
            key="tabs"
            tabs={[SchemaTab, ValuesTab, DependencyTab]}
            currTab={() => { }}
        />
    ]

    const componentsArr = isDemergingActive ? [...components, <DeMergeButton key={"demerge"} />] : components
 
    const aktoGptButton = (
        <div 
            className={"button-fixed"}
            key="akto-gpt"
            style={{right: isDemergingActive ? "48px" : '24px'}}
        >
            <Button onClick={displayGPT} size="slim">Ask AktoGPT</Button> 
        </div>
    )

    const currentComponents = isGptActive ? [...componentsArr, aktoGptButton] : componentsArr

    return ( 
        <div>
            <FlyLayout
                title="API details"
                show={showDetails}
                setShow={setShowDetails}
                components={currentComponents}
                loading={loading}
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