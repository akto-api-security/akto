import React, { useCallback, useEffect, useMemo, useState, useRef } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import func from '@/util/func'
import transform from '../transform'
import SampleDataList from '../../../components/shared/SampleDataList'
import SampleData from '../../../components/shared/SampleData'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs'
import { Badge, Box, Button, Divider, HorizontalStack, Icon, Popover, Text, VerticalStack, Link} from '@shopify/polaris'
import api from '../../observe/api'
import issuesApi from "../../issues/api"
import testingApi from "../api"
import GridRows from '../../../components/shared/GridRows'
import { useNavigate } from 'react-router-dom'
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo'
import "./style.css"
import ActivityTracker from '../../dashboard/components/ActivityTracker'
import observeFunc from "../../observe/transform.js"
import settingFunctions from '../../settings/module.js'
import issuesFunctions from '@/apps/dashboard/pages/issues/module';
import JiraTicketCreationModal from '../../../components/shared/JiraTicketCreationModal.jsx'
import MarkdownViewer from '../../../components/shared/MarkdownViewer.jsx'
import InlineEditableText from '../../../components/shared/InlineEditableText.jsx'
import ChatInterface from '../../../components/shared/ChatInterface.jsx'
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper.js'
import ApiGroups from '../../../components/shared/ApiGroups'
import ForbiddenRole from '../../../components/shared/ForbiddenRole'
import LegendLabel from './LegendLabel.jsx'

function TestRunResultFlyout(props) {


    const { selectedTestRunResult, loading, issueDetails ,getDescriptionText, infoState, createJiraTicket, jiraIssueUrl, showDetails, setShowDetails, isIssuePage, remediationSrc, azureBoardsWorkItemUrl, serviceNowTicketUrl, conversations, showForbidden} = props
    const [remediationText, setRemediationText] = useState("")
    const [fullDescription, setFullDescription] = useState(false)
    const [rowItems, setRowItems] = useState([])
    const [apiInfo, setApiInfo] = useState({})
    const [popoverActive, setPopoverActive] = useState(false)
    const [modalActive, setModalActive] = useState(false)
    const [jiraProjectMaps,setJiraProjectMap] = useState({})
    const [issueType, setIssueType] = useState('');
    const [projId, setProjId] = useState('')

    const [boardsModalActive, setBoardsModalActive] = useState(false)
    const [projectToWorkItemsMap, setProjectToWorkItemsMap] = useState({})
    const [projectId, setProjectId] = useState('')
    const [workItemType, setWorkItemType] = useState('')

    const [serviceNowModalActive, setServiceNowModalActive] = useState(false)
    const [serviceNowTables, setServiceNowTables] = useState([])
    const [serviceNowTable, setServiceNowTable] = useState('')

    const [description, setDescription] = useState("")
    const [editDescription, setEditDescription] = useState("")
    const [isEditingDescription, setIsEditingDescription] = useState(false)

    const [vulnerabilityAnalysisError, setVulnerabilityAnalysisError] = useState(null)
    const [refreshFlag, setRefreshFlag] = useState(Date.now().toString())
    const [labelsText, setLabelsText] = useState("")
    
    const fetchRemediationInfo = useCallback (async (testId) => {
        if (testId && testId.length > 0) {
            await testingApi.fetchRemediationInfo(testId).then((resp) => {
                setRemediationText(resp)
            }).catch((err) => {
                setRemediationText("Remediations not configured for this test.")
            })
        }
    })

    const fetchApiInfo = useCallback( async(apiInfoKey) => {
        let apiInfoData = {}
        if(apiInfoKey !== null){
            await api.fetchEndpoint(apiInfoKey).then((res) => {
                apiInfoData = JSON.parse(JSON.stringify(res))
                setApiInfo(apiInfoData)
            })
            let sensitiveParam = ""
            const sensitiveParamsSet = new Set();
            await api.loadSensitiveParameters(apiInfoKey.apiCollectionId,apiInfoKey.url, apiInfoKey.method).then((resp) => {
                resp?.data?.endpoints.forEach((x, index) => {
                    sensitiveParamsSet.add(x.subTypeString.toUpperCase())
                })
                const unique = sensitiveParamsSet.size
                let index = 0;
                sensitiveParamsSet.forEach((x) =>{
                    sensitiveParam += x;
                    if(index !== unique - 1){
                        sensitiveParam += ", "
                    }
                    index++
                })
            })

            setRowItems(transform.getRowInfo(issueDetails.severity,apiInfoData,issueDetails.jiraIssueUrl,sensitiveParam,issueDetails.testRunIssueStatus === 'IGNORED', issueDetails.azureBoardsWorkItemUrl, issueDetails.servicenowIssueUrl, issueDetails.ticketId))
        }
    },[issueDetails])

    const navigate = useNavigate()

    useEffect(() => {
       if(issueDetails && Object.keys(issueDetails).length > 0){       
            fetchApiInfo(issueDetails.id.apiInfoKey)
       }
       setTimeout(() => {
            setDescription(issueDetails?.description || "")
            setEditDescription(issueDetails?.description || "")
       }, [100])
    },[issueDetails?.id?.apiInfoKey])

    const handleSaveDescription = async () => {
        setIsEditingDescription(false);
        if (editDescription === description) {
            return;
        }
        try {
            await testingApi.updateIssueDescription(issueDetails.id, editDescription);
            setDescription(editDescription);
            func.setToast(true, false, "Description saved successfully");
            // Use state variable for updates, no need to re-fetch
            if (typeof props.setIssueDetails === 'function') {
                props.setIssueDetails({ ...issueDetails, description: editDescription });
            }
        } catch (err) {
            console.error("Failed to save description:", err);
            func.setToast(true, true, "Failed to save description");
        }
    }

    useEffect(() => {
        if (!remediationSrc) {
            fetchRemediationInfo("tests-library-master/remediation/"+selectedTestRunResult.testCategoryId+".md")
        } else {
            setRemediationText(remediationSrc)
        }
    }, [selectedTestRunResult.testCategoryId, remediationSrc])


    function ignoreAction(ignoreReason){
        const severity = (selectedTestRunResult && selectedTestRunResult.vulnerable) ? issueDetails.severity : "";
        let obj = {}
        if(issueDetails?.testRunIssueStatus !== "IGNORED"){
            obj = {[selectedTestRunResult.id]: severity.toUpperCase()}
        }
        issuesApi.bulkUpdateIssueStatus([issueDetails.id], "IGNORED", ignoreReason, obj ).then((res) => {
            func.setToast(true, false, `Issue ignored`)
        })
    }

    function reopenAction(){
        issuesApi.bulkUpdateIssueStatus([issueDetails.id], "OPEN", "" ).then((res) => {
            func.setToast(true, false, "Issue re-opened")
        })
    }

    const handleJiraClick = async() => {
        if(!modalActive){
            const jirIntegration = await settingFunctions.fetchJiraIntegration()
            if(jirIntegration.projectIdsMap !== null && Object.keys(jirIntegration.projectIdsMap).length > 0){
                setJiraProjectMap(jirIntegration.projectIdsMap)
                if(Object.keys(jirIntegration.projectIdsMap).length > 0){
                    setProjId(Object.keys(jirIntegration.projectIdsMap)[0])
                }
            }else{
                setProjId(jirIntegration.projId)
                setIssueType(jirIntegration.issueType)
            }
        }
        setModalActive(!modalActive)
    }

    const handleSaveAction = (id, labels) => {
        if(projId.length > 0 && issueType.length > 0){
            // Use labels parameter if provided, otherwise fall back to state
            const labelsToUse = labels !== undefined ? labels : labelsText;
            createJiraTicket(id, projId, issueType, labelsToUse)
            setModalActive(false)
        }else{
            func.setToast(true, true, "Invalid project id or issue type")
        }
    }

    const handleAzureBoardClick = async() => {
        if(!boardsModalActive){
            const azureBoardsIntegration = await settingFunctions.fetchAzureBoardsIntegration()
            if(azureBoardsIntegration.projectToWorkItemsMap != null && Object.keys(azureBoardsIntegration.projectToWorkItemsMap).length > 0){
                setProjectToWorkItemsMap(azureBoardsIntegration.projectToWorkItemsMap)
                if(Object.keys(azureBoardsIntegration.projectToWorkItemsMap).length > 0){
                    setProjectId(Object.keys(azureBoardsIntegration.projectToWorkItemsMap)[0])
                    setWorkItemType(Object.values(azureBoardsIntegration.projectToWorkItemsMap)[0]?.[0])
                }
            }else{
                setProjectId(azureBoardsIntegration?.projectId)
                setWorkItemType(azureBoardsIntegration?.workItemType)
            }
        }
        setBoardsModalActive(!boardsModalActive)
    }

    const handleAzureBoardWorkitemCreation = async(id) => {
        if(projectId.length > 0 && workItemType.length > 0){
            let customABWorkItemFieldsPayload = [];
            try {
                customABWorkItemFieldsPayload = issuesFunctions.prepareCustomABWorkItemFieldsPayload(projectId, workItemType);
            } catch (error) {
                setToast(true, true, "Please fill all required fields before creating a Azure boards work item.");
                return;
            }
            
            await issuesApi.createAzureBoardsWorkItem(issueDetails.id, projectId, workItemType, window.location.origin, customABWorkItemFieldsPayload).then((res) => {
                func.setToast(true, false, "Work item created")
            }).catch((err) => {
                func.setToast(true, true, err?.response?.data?.errorMessage || "Error creating work item")
            })
        }else{
            func.setToast(true, true, "Invalid project id or work item type")
        }
        setBoardsModalActive(false)
    }

    const handleServiceNowClick = async() => {
        if(!serviceNowModalActive){
            const serviceNowIntegration = await settingFunctions.fetchServiceNowIntegration()
            if(serviceNowIntegration.tableNames && serviceNowIntegration.tableNames.length > 0){
                setServiceNowTables(serviceNowIntegration.tableNames)
                setServiceNowTable(serviceNowIntegration.tableNames[0])
            }
        }
        setServiceNowModalActive(!serviceNowModalActive)
    }

    const handleServiceNowTicketCreation = async(id) => {
        if(serviceNowTable && serviceNowTable.length > 0){
            func.setToast(true, false, "Please wait while we create your ServiceNow ticket.")
            await issuesApi.createServiceNowTicket(issueDetails.id, serviceNowTable).then((res) => {
                if(res?.errorMessage) {
                    func.setToast(true, false, res?.errorMessage)
                } else {
                    func.setToast(true, false, "ServiceNow ticket created successfully")
                }
            }).catch((err) => {
                func.setToast(true, true, err?.response?.data?.errorMessage || "Error creating ServiceNow ticket")
            })
        }else{
            func.setToast(true, true, "Invalid ServiceNow table")
        }
        setServiceNowModalActive(false)
    }
    
    const issues = [{
        content: 'False positive',
        onAction: () => { ignoreAction("False positive") }
    },
    {
        content: 'Acceptable risk',
        onAction: () => { ignoreAction("Acceptable risk") }
    },
    {
        content: 'No time to fix',
        onAction: () => { ignoreAction("No time to fix") }
    }]

    const  reopen =  [{
        content: 'Reopen',
        onAction: () => { reopenAction() }
    }]

    const handleClose = () => {
        const navigateUrl = window.location.pathname.split("result")[0]
        navigate(navigateUrl) 
    }

    const openTest = () => {
        const navUrl = window.location.origin + "/dashboard/test-editor/" + selectedTestRunResult.testCategoryId
        window.open(navUrl, "_blank")
    }

    function ActionsComp (){
        const issuesActions = issueDetails?.testRunIssueStatus === "IGNORED" ? [...issues, ...reopen] : issues
        return(
            issueDetails?.id &&
        <Popover
            activator={<Button disclosure onClick={() => setPopoverActive(!popoverActive)}>Triage</Button>}
            active={popoverActive}
            onClose={() => setPopoverActive(false)}
            autofocusTarget="first-node"
            preferredPosition="below"
            preferredAlignment="left"
        >
            <Popover.Pane fixed>
                <Popover.Section>
                    <VerticalStack gap={"4"}>
                        {issuesActions.map((issue, index) => {
                            return(
                                <div style={{cursor: 'pointer'}} onClick={() => {issue.onAction(); setPopoverActive(false)}} key={index}>
                                    {issue.content}
                                </div>
                            )
                        })}
                    </VerticalStack>
                </Popover.Section>
            </Popover.Pane>
        </Popover>
    )}
    function TitleComponent() {
        const severity = (selectedTestRunResult && selectedTestRunResult.vulnerable) ? issueDetails.severity : ""
        return(
            <div style={{display: 'flex', justifyContent: "space-between", alignItems: "flex-start", gap:"24px", padding: "16px", paddingTop: '0px'}}>
                <VerticalStack gap={"2"}>
                    <Box width="100%">
                        <div style={{display: 'flex', gap: '4px', marginBottom: '4px'}} className='test-title'>
                            <Button removeUnderline plain monochrome onClick={() => openTest()}>
                                <Text variant="headingSm" alignment="start" breakWord>{selectedTestRunResult?.name}</Text>
                            </Button>
                            {(severity && severity?.length > 0) ? (issueDetails?.testRunIssueStatus === 'IGNORED' ? <Badge size='small'>Ignored</Badge> : <Box className={`badge-wrapper-${severity.toUpperCase()}`}><Badge size="small" status={observeFunc.getColor(severity)}>{severity}</Badge></Box>) : null}
                        </div>

                        {
                            isEditingDescription ? (
                                <InlineEditableText
                                    textValue={editDescription || ""}
                                    setTextValue={setEditDescription}
                                    handleSaveClick={handleSaveDescription}
                                    setIsEditing={setIsEditingDescription}
                                    placeholder={"Add a brief description"}
                                    maxLength={64}
                                />
                            ) : (
                                !description ? (
                                    <Button plain removeUnderline onClick={() => setIsEditingDescription(true)}>
                                        Add description
                                    </Button>
                                ) : (
                                    <Button plain removeUnderline onClick={() => setIsEditingDescription(true)}>
                                        <Text as="span" variant="bodyMd" color="subdued" alignment="start">
                                            {description}
                                        </Text>
                                    </Button>
                                    )
                            )
                        }
                    </Box>
                    <HorizontalStack gap={"2"}>
                        <Text color="subdued" variant="bodySm">{transform.getTestingRunResultUrl(selectedTestRunResult)}</Text>
                        <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px'/>
                        <Text color="subdued" variant="bodySm">{selectedTestRunResult?.testCategory}</Text>
                    </HorizontalStack>
                    <ApiGroups collectionIds={apiInfo?.collectionIds} />
                </VerticalStack>
                <HorizontalStack gap={2} wrap={false}>
                    <ActionsComp />

                    {selectedTestRunResult && selectedTestRunResult.vulnerable &&
                        <HorizontalStack gap={2} wrap={false}>
                            <JiraTicketCreationModal
                                activator={window.JIRA_INTEGRATED === 'true' ? <Button id={"create-jira-ticket-button"} primary onClick={handleJiraClick} disabled={jiraIssueUrl !== "" || window.JIRA_INTEGRATED !== "true"}>Create Jira Ticket</Button> : <></>}
                                modalActive={modalActive}
                                setModalActive={setModalActive}
                                handleSaveAction={handleSaveAction}
                                jiraProjectMaps={jiraProjectMaps}
                                setProjId={setProjId}
                                setIssueType={setIssueType}
                                projId={projId}
                                issueType={issueType}
                                issueId={issueDetails.id}
                                labelsText={labelsText}
                                setLabelsText={setLabelsText}
                            />
                            <JiraTicketCreationModal
                                activator={window.AZURE_BOARDS_INTEGRATED === 'true' ? <Button id={"create-azure-boards-ticket-button"} primary onClick={handleAzureBoardClick} disabled={azureBoardsWorkItemUrl !== "" || window.AZURE_BOARDS_INTEGRATED !== "true"}>Create Work Item</Button> : <></>}
                                modalActive={boardsModalActive}
                                setModalActive={setBoardsModalActive}
                                handleSaveAction={handleAzureBoardWorkitemCreation}
                                jiraProjectMaps={projectToWorkItemsMap}
                                projId={projectId}
                                setProjId={setProjectId}
                                issueType={workItemType}
                                setIssueType={setWorkItemType}
                                issueId={issueDetails.id}
                                isAzureModal={true}
                            />
                            <JiraTicketCreationModal
                                activator={window.SERVICENOW_INTEGRATED === 'true' ? <Button id={"create-servicenow-ticket-button"} primary onClick={handleServiceNowClick} disabled={serviceNowTicketUrl !== "" || window.SERVICENOW_INTEGRATED !== "true"}>Create ServiceNow Ticket</Button> : <></>}
                                modalActive={serviceNowModalActive}
                                setModalActive={setServiceNowModalActive}
                                handleSaveAction={handleServiceNowTicketCreation}
                                jiraProjectMaps={serviceNowTables}
                                setProjId={setServiceNowTable}
                                setIssueType={() => {}}
                                projId={serviceNowTable}
                                issueType=""
                                issueId={issueDetails.id}
                                isServiceNowModal={true}
                            />
                        </HorizontalStack>
                    }
                </HorizontalStack>
            </div>
        )
    }

    const dataExpiredComponent = <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
        <Text>
            Sample data might not be available for non-vulnerable tests more than 2 months ago.
            <br/>
            Please contact <Link url="mailto:support@akto.io">support@akto.io</Link> for more information.
        </Text>
    </Box>

    const dataStoreTime = 2 * 30 * 24 * 60 * 60;
    const dataExpired = func.timeNow() - (selectedTestRunResult?.endTimestamp || func.timeNow()) > dataStoreTime

    // Move state outside to prevent reset on component recreation
    const hasAnalyzedRef = useRef(false);
    const [vulnerabilityHighlights, setVulnerabilityHighlights] = useState({});
    
    // Component that handles vulnerability analysis only when mounted
    const ValuesTabContent = React.memo(() => {
        
        useEffect(() => {
            // Check if vulnerability highlighting is enabled (use existing GPT feature flag)
            //const isVulnerabilityHighlightingEnabled = window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"] && 
            //    window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"]?.isGranted === true;
            const isVulnerabilityHighlightingEnabled = false;

            if (!hasAnalyzedRef.current && selectedTestRunResult?.vulnerable && selectedTestRunResult?.testResults && isVulnerabilityHighlightingEnabled) {
                hasAnalyzedRef.current = true;
                setVulnerabilityAnalysisError(null);

                const timeoutId = setTimeout(() => {
                    const analysisPromises = selectedTestRunResult.testResults.map(async (result, idx) => {
                        // Parse the message if it's a string
                        let messageObj = result.message;
                        if (typeof messageObj === 'string') {
                            try {
                                messageObj = JSON.parse(messageObj);
                            } catch (e) {
                                console.error('Failed to parse message string for idx', idx, e);
                                return null;
                            }
                        }

                        if (messageObj && messageObj.response && messageObj.response.body) {
                            try {
                                // Minimal payload structure for optimal LLM analysis
                                const testResultData = {
                                    resultId: selectedTestRunResult?.id ? `${selectedTestRunResult.id}_${idx}` : null,
                                    testContext: {
                                        category: selectedTestRunResult?.testCategoryId || selectedTestRunResult?.testCategory || 'UNKNOWN',
                                        description: issueDetails?.description || selectedTestRunResult?.name || '',
                                        severity: issueDetails?.severity || 'HIGH',
                                        cwe: issueDetails?.cwe || []
                                    },
                                    request: {
                                        method: messageObj.request?.method || 'GET',
                                        url: messageObj.request?.url || '',
                                        contentType: messageObj.request?.headers?.['content-type'] || 'application/json',
                                        authorization: messageObj.request?.headers?.['authorization'] || null,
                                        userAgent: messageObj.request?.headers?.['user-agent'] || null,
                                        xForwardedFor: messageObj.request?.headers?.['x-forwarded-for'] || null,
                                        origin: messageObj.request?.headers?.['origin'] || null,
                                        referer: messageObj.request?.headers?.['referer'] || null
                                    },
                                    response: {
                                        statusCode: messageObj.response?.statusCode || 200,
                                        headers: messageObj.response?.headers || {},
                                        body: messageObj.response?.body || ''
                                    }
                                };
                            
                                const response = await testingApi.analyzeVulnerability(
                                    JSON.stringify(testResultData),
                                    'redteaming'
                                );

                                const analysisData = response.analysisResult || response;

                                if (analysisData?.vulnerableSegments?.length > 0) {
                                    const enhancedSegments = analysisData.vulnerableSegments.map(segment => ({
                                        ...segment,
                                        vulnerabilityType: selectedTestRunResult?.testCategoryId || selectedTestRunResult?.testCategory || 'UNKNOWN',
                                        severity: issueDetails?.severity || 'HIGH'
                                    }));

                                    setVulnerabilityHighlights(prev => ({
                                        ...prev,
                                        [idx]: enhancedSegments
                                    }));
                                    setTimeout(() => {
                                        setRefreshFlag(Date.now().toString());
                                    }, 10)
                                } else {
                                    setVulnerabilityHighlights(prev => {
                                        const newState = { ...prev };
                                        delete newState[idx];
                                        return newState;
                                    });
                                }
                                return { idx, success: true };
                            } catch (error) {
                                console.error('Failed to analyze vulnerability for result', idx, error);
                                return { idx, success: false };
                            }
                        }
                        return null;
                    });
                
                    Promise.allSettled(analysisPromises).finally(() => {
                    });
                }, 60);

                return () => clearTimeout(timeoutId);
            }
        }, [selectedTestRunResult?.id,]);
        
        if (dataExpired && !selectedTestRunResult?.vulnerable) {
            return dataExpiredComponent;
        }
        
        if (!selectedTestRunResult?.testResults) {
            return null;
        }
        
        
        return (
            <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
                <VerticalStack gap="3">
                    <Box padding="3" background="bg-surface-secondary" borderRadius="2">
                        <LegendLabel/>
                    </Box>
                    <SampleDataList
                        key="Sample values"
                        heading={"Attempt"}
                        minHeight={"30vh"}
                        vertical={true}
                        sampleData={
                            selectedTestRunResult?.testResults.map((result, idx) => {
                                if (result.errors && result.errors.length > 0) {
                                    let errorList = result.errors.join(", ");
                                    return { errorList: errorList }
                                }
                                // Add vulnerability highlights only for response
                                let vulnerabilitySegments = vulnerabilityHighlights[idx] || [];
                                if (result.originalMessage || result.message) {
                                    return {
                                        originalMessage: result.originalMessage,
                                        message: result.message,
                                        vulnerabilitySegments
                                    }
                                }
                                return { errorList: "No data found" }
                            })}
                        isNewDiff={true}
                        vulnerable={selectedTestRunResult?.vulnerable}
                        vulnerabilityAnalysisError={vulnerabilityAnalysisError}
                    />
                </VerticalStack>
            </Box>
        );
    });

    const conversationTab = useMemo(() => {
        if (typeof selectedTestRunResult !== "object") return null;
        return {
            id: 'evidence',
            content: "Evidence",
            component: <ChatInterface conversations={conversations} sort={false}/>
        }
    }, [selectedTestRunResult, conversations])
    
    const ValuesTab = useMemo(() => {
        if (typeof selectedTestRunResult !== "object") return null;
        return {
            id: 'values',
            content: "Evidence",
            component: <ValuesTabContent />
        }
    }, [selectedTestRunResult, dataExpired, issueDetails, refreshFlag])

    const finalResultTab = conversations?.length > 0 ? conversationTab : ValuesTab

    function RowComp ({cardObj}){
        const {title, value, tooltipContent} = cardObj
        return(
            value ? <Box width="224px">
                <VerticalStack gap={"2"}>
                    <TitleWithInfo
                        textProps={{variant:"bodyMd", fontWeight:"semibold"}}
                        titleText={title}
                        tooltipContent={tooltipContent}
                    />
                    {value}
                </VerticalStack>
            </Box>: null
        )
    }

    const testResultDetailsComp = (
        <GridRows columns={3} items={rowItems} CardComponent={RowComp} />
    )

    function MoreInformationComp({ infoState }){
        infoState = infoState.filter((item) => item.title !== 'Jira')

        return(
            <VerticalStack gap={"2"}>
                {
                    infoState.map((item, index) => {
                        const {title, content, tooltipContent, icon} = item

                        if (content === null || content === undefined || content === "") return null

                        return (
                            <VerticalStack gap={"5"} key={index}>
                                <VerticalStack gap={"2"} >
                                    <HorizontalStack gap="1_5-experimental">
                                        <Box><Icon source={icon} color='subdued' /></Box>
                                        <TitleWithInfo
                                            textProps={{ variant: "bodyMd", fontWeight: "semibold", color: "subdued" }}
                                            titleText={title}
                                            tooltipContent={tooltipContent}
                                        />
                                    </HorizontalStack>
                                    {content}
                                </VerticalStack>
                            </VerticalStack>
                        )
                    })
                }
            </VerticalStack>
        )
    }

    const overviewComp = (
        <Box padding={"4"}>
            <VerticalStack gap={"5"}>
                <VerticalStack gap={"2"}>
                    <TitleWithInfo
                        textProps={{variant:"bodyMd", fontWeight:"semibold", color:"subdued"}}
                        titleText={"Description"}
                        tooltipContent={"A brief description about the test from test library"}
                    />
                    <Box as="span">
                        {
                            getDescriptionText(fullDescription) 
                        }
                        <Button plain onClick={() => setFullDescription(!fullDescription)}> {fullDescription ? "Less" : "More"} information</Button>
                    </Box>
                </VerticalStack>
                <Divider />
                {testResultDetailsComp}  
                {infoState && typeof infoState === 'object' && infoState.length > 0 ? (
                    <>
                        <Divider />
                        <MoreInformationComp infoState={infoState} />
                    </>
                ) : null}
            </VerticalStack>
        </Box>
    )
    const overviewTab = {
        id: "overview",
        content: 'Overview',
        component: issueDetails.id && overviewComp
    }

    const generateActivityEvents = (issue) => {
        const activityEvents = []

        const createdEvent = {
            description: 'Found the issue',
            timestamp: issue.creationTime,
        }
        activityEvents.push(createdEvent)


        if (issue.testRunIssueStatus === 'IGNORED') {
            const ignoredEvent = {
                description: `Issue marked as IGNORED - ${issue.ignoreReason || 'No reason provided'}`,
                timestamp: issue.lastUpdated,
                user: issue.lastUpdatedBy || 'System'
            }
            activityEvents.push(ignoredEvent)
        }

        if (issue.testRunIssueStatus === 'FIXED') {
            const fixedEvent = {
                description: `Issue marked as FIXED`,
                timestamp: issue.lastUpdated,
                user: issue.lastUpdatedBy || 'System'
            }
            activityEvents.push(fixedEvent)
        }

        return activityEvents
    }
    const latestActivity = generateActivityEvents(issueDetails).reverse()

    const timelineTab = (selectedTestRunResult && selectedTestRunResult.vulnerable) && {
        id: "timeline",
        content: "Timeline",
        component: <ActivityTracker latestActivity={latestActivity} />
    }

    const remediationTab = (selectedTestRunResult && selectedTestRunResult.vulnerable) && {
        id: "remediation",
        content: "Remediation",
        component: (<MarkdownViewer markdown={remediationText}></MarkdownViewer>)
    }

    const errorTab = {
        id: "error",
        content: "Attempt",
        component:  (selectedTestRunResult.errors && selectedTestRunResult.errors.length > 0 ) && <Box padding={"4"}>
            {
            selectedTestRunResult?.errors?.map((error, i) => {
                if (error) {
                    let data = {
                        original : error
                    }
                    return (
                        <SampleData key={i} data={data} language="yaml" minHeight="450px" wordWrap={false}/>
                      )
                }
            })
          }
        </Box>
    }

    const attemptTab = !selectedTestRunResult.testResults ? errorTab : finalResultTab

    const tabsComponent = (
        <LayoutWithTabs
            key={issueDetails?.id}
            tabs={issueDetails?.id ? [overviewTab,timelineTab,finalResultTab, remediationTab].filter(Boolean): [attemptTab]}
            currTab = {() => {}}
        />
    )

    const currentComponents = showForbidden 
        ? [<Box padding="4"><ForbiddenRole /></Box>]
        : [<TitleComponent/>, tabsComponent]

    const title = isIssuePage ? "Issue details" : mapLabel('Test result', getDashboardCategory())

    return (
        <FlyLayout
            title={title}
            show={showDetails}
            setShow={setShowDetails}
            components={currentComponents}
            loading={loading}
            showDivider={true}
            newComp={true}
            handleClose={handleClose}
            isHandleClose={true}
        />
    )
}

export default TestRunResultFlyout