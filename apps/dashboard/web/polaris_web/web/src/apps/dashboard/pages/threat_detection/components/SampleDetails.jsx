import { Badge, Box, Button, Divider, HorizontalStack, Modal, Text, Tooltip, VerticalStack, Popover, ActionList } from "@shopify/polaris";
import FlyLayout from "../../../components/layouts/FlyLayout";
import SampleDataList from "../../../components/shared/SampleDataList";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import func from "@/util/func";
import { useEffect, useState} from "react";
import testingApi from "../../testing/api"
import threatDetectionApi from "../api"
import issuesApi from "../../issues/api"
import MarkdownViewer from "../../../components/shared/MarkdownViewer";
import TooltipText from "../../../components/shared/TooltipText";
import ActivityTracker from "../../dashboard/components/ActivityTracker";
import settingFunctions from "../../settings/module";
import JiraTicketCreationModal from "../../../components/shared/JiraTicketCreationModal";
import transform from "../../testing/transform";

function SampleDetails(props) {
    const { showDetails, setShowDetails, data, title, moreInfoData, threatFiltersMap, eventId, eventStatus, onStatusUpdate, loading } = props
    let currentTemplateObj = threatFiltersMap[moreInfoData?.templateId]

    let severity = currentTemplateObj?.severity || "HIGH"
    const [remediationText, setRemediationText] = useState("")
    const [latestActivity, setLatestActivity] = useState([])
    const [showModal, setShowModal] = useState(false);  
    const [triageLoading, setTriageLoading] = useState(false);
    const [actionPopoverActive, setActionPopoverActive] = useState(false);

    // Jira ticket states
    const [jiraTicketUrl, setJiraTicketUrl] = useState(props.jiraTicketUrl || "");
    const [modalActive, setModalActive] = useState(false);
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});
    const [projId, setProjId] = useState("");
    const [issueType, setIssueType] = useState("");

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
                <Divider />
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
        fetchRemediationInfo();
        aggregateActivity();
    }, [moreInfoData?.templateId, data])

    useEffect(() => {
        setJiraTicketUrl(props.jiraTicketUrl || "")
    }, [props.jiraTicketUrl])

    const openTest = (id) => {
        const navigateUrl = window.location.origin + "/dashboard/protection/threat-policy?policy=" + id
        window.open(navigateUrl, "_blank")
    }

    const handleStatusChange = async (newStatus) => {
        if (!eventId) return;

        setActionPopoverActive(false);
        
        setTriageLoading(true);
        try {
            const response = await threatDetectionApi.updateMaliciousEventStatus({ eventId: eventId, status: newStatus });
            if (response?.updateSuccess) {
                // Update parent state instead of refreshing page
                if (onStatusUpdate) {
                    onStatusUpdate(newStatus);
                }
                const statusText = newStatus === 'UNDER_REVIEW' ? 'marked for review' :
                                 newStatus === 'IGNORED' ? 'ignored' : 'reactivated';
                func.setToast(true, false, `Event ${statusText} successfully`);
            } else {
                func.setToast(true, true, 'Failed to update event status');
            }
        } catch (error) {
        } finally {
            setTriageLoading(false);
        }
    }

    const handleJiraClick = async () => {
        if (!modalActive) {
            try {
                const jiraIntegration = await settingFunctions.fetchJiraIntegration();
                if (jiraIntegration.projectIdsMap !== null && Object.keys(jiraIntegration.projectIdsMap).length > 0) {
                    setJiraProjectMaps(jiraIntegration.projectIdsMap);
                    if (Object.keys(jiraIntegration.projectIdsMap).length > 0) {
                        setProjId(Object.keys(jiraIntegration.projectIdsMap)[0]);
                    }
                } else {
                    setProjId(jiraIntegration.projId || '');
                    setIssueType(jiraIntegration.issueType || '');
                }
            } catch (error) {
                func.setToast(true, true, "Failed to fetch Jira integration settings");
            }
        }
        setModalActive(!modalActive);
    }

    const createJiraTicket = async (threatEventId, projId, issueType) => {
        if (!threatEventId || !projId || !issueType) {
            func.setToast(true, true, "Missing required parameters");
            return;
        }

        try {
            // Extract host and endpoint from URL
            const url = moreInfoData?.url || "";
            let hostStr = "";
            let endPointStr = "";

            try {
                if (url.startsWith("http")) {
                    const urlObj = new URL(url);
                    hostStr = urlObj.host;
                    endPointStr = urlObj.pathname;
                } else {
                    hostStr = url;
                    endPointStr = url;
                }
            } catch (err) {
                hostStr = url;
                endPointStr = url;
            }

            // Build issue title and description (Jira-compatible formatting)
            const issueTitle = currentTemplateObj?.testName || currentTemplateObj?.name || moreInfoData?.templateId;
            const attackCount = data?.length || 0;
            const issueDescription = `Threat Detection Alert

Template ID: ${moreInfoData?.templateId}
Severity: ${severity}
Attack Count: ${attackCount}
Host: ${hostStr}
Endpoint: ${endPointStr}

Description:
${currentTemplateObj?.description || "No description available"}

Details:
${currentTemplateObj?.details || "N/A"}

Impact:
${currentTemplateObj?.impact || "N/A"}

Reference URL: ${window.location.href}`.trim();

            func.setToast(true, false, "Creating Jira Ticket");

            // Call createGeneralJiraTicket API (similar to ActionItemsContent)
            const response = await issuesApi.createGeneralJiraTicket({
                title: issueTitle,
                description: issueDescription,
                projId,
                issueType,
                threatEventId: threatEventId
            });

            if (response?.errorMessage) {
                func.setToast(true, true, response?.errorMessage);
                return;
            }

            // Update local state with the Jira ticket URL
            if (response?.jiraTicketUrl) {
                setJiraTicketUrl(response.jiraTicketUrl);
                func.setToast(true, false, "Jira Ticket Created Successfully");
            }

        } catch (error) {
            func.setToast(true, true, "Failed to create Jira ticket");
        }
    }

    const handleSaveAction = async () => {
        if (!projId || !issueType) {
            func.setToast(true, true, "Please select a project and issue type");
            return;
        }

        await createJiraTicket(eventId, projId, issueType);
        setModalActive(false);
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
                        <Popover
                            active={actionPopoverActive}
                            activator={
                                <Button
                                    size="slim"
                                    onClick={() => setActionPopoverActive(!actionPopoverActive)}
                                    disclosure
                                    loading={triageLoading}
                                    disabled={!eventId}
                                >
                                    Event Actions
                                </Button>
                            }
                            onClose={() => setActionPopoverActive(false)}
                        >
                            <ActionList
                                items={[
                                    eventStatus === 'UNDER_REVIEW' || eventStatus === 'TRIAGE' ? {
                                        content: 'Reactivate',
                                        onAction: () => handleStatusChange('ACTIVE'),
                                    } : {
                                        content: 'Mark for Review',
                                        onAction: () => handleStatusChange('UNDER_REVIEW'),
                                    },
                                    eventStatus === 'IGNORED' ? {
                                        content: 'Reactivate',
                                        onAction: () => handleStatusChange('ACTIVE'),
                                    } : {
                                        content: 'Ignore',
                                        onAction: () => handleStatusChange('IGNORED'),
                                    }
                                ].filter(item => item)}
                            />
                        </Popover>
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
                        {jiraTicketUrl ? (
                            transform.getJiraComponent(jiraTicketUrl)
                        ) : (
                            <JiraTicketCreationModal
                                activator={
                                    <Button
                                        size="slim"
                                        onClick={handleJiraClick}
                                        disabled={window.JIRA_INTEGRATED !== "true"}
                                    >
                                        Create Jira Ticket
                                    </Button>
                                }
                                modalActive={modalActive}
                                setModalActive={setModalActive}
                                handleSaveAction={handleSaveAction}
                                jiraProjectMaps={jiraProjectMaps}
                                projId={projId}
                                setProjId={setProjId}
                                issueType={issueType}
                                setIssueType={setIssueType}
                                issueId={eventId}
                            />
                        )}
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
        loading={loading}
    />
}

export default SampleDetails;