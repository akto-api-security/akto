import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { Box, Button, Popover, Modal, Tooltip, VerticalStack, HorizontalStack, Text, TextField } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import SampleDataList from "../../../components/shared/SampleDataList";
import { useEffect, useState } from "react";
import api from "../api";
import ApiSchema from "./ApiSchema";
import dashboardFunc from "../../transform";
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout";
import func from "@/util/func"
import transform from "../transform";
import ApiDependency from "./ApiDependency";
import RunTest from "./RunTest";
import PersistStore from "../../../../main/PersistStore";
import values from "@/util/values";

import { HorizontalDotsMinor, FileMinor } from "@shopify/polaris-icons"
import LocalStore from "../../../../main/LocalStorageStore";

function ApiDetails(props) {

    const { showDetails, setShowDetails, apiDetail, headers, getStatus, isGptActive } = props

    const localCategoryMap = LocalStore.getState().categoryMap
    const localSubCategoryMap = LocalStore.getState().subCategoryMap

    const [sampleData, setSampleData] = useState([])
    const [paramList, setParamList] = useState([])
    const [selectedUrl, setSelectedUrl] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [loading, setLoading] = useState(false)
    const [badgeActive, setBadgeActive] = useState(false)
    const [showMoreActions, setShowMoreActions] = useState(false)
    const setSelectedSampleApi = PersistStore(state => state.setSelectedSampleApi)
    const [disabledTabs, setDisabledTabs] = useState([])
    const [description, setDescription] = useState("")
    const [showDescriptionModal, setShowDescriptionModal] = useState(false)

    const [useLocalSubCategoryData, setUseLocalSubCategoryData] = useState(false)

    const statusFunc = getStatus ? getStatus : (x) => {
        try {
            if (paramList && paramList.length > 0 &&
                paramList.filter(x => x?.nonSensitiveDataType).map(x => x.subTypeString).includes(x)) {
                return "info"
            }
        } catch (e) {

        }
        return "warning"
    }

    const fetchData = async () => {
        if (showDetails) {
            setLoading(true)
            const { apiCollectionId, endpoint, method, description } = apiDetail
            setSelectedUrl({ url: endpoint, method: method })
            api.checkIfDependencyGraphAvailable(apiCollectionId, endpoint, method).then((resp) => {
                if (!resp.dependencyGraphExists) {
                    setDisabledTabs(["dependency"])
                } else {
                    setDisabledTabs([])
                }
            })

            setTimeout(() => {
                setDescription(description == null ? "" : description)
            }, 100)
            let commonMessages = []
            await api.fetchSampleData(endpoint, apiCollectionId, method).then((res) => {
                api.fetchSensitiveSampleData(endpoint, apiCollectionId, method).then(async (resp) => {
                    if (resp.sensitiveSampleData && Object.keys(resp.sensitiveSampleData).length > 0) {
                        if (res.sampleDataList.length > 0) {
                            commonMessages = transform.getCommonSamples(res.sampleDataList[0].samples, resp)
                        } else {
                            commonMessages = transform.prepareSampleData(resp, '')
                        }
                    } else {
                        let sensitiveData = []
                        await api.loadSensitiveParameters(apiCollectionId, endpoint, method).then((res3) => {
                            sensitiveData = res3.data.endpoints;
                        })
                        let samples = res.sampleDataList.map(x => x.samples)
                        samples = samples.flat()
                        let newResp = transform.convertSampleDataToSensitiveSampleData(samples, sensitiveData)
                        commonMessages = transform.prepareSampleData(newResp, '')
                    }
                    setSampleData(commonMessages)
                })
            })
            setTimeout(() => {
                setLoading(false)
            }, 100)
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

                    try {
                        resp.data.params?.forEach(x => {
                            if (!values?.skipList.includes(x.subTypeString) && !x?.savedAsSensitive && !x?.sensitive) {
                                x.nonSensitiveDataType = true
                            }
                        })
                    } catch (e){
                    }
                    setParamList(resp.data.params)
                })
            })
        }
    }

    const handleSaveDescription = () => {
        const { apiCollectionId, endpoint, method } = apiDetail;
        
        // Check for special characters
        const specialChars = /[!@#$%^&*()\-_=+\[\]{}\\|;:'",.<>/?~]/;
        if (specialChars.test(description)) {
            func.setToast(true, true, "Description contains special characters that are not allowed.");
            return;
        }
        
        setShowDescriptionModal(false);
        
        api.saveEndpointDescription(apiCollectionId, endpoint, method, description)
            .then(() => {
                func.setToast(true, false, "Description saved successfully");
            })
            .catch((err) => {
                console.error("Failed to save description:", err);
                func.setToast(true, true, "Failed to save description. Please try again.");
            });
    };

    const runTests = async (testsList) => {
        setIsGptScreenActive(false)
        const apiKeyInfo = {
            apiCollectionId: apiDetail.apiCollectionId,
            url: selectedUrl.url,
            method: selectedUrl.method
        }
        await api.scheduleTestForCustomEndpoints(apiKeyInfo, func.timNow(), false, testsList, "akto_gpt_test", -1, -1)
        func.setToast(true, false, "Triggered tests successfully!")
    }

    const badgeClicked = () => {
        setBadgeActive(true)
    }

    useEffect(() => {
        if (
            (localCategoryMap && Object.keys(localCategoryMap).length > 0) &&
            (localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0)
        ) {
            setUseLocalSubCategoryData(true)
        }

        fetchData();
    }, [apiDetail])

    function displayGPT() {
        setIsGptScreenActive(true)
        let requestObj = { key: "PARAMETER", jsonStr: sampleData[0]?.message, apiCollectionId: Number(apiDetail.apiCollectionId) }
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }

    function isDeMergeAllowed() {
        const { endpoint } = apiDetail
        if (!endpoint || endpoint === undefined) {
            return false;
        }
        return (endpoint.includes("STRING") || endpoint.includes("INTEGER") || endpoint.includes("OBJECT_ID"))
    }

    const openTest = () => {
        const apiKeyInfo = {
            apiCollectionId: apiDetail["apiCollectionId"],
            url: selectedUrl.url,
            method: {
                "_name": selectedUrl.method
            }

        }
        setSelectedSampleApi(apiKeyInfo)
        const navUrl = window.location.origin + "/dashboard/test-editor/REMOVE_TOKENS"
        window.open(navUrl, "_blank")
    }

    const isDemergingActive = isDeMergeAllowed();

    const SchemaTab = {
        id: 'schema',
        content: "Schema",
        component: paramList.length > 0 && <Box paddingBlockStart={"4"}> 
        <ApiSchema
            data={paramList} 
            badgeActive={badgeActive}
            setBadgeActive={setBadgeActive}
            apiInfo={
                {
                    apiCollectionId: apiDetail.apiCollectionId,
                    url: apiDetail.endpoint,
                    method: apiDetail.method
                }
            }
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
    let newData = apiDetail
    newData['copyEndpoint'] = {
        method: apiDetail.method,
        endpoint: apiDetail.endpoint
    }

    try {
        newData['nonSensitiveTags'] = [...new Set(paramList.filter(x => x?.nonSensitiveDataType).map(x => x.subTypeString))]
    } catch (e){
    }
    try {
        newData['sensitiveTags'] = apiDetail?.sensitiveTags && apiDetail?.sensitiveTags.length > 0 ? apiDetail?.sensitiveTags : 
        [...new Set(paramList.filter(x => x?.savedAsSensitive || x?.sensitive).map(x => x.subTypeString))]
    } catch (e){
    }

    const headingComp = (
        <div style={{ display: "flex", justifyContent: "space-between" }} key="heading">
            <div style={{ display: "flex", flexDirection: "column"}}>
                <div style={{ display: "flex", gap: '8px' }}>
                    <GithubCell
                        width="32vw"
                        data={newData}
                        headers={headers}
                        getStatus={statusFunc}
                        isBadgeClickable={true}
                        badgeClicked={badgeClicked}
                    />
                </div>
                <HorizontalStack gap={2} align="start" blockAlign="start">
                    {!description && (
                        <Button plain onClick={() => setShowDescriptionModal(true)} textAlign="left">
                            Add description
                        </Button>
                    )}
                    {description && (
                        <Button plain onClick={() => setShowDescriptionModal(true)} textAlign="left">
                            <Text as="span" variant="bodyMd" color="subdued" alignment="start">
                                {description}
                            </Text>
                        </Button>
                    )}
                </HorizontalStack>
            </div>
            <div style={{ display: "flex", gap: '8px' }}>
                <RunTest
                    apiCollectionId={apiDetail["apiCollectionId"]}
                    endpoints={[apiDetail]}
                    filtered={true}
                    useLocalSubCategoryData={useLocalSubCategoryData}
                    preActivator={false}
                    disabled={window.USER_ROLE === "GUEST"}
                />
                <Box>
                    <Tooltip content="Open URL in test editor" dismissOnMouseOut>
                        <Button monochrome onClick={() => openTest()} icon={FileMinor} />
                    </Tooltip>
                </Box>
                {
                    isGptActive || isDemergingActive ? <Popover
                        active={showMoreActions}
                        activator={
                            <Tooltip content="More actions" dismissOnMouseOut ><Button plain monochrome icon={HorizontalDotsMinor} onClick={() => setShowMoreActions(!showMoreActions)} /></Tooltip>
                        }
                        autofocusTarget="first-node"
                        onClose={() => setShowMoreActions(false)}
                    >
                        <Popover.Pane fixed>
                            <Popover.Section>
                                <VerticalStack gap={"2"}>
                                    {isGptActive ? <Button plain monochrome removeUnderline onClick={displayGPT} size="slim">Ask AktoGPT</Button> : null}
                                    {isDemergingActive ? <Button plain monochrome removeUnderline size="slim" onClick={deMergeApis}>De merge</Button> : null}
                                </VerticalStack>
                            </Popover.Section>
                        </Popover.Pane>
                    </Popover> : null
                }

            </div>
        </div>
    )

    const components = [
        headingComp,
        <LayoutWithTabs
            key="tabs"
            tabs={[ValuesTab, SchemaTab, DependencyTab]}
            currTab={() => { }}
            disabledTabs={disabledTabs}
        />
    ]

    return (
        <div>
            <FlyLayout
                title="API details"
                show={showDetails}
                setShow={setShowDetails}
                components={components}
                loading={loading}
            />
            <Modal
                open={showDescriptionModal}
                onClose={() => setShowDescriptionModal(false)}
                title="API Endpoint Description"
                primaryAction={{
                    content: 'Save',
                    onAction: handleSaveDescription
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: () => setShowDescriptionModal(false)
                    }
                ]}
            >
                <Modal.Section>
                    <TextField
                        label="Description"
                        value={description}
                        onChange={(value) => {
                            // Remove all special characters from the input
                            const filteredValue = value.replace(/[!@#$%^&*()\-_=+\[\]{}\\|;:'",.<>/?~]/g, '');
                            setDescription(filteredValue);
                        }}
                        multiline={4}
                        autoComplete="off"
                        maxLength={64}
                        placeholder="Add a brief description for this endpoint"
                        helpText={`${description.length}/64 characters`}
                    />
                </Modal.Section>
            </Modal>
            <Modal large open={isGptScreenActive} onClose={() => setIsGptScreenActive(false)} title="Akto GPT">
                <Modal.Section flush>
                    <AktoGptLayout prompts={prompts} closeModal={() => setIsGptScreenActive(false)} runCustomTests={(tests) => runTests(tests)} />
                </Modal.Section>
            </Modal>
        </div>
    )
}

export default ApiDetails