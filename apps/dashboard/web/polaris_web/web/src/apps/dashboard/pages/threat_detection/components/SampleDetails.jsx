import { Badge, Box, Button, Divider, HorizontalStack, Modal, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import FlyLayout from "../../../components/layouts/FlyLayout";
import SampleDataList from "../../../components/shared/SampleDataList";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import func from "@/util/func";
import { useEffect, useState } from "react";
import testingApi from "../../testing/api"
import MarkdownViewer from "../../../components/shared/MarkdownViewer";
import TooltipText from "../../../components/shared/TooltipText";
import ActivityTracker from "../../dashboard/components/ActivityTracker";
import ApiSchema from "../../observe/api_collections/ApiSchema";

function SampleDetails(props) {
    const { showDetails, setShowDetails, data, title, moreInfoData, threatFiltersMap } = props
    let currentTemplateObj = threatFiltersMap[moreInfoData?.templateId]

    let severity = currentTemplateObj?.severity || "HIGH"
    const [remediationText, setRemediationText] = useState("")
    const [latestActivity, setLatestActivity] = useState([])
    const [showModal, setShowModal] = useState(false);

    const fetchRemediationInfo = async() => {
        if(moreInfoData?.templateId !== undefined){
            testingApi.fetchRemediationInfo(moreInfoData?.templateId).then((resp) => {
                setRemediationText(resp)
            }).catch((err) => {
                setRemediationText("Remediation not configured for this test.")
            })
        }
        
    }
    const overviewComp = (
        <Box padding={"4"}>
            <VerticalStack gap={"5"}>
                <VerticalStack gap={"2"}>
                    <Text variant="headingMd">Description</Text>
                    <Text variant="bodyMd">{currentTemplateObj?.description || "-"}</Text>
                </VerticalStack>
                <Divider />
                <VerticalStack gap={"2"}>
                    <Text variant="headingMd">Details</Text>
                    <Text variant="bodyMd">{currentTemplateObj?.details || "-"}</Text>
                </VerticalStack>
                <Divider />
                <VerticalStack gap={"2"}>
                    <Text variant="headingMd">Impact</Text>
                    <Text variant="bodyMd">{currentTemplateObj?.impact || "-"}</Text>
                </VerticalStack>
            </VerticalStack>
        </Box>
    )

    const overviewTab = {
        id: "overview",
        content: 'Overview',
        component: currentTemplateObj && overviewComp
    }

    const aggregateActivity = () => {
        let timeMap = {};
        data.forEach((x) => {
            const key = x.ts
            if(timeMap.hasOwnProperty(key)){
                timeMap[key] = timeMap[key] + 1
            }else{
                timeMap[key] = 1
            }
        })
        const activityEvents =  Object.entries(timeMap)
        .map(([key, value]) => ({
            description: `Attacker attacked ${value} times`,
            timestamp: Number(key)
        }))
        .sort((a, b) => a.timestamp - b.timestamp);
        setLatestActivity(activityEvents)
    }

    const timelineTab = data.length > 0 && {
        id: "timeline",
        content: "Timeline",
        component: <ActivityTracker latestActivity={latestActivity} />
    }


    const SchemaTab = {
        id: 'schema',
        content: "Schema",
        component:  <Box paddingBlockStart={"4"}> 
            <ApiSchema
                apiInfo={{
                    apiCollectionId: moreInfoData?.apiCollectionId,
                    url: moreInfoData?.url,
                    method: moreInfoData?.method
                }}
            />
        </Box>
    }

    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: (
            <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
                <SampleDataList
                    key="Sample values"
                    heading={"Attempt"}
                    minHeight={"30vh"}
                    vertical={true}
                    sampleData={data?.map((result) => {
                        return { message: result.orig, highlightPaths: [], metadata: result.metadata }
                    })}
                />
            </Box>)
    }

    const remediationTab = remediationText.length > 0 && {
        id: "remediation",
        content: "Remediation",
        component: (<MarkdownViewer markdown={remediationText}></MarkdownViewer>)
    }

    useEffect(() => {
       fetchRemediationInfo()
       aggregateActivity()
    },[moreInfoData?.templateId, data])

    const openTest = (id) => {
        const navigateUrl = window.location.origin + "/dashboard/protection/threat-policy?policy=" + id
        window.open(navigateUrl, "_blank")
    }

    function TitleComponent () {
        return(
            <Box padding={"4"} paddingBlockStart={"0"} maxWidth="100%">
                <HorizontalStack wrap={false} align="space-between" gap={"6"}>
                    <Box maxWidth="50%">
                        <VerticalStack gap={"2"}>
                            <HorizontalStack gap={"2"} align="start">
                                <Button onClick={() => openTest(moreInfoData?.templateId)} removeUnderline plain monochrome>
                                    <Box maxWidth="180px">
                                        <TooltipText tooltip={moreInfoData?.templateId} text={moreInfoData?.templateId} textProps={{variant: 'headingMd'}}  />
                                    </Box>
                                </Button> 
                                <div className={`badge-wrapper-${severity}`}>
                                    <Badge size="small">{func.toSentenceCase(severity)}</Badge>
                                </div>
                            </HorizontalStack>
                            <HorizontalStack gap={"1"} wrap={false}>
                                <Tooltip content={moreInfoData?.url}>
                                    <Text color="subdued" variant="bodySm" truncate>{moreInfoData?.url}</Text>
                                </Tooltip>
                                {
                                    currentTemplateObj?.category?.name && (
                                        <>
                                            <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px'/>
                                            <Text color="subdued" variant="bodySm">{currentTemplateObj?.category?.name || "-"}</Text>
                                        </>
                                    )
                                }
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                    <HorizontalStack gap={"2"} wrap={false}>
                        <Modal
                            activator={<Button destructive size="slim" onClick={() => setShowModal(!showModal)}>Block IPs</Button>}
                            open={showModal}
                            onClose={() => setShowModal(false)}
                            primaryAction={{content: 'Save', onAction: () => setShowModal(false)}}
                            title={"Block IP ranges"}
                        >
                            <Modal.Section>
                                <Text variant="bodyMd" color="subdued">
                                    By blocking these IP ranges, no user will be able to access your application
                                    Are you sure you want to block these IPs
                                </Text>
                            </Modal.Section>
                        </Modal>
                        <Button size="slim" disabled>Create Jira Ticket</Button>
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
        )
    }

    const tabsComponent = (
        <LayoutWithTabs
            key={"tabs-comp"}
            tabs={ window.location.href.indexOf("guardrails") > -1 ? [ValuesTab] : [overviewTab, timelineTab, ValuesTab, remediationTab]}
            currTab = {() => {}}
        />
    )

    const currentComponents = [
        <TitleComponent/>, tabsComponent
    ]

    return <FlyLayout
        title={title || ""}
        show={showDetails}
        setShow={setShowDetails}
        components={currentComponents}
    />
}

export default SampleDetails;