import React, { useCallback, useEffect, useMemo, useState, useRef } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import func from '@/util/func'
import transform from '../transform'
import SampleDataList from '../../../components/shared/SampleDataList'
import SampleData from '../../../components/shared/SampleData'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs'
import { Badge, Box, Button, Divider, HorizontalStack, Icon, Popover, Text, Tooltip, VerticalStack, Link, ActionList } from '@shopify/polaris'
import { EditMinor, FileMinor } from '@shopify/polaris-icons'
import CompulsoryDescriptionModal from "../../issues/components/CompulsoryDescriptionModal.jsx"
import api from '../../observe/api'
import issuesApi from "../../issues/api"
import testingApi from "../api"
import testEditorApi from "../../test_editor/api"
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
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper.js'
import ApiGroups from '../../../components/shared/ApiGroups'
import ForbiddenRole from '../../../components/shared/ForbiddenRole'
import LegendLabel from './LegendLabel.jsx'
import TestRunResultChat from './TestRunResultChat.jsx'
import AiExecutionJourney from './components/AiExecutionJourney'
import AskAktoSection from './AskAktoSection.jsx'
import PersistStore from '../../../../main/PersistStore'

const SKIPPED_TEST_DOCS_URL = "https://docs.akto.io/api-security-testing/concepts/skipped-test-cases";
const SKIP_ERROR_KEYWORDS = ["skipping execution"];

function isSkippedTestError(errorText) {
  return SKIP_ERROR_KEYWORDS.some(keyword => errorText?.includes(keyword));
}

function TestRunResultFlyout(props) {
    const { selectedTestRunResult, loading, issueDetails, getDescriptionText, infoState, createJiraTicket, createDevRevTicket, jiraIssueUrl, showDetails, setShowDetails, isIssuePage, remediationSrc, azureBoardsWorkItemUrl, serviceNowTicketUrl, devrevWorkUrl, wizFindingUrl, conversations, conversationRemediationText, showForbidden, aiSummary, aiSummaryLoading, aiMessages, aiLoading, onGenerateAiOverview, onSendFollowUp, toolsCalls, runAutomatedTests } = props
    const [remediationText, setRemediationText] = useState("")
    const [fullDescription, setFullDescription] = useState(false)
    const [rowItems, setRowItems] = useState([])
    const [apiInfo, setApiInfo] = useState({})
    const [popoverActive, setPopoverActive] = useState(false)
    const [modalActive, setModalActive] = useState(false)
    const [jiraProjectMaps, setJiraProjectMap] = useState({})
    const [issueType, setIssueType] = useState('');
    const [projId, setProjId] = useState('')

    const [boardsModalActive, setBoardsModalActive] = useState(false)
    const [projectToWorkItemsMap, setProjectToWorkItemsMap] = useState({})
    const [projectId, setProjectId] = useState('')
    const [workItemType, setWorkItemType] = useState('')

    const [serviceNowModalActive, setServiceNowModalActive] = useState(false)
    const [serviceNowTables, setServiceNowTables] = useState([])
    const [serviceNowTable, setServiceNowTable] = useState('')

    const [devrevModalActive, setDevRevModalActive] = useState(false)
    const [devrevParts, setDevRevParts] = useState([])
    const [devrevPartId, setDevRevPartId] = useState('')
    const [devrevWorkItemType, setDevRevWorkItemType] = useState('issue')

    const [description, setDescription] = useState("")
    const [editDescription, setEditDescription] = useState("")
    const [isEditingDescription, setIsEditingDescription] = useState(false)

    const [severityPopoverActive, setSeverityPopoverActive] = useState(false)
    const [isHoveringSeverity, setIsHoveringSeverity] = useState(false)

    const [vulnerabilityAnalysisError, setVulnerabilityAnalysisError] = useState(null)
    const [refreshFlag, setRefreshFlag] = useState(Date.now().toString())
    const [labelsText, setLabelsText] = useState("")

    // Compulsory description modal states
    const [compulsoryDescriptionModal, setCompulsoryDescriptionModal] = useState(false)
    const [pendingIgnoreAction, setPendingIgnoreAction] = useState(null)
    const [mandatoryDescription, setMandatoryDescription] = useState("")
    const [modalLoading, setModalLoading] = useState(false)
    const [compulsorySettings, setCompulsorySettings] = useState({
        falsePositive: false,
        noTimeToFix: false,
        acceptableFix: false
    })

    const setSelectedSampleApi = PersistStore(state => state.setSelectedSampleApi)

    const normalizeRemediationText = (value) => {
        if (value && typeof value === 'object' && typeof value.message === 'string') {
            return value.message;
        }
        if (typeof value === 'string') {
            try {
                const parsed = JSON.parse(value);
                if (parsed && typeof parsed === 'object' && typeof parsed.message === 'string') {
                    return parsed.message;
                }
            } catch (_) {}
            return value;
        }
        return '';
    }

    const fetchRemediationInfo = useCallback(async (testId) => {
        if (testId && testId.length > 0) {
            await testingApi.fetchRemediationInfo(testId).then((resp) => {
                setRemediationText(normalizeRemediationText(resp))
            }).catch((err) => {
                setRemediationText("Remediations not configured for this test.")
            })
        }
    })

    const fetchApiInfo = useCallback(async (apiInfoKey) => {
        let apiInfoData = {}
        if (apiInfoKey !== null) {
            await api.fetchEndpoint(apiInfoKey).then((res) => {
                apiInfoData = JSON.parse(JSON.stringify(res))
                setApiInfo(apiInfoData)
            })
            let sensitiveParam = ""
            const sensitiveParamsSet = new Set();
            await api.loadSensitiveParameters(apiInfoKey.apiCollectionId, apiInfoKey.url, apiInfoKey.method).then((resp) => {
                resp?.data?.endpoints.forEach((x, index) => {
                    sensitiveParamsSet.add(x.subTypeString.toUpperCase())
                })
                const unique = sensitiveParamsSet.size
                let index = 0;
                sensitiveParamsSet.forEach((x) => {
                    sensitiveParam += x;
                    if (index !== unique - 1) {
                        sensitiveParam += ", "
                    }
                    index++
                })
            })

            setRowItems(transform.getRowInfo(issueDetails.severity, apiInfoData, issueDetails.jiraIssueUrl, sensitiveParam, issueDetails.testRunIssueStatus === 'IGNORED', issueDetails.azureBoardsWorkItemUrl, issueDetails.servicenowIssueUrl, issueDetails.ticketId, issueDetails.devrevWorkUrl, issueDetails.wizFindingUrl))
        }
    }, [issueDetails])

    const navigate = useNavigate()

    useEffect(() => {
        if (issueDetails && Object.keys(issueDetails).length > 0) {
            fetchApiInfo(issueDetails.id.apiInfoKey)
        }
        setTimeout(() => {
            setDescription(issueDetails?.description || "")
            setEditDescription(issueDetails?.description || "")
        }, [100])
    }, [issueDetails?.id?.apiInfoKey])

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
            func.setToast(true, true, "Failed to save description");
        }
    }

    const handleSeverityUpdate = async (newSeverity) => {
        setSeverityPopoverActive(false);
        try {
            await issuesApi.bulkUpdateIssueSeverity([issueDetails.id], newSeverity);
            func.setToast(true, false, `Severity updated to ${newSeverity}`);

            // Update issueDetails object
            issueDetails.severity = newSeverity;

            // Trigger re-render using existing refreshFlag state
            setRefreshFlag(Date.now().toString());

            // Update local state
            if (typeof props.setIssueDetails === 'function') {
                props.setIssueDetails({ ...issueDetails, severity: newSeverity });
            }
            // Trigger refresh if needed
            if (typeof props.refreshIssueDetails === 'function') {
                props.refreshIssueDetails();
            }
        } catch (err) {
            func.setToast(true, true, "Failed to update severity");
        }
    }

    useEffect(() => {
        if (remediationSrc) {
            // Priority 1: Use remediation from backend/subCategoryMap
            setRemediationText(normalizeRemediationText(remediationSrc))
        } else if (conversationRemediationText) {
            // Priority 2: Use remediation text extracted from conversations
            setRemediationText(normalizeRemediationText(conversationRemediationText))
        } else {
            // Priority 3: Fall back to fetching from file
            fetchRemediationInfo("tests-library-master/remediation/" + selectedTestRunResult.testCategoryId + ".md")
        }
    }, [selectedTestRunResult.testCategoryId, remediationSrc, conversationRemediationText, fetchRemediationInfo])

    // Fetch compulsory description settings
    useEffect(() => {
        const fetchCompulsorySettings = async () => {
            try {
                const accountConfig = await settingFunctions.fetchAccountConfig();

                if (accountConfig?.compulsoryDescription) {
                    setCompulsorySettings(accountConfig.compulsoryDescription);
                }
            } catch (error) {

            }
        };
        fetchCompulsorySettings();
    }, []);

    // Map ignore reason text to key
    const getIgnoreReasonKey = (ignoreReason) => {
        const reasonMap = {
            "False positive": "falsePositive",
            "Acceptable risk": "acceptableFix",
            "No time to fix": "noTimeToFix"
        };
        return reasonMap[ignoreReason] || ignoreReason;
    };

    // Use keys directly for reasons and compulsorySettings
    const requiresDescription = (reasonKey) => {
        return compulsorySettings[reasonKey] || false;
    };

    const handleIgnoreWithDescription = () => {
        if (pendingIgnoreAction && mandatoryDescription.trim()) {
            // Update description first
            testingApi.updateIssueDescription(issueDetails.id, mandatoryDescription).then(() => {
                performIgnoreAction(pendingIgnoreAction.ignoreReason, mandatoryDescription);
                setCompulsoryDescriptionModal(false);
                setPendingIgnoreAction(null);
                setMandatoryDescription("");
            }).catch((err) => {
                func.setToast(true, true, "Failed to update description");
            });
        }
    };

    useEffect(() => {
        if (compulsoryDescriptionModal && pendingIgnoreAction) {
            setMandatoryDescription("");
            setModalLoading(false);
        }
    }, [compulsoryDescriptionModal, pendingIgnoreAction]);

    const performIgnoreAction = (ignoreReason, description = "") => {
        const severity = (selectedTestRunResult && selectedTestRunResult.vulnerable) ? issueDetails.severity : "";
        let obj = {}
        if (issueDetails?.testRunIssueStatus !== "IGNORED") {
            obj = { [selectedTestRunResult.id]: severity.toUpperCase() }
        }
        issuesApi.bulkUpdateIssueStatus([issueDetails.id], "IGNORED", ignoreReason, { description }).then((res) => {
            func.setToast(true, false, `Issue ignored${description ? " with description" : ""}`)
        })
    }


    function ignoreAction(ignoreReason) {
        const reasonKey = getIgnoreReasonKey(ignoreReason);
        if (requiresDescription(reasonKey)) {
            setPendingIgnoreAction({ ignoreReason });
            setCompulsoryDescriptionModal(true);
            return;
        }
        performIgnoreAction(ignoreReason);
    }

    function reopenAction() {
        issuesApi.bulkUpdateIssueStatus([issueDetails.id], "OPEN", "").then((res) => {
            func.setToast(true, false, "Issue re-opened")
        })
    }

    const handleJiraClick = async () => {
        if (!modalActive) {
            const jirIntegration = await settingFunctions.fetchJiraIntegration()
            if (jirIntegration.projectIdsMap !== null && Object.keys(jirIntegration.projectIdsMap).length > 0) {
                setJiraProjectMap(jirIntegration.projectIdsMap)
                if (Object.keys(jirIntegration.projectIdsMap).length > 0) {
                    setProjId(Object.keys(jirIntegration.projectIdsMap)[0])
                }
            } else {
                setProjId(jirIntegration.projId)
                setIssueType(jirIntegration.issueType)
            }
        }
        setModalActive(!modalActive)
    }

    const handleSaveAction = (id, labels) => {
        if (projId.length > 0 && issueType.length > 0) {
            // Use labels parameter if provided, otherwise fall back to state
            const labelsToUse = labels !== undefined ? labels : labelsText;
            createJiraTicket(id, projId, issueType, labelsToUse)
            setModalActive(false)
        } else {
            func.setToast(true, true, "Invalid project id or issue type")
        }
    }

    const handleAzureBoardClick = async () => {
        if (!boardsModalActive) {
            const azureBoardsIntegration = await settingFunctions.fetchAzureBoardsIntegration()
            if (azureBoardsIntegration.projectToWorkItemsMap != null && Object.keys(azureBoardsIntegration.projectToWorkItemsMap).length > 0) {
                setProjectToWorkItemsMap(azureBoardsIntegration.projectToWorkItemsMap)
                if (Object.keys(azureBoardsIntegration.projectToWorkItemsMap).length > 0) {
                    setProjectId(Object.keys(azureBoardsIntegration.projectToWorkItemsMap)[0])
                    setWorkItemType(Object.values(azureBoardsIntegration.projectToWorkItemsMap)[0]?.[0])
                }
            } else {
                setProjectId(azureBoardsIntegration?.projectId)
                setWorkItemType(azureBoardsIntegration?.workItemType)
            }
        }
        setBoardsModalActive(!boardsModalActive)
    }

    const handleAzureBoardWorkitemCreation = async (id) => {
        if (projectId.length > 0 && workItemType.length > 0) {
            let customABWorkItemFieldsPayload = [];
            try {
                customABWorkItemFieldsPayload = issuesFunctions.prepareCustomABWorkItemFieldsPayload(projectId, workItemType);
            } catch (error) {
                func.setToast(true, true, "Please fill all required fields before creating a Azure boards work item.");
                return;
            }

            await issuesApi.createAzureBoardsWorkItem(issueDetails.id, projectId, workItemType, window.location.origin, customABWorkItemFieldsPayload).then((res) => {
                func.setToast(true, false, "Work item created")
            }).catch((err) => {
                func.setToast(true, true, err?.response?.data?.errorMessage || "Error creating work item")
            })
        } else {
            func.setToast(true, true, "Invalid project id or work item type")
        }
        setBoardsModalActive(false)
    }

    const handleServiceNowClick = async () => {
        if (!serviceNowModalActive) {
            const serviceNowIntegration = await settingFunctions.fetchServiceNowIntegration()
            if (serviceNowIntegration.tableNames && serviceNowIntegration.tableNames.length > 0) {
                setServiceNowTables(serviceNowIntegration.tableNames)
                setServiceNowTable(serviceNowIntegration.tableNames[0])
            }
        }
        setServiceNowModalActive(!serviceNowModalActive)
    }

    const handleServiceNowTicketCreation = async (id) => {
        if (serviceNowTable && serviceNowTable.length > 0) {
            func.setToast(true, false, "Please wait while we create your ServiceNow ticket.")
            await issuesApi.createServiceNowTicket(issueDetails.id, serviceNowTable).then((res) => {
                if (res?.errorMessage) {
                    func.setToast(true, false, res?.errorMessage)
                } else {
                    func.setToast(true, false, "ServiceNow ticket created successfully")
                }
            }).catch((err) => {
                func.setToast(true, true, err?.response?.data?.errorMessage || "Error creating ServiceNow ticket")
            })
        } else {
            func.setToast(true, true, "Invalid ServiceNow table")
        }
        setServiceNowModalActive(false)
    }

    const handleDevRevClick = async () => {
        if (!devrevModalActive) {
            const devrevIntegration = await settingFunctions.fetchDevRevIntegration()
            const partsMap = devrevIntegration.partsMap || {}
            const partsArray = Object.entries(partsMap).map(([id, name]) => ({ id, name }))
            if (partsArray.length > 0) {
                setDevRevParts(partsArray)
                setDevRevPartId(partsArray[0].id)
            }
        }
        setDevRevModalActive(!devrevModalActive)
    }

    const handleDevRevTicketCreation = async (issueId, labels) => {
        if (devrevPartId && devrevPartId.length > 0) {
            await createDevRevTicket(issueDetails.id, devrevPartId, devrevWorkItemType)
            setDevRevModalActive(false)
        } else {
            func.setToast(true, true, "Invalid DevRev part")
        }
    }

    const handleWizFindingCreation = async () => {
        const items = [issueDetails?.id]
        await issuesApi.createWizFindings(items).then((res) => {
            func.setToast(true, false, "Wiz finding creation initiated.")
        }).catch((err) => {
            func.setToast(true, true, "Error creating wiz finding")
        })
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

    const reopen = [{
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

    const openUrlInTestEditor = () => {
        const apiInfoKey = issueDetails?.id?.apiInfoKey
        if (!apiInfoKey) return
        setSelectedSampleApi({
            apiCollectionId: apiInfoKey.apiCollectionId,
            url: apiInfoKey.url,
            method: { "_name": apiInfoKey.method }
        })
        const navUrl = window.location.origin + "/dashboard/test-editor/" + selectedTestRunResult.testCategoryId
        window.open(navUrl, "_blank")
    }

    const categoryKey =
      selectedTestRunResult?.superCategoryName ||
      selectedTestRunResult?.testCategory?.match(/\(([^)]+)\)/)?.[1] ||
      selectedTestRunResult?.testCategory;
    const owaspData = func.categoryMapping[categoryKey] || {};
    const owaspMapping = owaspData.label || "";
    const owaspUrl = owaspData.url || "";
    const owaspAgenticData = func.agenticCategoryMapping[categoryKey] || {};
    const owaspAgenticMapping = owaspAgenticData.label || "";
    const owaspAgenticUrl = owaspAgenticData.url || "";

    function ActionsComp() {
        const issuesActions = issueDetails?.testRunIssueStatus === "IGNORED" ? [...issues, ...reopen] : issues
        return (
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
                                return (
                                    <div style={{ cursor: 'pointer' }} onClick={() => { issue.onAction(); setPopoverActive(false) }} key={index}>
                                        {issue.content}
                                    </div>
                                )
                            })}
                        </VerticalStack>
                    </Popover.Section>
                </Popover.Pane>
            </Popover>
        )
    }

    function TitleComponent() {
        const severity = (selectedTestRunResult && selectedTestRunResult.vulnerable) ? issueDetails.severity : ""
        return (
            <div style={{ display: 'flex', justifyContent: "space-between", alignItems: "flex-start", gap: "24px", padding: "16px", paddingTop: '0px' }}>
                <VerticalStack gap={"2"}>
                    <Box width="100%">
                        <div style={{ display: 'flex', gap: '4px', marginBottom: '4px' }} className='test-title'>
                            <VerticalStack gap={1}>
                                <HorizontalStack gap={1}>
                                    <Button removeUnderline plain monochrome onClick={() => openTest()}>
                                        <Text variant="headingSm" alignment="start" breakWord>{selectedTestRunResult?.name}</Text>
                                    </Button>
                                    {(severity && severity?.length > 0) ? (
                                        issueDetails?.testRunIssueStatus === 'IGNORED' ?
                                            <Badge size='small'>Ignored</Badge> :
                                            <Popover
                                                active={severityPopoverActive}
                                                activator={
                                                    <Button
                                                        plain
                                                        removeUnderline
                                                        onClick={() => setSeverityPopoverActive(!severityPopoverActive)}
                                                        onMouseEnter={() => setIsHoveringSeverity(true)}
                                                        onMouseLeave={() => setIsHoveringSeverity(false)}
                                                    >
                                                        <HorizontalStack gap="1" align="center">
                                                            <Box className={`badge-wrapper-${severity.toUpperCase()}`}>
                                                                <Badge size="small" status={observeFunc.getColor(severity)}>{severity}</Badge>
                                                            </Box>
                                                            {isHoveringSeverity && (
                                                                <Icon source={EditMinor} tone="base" />
                                                            )}
                                                        </HorizontalStack>
                                                    </Button>
                                                }
                                                onClose={() => setSeverityPopoverActive(false)}
                                                autofocusTarget="first-node"
                                            >
                                                <ActionList
                                                    items={[
                                                        {
                                                            content: 'Critical',
                                                            onAction: () => handleSeverityUpdate('CRITICAL')
                                                        },
                                                        {
                                                            content: 'High',
                                                            onAction: () => handleSeverityUpdate('HIGH')
                                                        },
                                                        {
                                                            content: 'Medium',
                                                            onAction: () => handleSeverityUpdate('MEDIUM')
                                                        },
                                                        {
                                                            content: 'Low',
                                                            onAction: () => handleSeverityUpdate('LOW')
                                                        }
                                                    ]}
                                                />
                                            </Popover>
                                    ) : null}
                                </HorizontalStack>
                                {owaspMapping.length > 0 ? (
                                    <Link onClick={() => owaspUrl && window.open(owaspUrl, '_blank')}>
                                        <Badge size="small">OWASP Top 10 | {owaspMapping}</Badge>
                                    </Link>
                                ) : null}
                                {owaspAgenticMapping.length > 0 ? (
                                    <Link onClick={() => owaspAgenticUrl && window.open(owaspAgenticUrl, '_blank')}>
                                        <Badge size="small">{owaspAgenticMapping}</Badge>
                                    </Link>
                                ) : null}
                            </VerticalStack>
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
                                    <Button plain removeUnderline textAlign="left" onClick={() => setIsEditingDescription(true)}>
                                        Add description
                                    </Button>
                                ) : (
                                    <Button plain removeUnderline textAlign="left" onClick={() => setIsEditingDescription(true)}>
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
                        <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
                        <Text color="subdued" variant="bodySm">{selectedTestRunResult?.testCategory}</Text>
                    </HorizontalStack>

                    <ApiGroups collectionIds={apiInfo?.collectionIds} />
                    {/* tools call format: {mcp/agent name} -> value: {tools for that mcp/agent} */}
                    <VerticalStack gap={1}>
                        {Object.keys(toolsCalls).map(agentName => (
                            <HorizontalStack gap={1} key={agentName}>
                                <Badge status='info'>{agentName}</Badge>
                                <Text variant="bodySm">{toolsCalls[agentName].join(', ')}</Text>
                            </HorizontalStack>
                        ))}
                    </VerticalStack>
                </VerticalStack>
                <HorizontalStack gap={2} wrap={false}>
                    {issueDetails?.id?.apiInfoKey && (
                        <Tooltip content="Open URL in test editor" dismissOnMouseOut>
                            <Button monochrome onClick={openUrlInTestEditor} icon={FileMinor} />
                        </Tooltip>
                    )}
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
                                setIssueType={() => { }}
                                projId={serviceNowTable}
                                issueType=""
                                issueId={issueDetails.id}
                                isServiceNowModal={true}
                            />
                            <JiraTicketCreationModal
                                activator={window.DEVREV_INTEGRATED === 'true' ? <Button id={"create-devrev-ticket-button"} primary onClick={handleDevRevClick} disabled={devrevWorkUrl !== "" || window.DEVREV_INTEGRATED !== "true"}>Create DevRev Ticket</Button> : <></>}
                                modalActive={devrevModalActive}
                                setModalActive={setDevRevModalActive}
                                handleSaveAction={handleDevRevTicketCreation}
                                jiraProjectMaps={devrevParts}
                                setProjId={setDevRevPartId}
                                setIssueType={setDevRevWorkItemType}
                                projId={devrevPartId}
                                issueType={devrevWorkItemType}
                                issueId={issueDetails.id}
                                isDevRevModal={true}
                            />
                           
                            { window?.WIZ_INTEGRATED === 'true' ? 
                                <Button id={"create-wiz-finding-button"} primary onClick={handleWizFindingCreation} disabled={ wizFindingUrl?.length > 0 }>
                                    Create Wiz Finding
                                </Button> 
                                : <></>
                            }
                        </HorizontalStack>
                    }
                </HorizontalStack>
            </div>
        )
    }

    const dataExpiredComponent = <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
        <Text>
            Sample data might not be available for non-vulnerable tests more than 2 months ago.
            <br />
            Please contact <Link url="mailto:support@akto.io">support@akto.io</Link> for more information.
        </Text>
    </Box>

    const dataStoreTime = 2 * 30 * 24 * 60 * 60;
    const dataExpired = func.timeNow() - (selectedTestRunResult?.endTimestamp || func.timeNow()) > dataStoreTime

    // Track which result id we've already analyzed so we analyze exactly once per
    // result and never re-trigger the LLM call on re-renders / tab remounts.
    const analyzedIdRef = useRef(null);
    const [vulnerabilityHighlights, setVulnerabilityHighlights] = useState({});

    // Build deterministic evidence anchors when the model returns nothing but the
    // test is already confirmed vulnerable. This guarantees a highlight + tooltip
    // without depending on the LLM (common for authorization/access-control issues,
    // where a single response looks normal in isolation).
    const parseHeaders = (h) => {
        if (!h) return {};
        if (typeof h === 'object') return h;
        if (typeof h === 'string') {
            try { return JSON.parse(h); } catch (e) { return {}; }
        }
        return {};
    };

    // Parse a request/response message that may be a JSON string or already an object.
    const parseMessageObj = (msg) => {
        if (!msg) return null;
        if (typeof msg === 'object') return msg;
        if (typeof msg === 'string') {
            try { return JSON.parse(msg); } catch (e) { return null; }
        }
        return null;
    };

    // Cap how many changed fields we surface so the prompt/UI stay small.
    const MAX_DIFF_ENTRIES = 15;

    // A value is "masked" when the UI redacts it (e.g. auth headers shown as ******).
    // We must not emit such a value as a phrase (it won't exist verbatim in the
    // rendered editor); the segment is bound by its field key instead.
    const isMaskedValue = (v) => typeof v === 'string' && /^\*{3,}$/.test(v.trim());

    // Transport / proxy / CDN / infra / credential / probe headers are NEVER
    // meaningful attack evidence - they ride along on every request. Highlighting
    // them floods the evidence panel with noise (host, x-forwarded-*, fastly-*,
    // the auth JWT, the akto "a" probe, etc.), so we never treat them as injected.
    const NOISE_FIELD_PREFIXES = ['x-forwarded-', 'x-akto-', 'x-sigsci-', 'fastly-', 'sec-fetch-', 'sec-ch-', 'x-amzn-', 'cf-'];
    const NOISE_FIELD_EXACT = new Set([
        'host', 'content-length', 'content-type', 'connection', 'accept', 'accept-encoding',
        'accept-language', 'accept-charset', 'user-agent', 'referer', 'origin', 'cache-control',
        'pragma', 'te', 'trailer', 'transfer-encoding', 'upgrade', 'keep-alive', 'via', 'forwarded',
        'x-real-ip', 'x-request-id', 'x-correlation-id', 'cdn-loop', 'x-varnish', 'x-timer',
        'x-cache', 'x-cache-hits', 'x-served-by', 'dnt',
        'authorization', 'cookie', 'set-cookie', 'proxy-authorization', 'x-api-key', 'api-key', 'apikey', 'a'
    ]);
    const isNoiseField = (key) => {
        const k = String(key || '').trim().toLowerCase();
        if (!k) return true;
        if (NOISE_FIELD_EXACT.has(k)) return true;
        return NOISE_FIELD_PREFIXES.some((p) => k.startsWith(p));
    };

    // Compute what the test changed between the baseline (original) request and the
    // attack request. These changed/added values are the concrete evidence of the
    // injected attack (e.g. invalid header values, swapped auth token, payload).
    //
    // IMPORTANT: a header/body field is only "injected" if there is a real baseline
    // to compare against. When the baseline has no headers (the diff is degenerate),
    // we must NOT mark the entire request as injected - otherwise every transport
    // header shows up as fake evidence.
    const computeRequestDiff = (originalMessage, attackMessage) => {
        const orig = parseMessageObj(originalMessage);
        const atk = parseMessageObj(attackMessage);
        if (!atk || !atk.request) return [];

        const entries = [];
        const origHeaders = parseHeaders(orig?.request?.headers);
        const atkHeaders = parseHeaders(atk?.request?.headers);
        const hasBaselineHeaders = Object.keys(origHeaders).length > 0;

        Object.keys(atkHeaders).forEach((key) => {
            if (entries.length >= MAX_DIFF_ENTRIES) return;
            if (isNoiseField(key)) return;
            const modified = atkHeaders[key];
            const original = origHeaders[key];
            const modStr = modified === undefined || modified === null ? '' : String(modified);
            const hasOriginal = original !== undefined;
            if (hasOriginal) {
                const origStr = String(original);
                if (origStr !== modStr) {
                    entries.push({ field: key, original: origStr, modified: modStr, location: 'REQUEST' });
                }
            } else if (hasBaselineHeaders) {
                // Genuinely added vs a real baseline.
                entries.push({ field: key, original: null, modified: modStr, location: 'REQUEST' });
            }
        });

        const origBodyObj = parseMessageObj(orig?.request?.body);
        const atkBodyObj = parseMessageObj(atk?.request?.body);
        const hasBaselineBody = origBodyObj && typeof origBodyObj === 'object';
        if (atkBodyObj && typeof atkBodyObj === 'object' && !Array.isArray(atkBodyObj)) {
            Object.keys(atkBodyObj).forEach((key) => {
                if (entries.length >= MAX_DIFF_ENTRIES) return;
                const modified = atkBodyObj[key];
                const original = origBodyObj ? origBodyObj[key] : undefined;
                const modStr = typeof modified === 'object' ? JSON.stringify(modified) : String(modified);
                const hasOriginal = original !== undefined;
                if (hasOriginal) {
                    const origStr = typeof original === 'object' ? JSON.stringify(original) : String(original);
                    if (origStr !== modStr) {
                        entries.push({ field: key, original: origStr, modified: modStr, location: 'REQUEST' });
                    }
                } else if (hasBaselineBody) {
                    entries.push({ field: key, original: null, modified: modStr, location: 'REQUEST' });
                }
            });
        } else {
            const origBody = orig?.request?.body;
            const atkBody = atk?.request?.body;
            if (typeof atkBody === 'string' && atkBody.length > 0 && typeof origBody === 'string' && origBody.length > 0 && String(origBody) !== atkBody && entries.length < MAX_DIFF_ENTRIES) {
                entries.push({
                    field: 'body',
                    original: String(origBody).slice(0, 120),
                    modified: atkBody.slice(0, 120),
                    location: 'REQUEST'
                });
            }
        }

        return entries.slice(0, MAX_DIFF_ENTRIES);
    };

    // For multi-exec tests, summarise the sibling attempts (everything except the
    // last/vulnerable one) so the model can explain cross-attempt proof (rate-limit,
    // 2-account BOLA) without us sending every full request/response.
    const buildSiblingAttempts = (testResults, lastIdx) => {
        if (!Array.isArray(testResults) || testResults.length <= 1) return [];
        const summary = [];
        testResults.forEach((res, i) => {
            if (i === lastIdx) return;
            if (summary.length >= 10) return;
            const msg = parseMessageObj(res?.message);
            const statusCode = msg?.response?.statusCode || null;
            const diff = computeRequestDiff(res?.originalMessage, res?.message);
            const keyChange = diff.length > 0 ? `${diff[0].field}=${diff[0].modified}` : null;
            summary.push({ attempt: i + 1, statusCode, keyChange });
        });
        return summary;
    };

    // Trim a test template (YAML) to keep the prompt small. The validate/success
    // conditions and injected wordlists are what explain WHY the test is vulnerable.
    const TEMPLATE_MAX_CHARS = 1500;
    const trimTemplate = (content) => {
        if (typeof content !== 'string' || content.length === 0) return '';
        return content.length > TEMPLATE_MAX_CHARS
            ? content.slice(0, TEMPLATE_MAX_CHARS) + "\n...[truncated]"
            : content;
    };

    // Cache trimmed templates per testSubType so we never refetch within a run.
    // On any failure (e.g. no TEST_EDITOR access) we degrade gracefully to "".
    const templateCacheRef = useRef({});
    const getTrimmedTemplate = async (testSubType) => {
        if (!testSubType) return '';
        if (templateCacheRef.current[testSubType] !== undefined) {
            return templateCacheRef.current[testSubType];
        }
        let trimmed = '';
        try {
            const resp = await testEditorApi.fetchTestContent(testSubType);
            const content = typeof resp === 'string' ? resp : (resp?.content || '');
            trimmed = trimTemplate(content);
        } catch (e) {
            trimmed = '';
        }
        templateCacheRef.current[testSubType] = trimmed;
        return trimmed;
    };

    // Deterministic, specific evidence when the model returns nothing. We never
    // emit a hardcoded generic sentence: we prefer (1) the concrete values the test
    // changed, then (2) the framework's own validationReason, then (3) the response
    // proof (a short value or the status code).
    const buildFallbackSegments = (messageObj, requestDiff, validationReason) => {
        const segments = [];

        // (1) Request-side: the actual injected/changed values.
        (requestDiff || []).forEach((d) => {
            if (segments.length >= MAX_DIFF_ENTRIES) return;
            const masked = isMaskedValue(d.modified);
            const seg = {
                field: d.field,
                location: 'REQUEST',
                reason: (d.original !== null && d.original !== undefined)
                    ? `"${d.field}" was changed from "${d.original}" to "${d.modified}" and the endpoint still accepted it.`
                    : `"${d.field}" was injected with "${d.modified}" and the endpoint still accepted it.`
            };
            // Bind by field when the value is masked/empty; otherwise highlight the value.
            if (!masked && typeof d.modified === 'string' && d.modified.length > 0) {
                seg.phrase = d.modified.slice(0, 80);
            }
            segments.push(seg);
        });

        // (2)/(3) Response-side proof, preferring the framework's own reason.
        const responseReason = (typeof validationReason === 'string' && validationReason.trim().length > 0)
            ? validationReason.trim()
            : null;
        const body = messageObj?.response?.body;
        const cleaned = typeof body === 'string'
            ? body.trim().replace(/^[\s"'\[{]+|[\s"'\]},]+$/g, '')
            : '';
        if (cleaned.length > 0 && cleaned.length <= 60) {
            segments.push({
                location: 'RESPONSE',
                phrase: cleaned,
                reason: responseReason || `The response returned this value, satisfying the test's success condition.`
            });
        } else if (messageObj?.response?.statusCode) {
            segments.push({
                location: 'RESPONSE',
                field: 'statusCode',
                phrase: String(messageObj.response.statusCode),
                reason: responseReason || `The request returned status ${messageObj.response.statusCode}, satisfying the test's success condition.`
            });
        }

        return segments;
    };

    // Run vulnerability analysis at the flyout level so it re-triggers whenever a
    // different result is selected on the same page. Keeping it here (instead of
    // inside the memoized ValuesTabContent) avoids relying on the inner component
    // remounting, which it does not always do when only the selected result changes
    // while staying on the same tab.
    useEffect(() => {
            // Check if vulnerability highlighting is enabled (use existing GPT feature flag)
            //const isVulnerabilityHighlightingEnabled = window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"] && 
            //    window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"]?.isGranted === true;
            const isVulnerabilityHighlightingEnabled = true;
            const currentId = selectedTestRunResult?.id;

            if (currentId && analyzedIdRef.current !== currentId && selectedTestRunResult?.vulnerable && selectedTestRunResult?.testResults && isVulnerabilityHighlightingEnabled) {
                analyzedIdRef.current = currentId;
                // Drop the previously viewed result's highlights so we never show
                // stale/mismatched evidence while the new analysis runs.
                setVulnerabilityHighlights({});
                setVulnerabilityAnalysisError(null);

                const timeoutId = setTimeout(() => {
                    // Multi-exec tests only mark the last attempt as vulnerable; analyze that one only.
                    const lastIdx = selectedTestRunResult.testResults.length - 1;
                    const analysisPromises = [lastIdx].map(async (idx) => {
                        const result = selectedTestRunResult.testResults[idx];
                        // Parse the message if it's a string
                        let messageObj = result.message;
                        if (typeof messageObj === 'string') {
                            try {
                                messageObj = JSON.parse(messageObj);
                            } catch (e) {
                                return null;
                            }
                        }

                        if (messageObj && messageObj.response && messageObj.response.body) {
                            try {
                                // What the test actually changed vs the baseline request - the
                                // concrete injected evidence (invalid headers, swapped token, payload).
                                const requestDiff = computeRequestDiff(result.originalMessage, result.message);
                                // The test's own validate outcome (why it was flagged vulnerable).
                                const resultValidationReason = result.validationReason || '';
                                // The test template (validate conditions + wordlists) explains the "why".
                                const testSubType = selectedTestRunResult?.testCategoryId;
                                const testTemplate = await getTrimmedTemplate(testSubType);
                                // Cross-attempt context for multi-exec tests.
                                const siblingAttempts = buildSiblingAttempts(selectedTestRunResult.testResults, idx);

                                // Minimal payload structure for optimal LLM analysis
                                const testResultData = {
                                    resultId: selectedTestRunResult?.id ? `${selectedTestRunResult.id}_${idx}` : null,
                                    testContext: {
                                        category: selectedTestRunResult?.testCategoryId || selectedTestRunResult?.testCategory || 'UNKNOWN',
                                        description: issueDetails?.description || selectedTestRunResult?.name || '',
                                        severity: issueDetails?.severity || 'HIGH',
                                        cwe: issueDetails?.cwe || [],
                                        // Why the framework considered this vulnerable, and the test
                                        // definition (validate conditions + injected wordlists).
                                        validationReason: resultValidationReason,
                                        testTemplate: testTemplate
                                    },
                                    request: {
                                        method: messageObj.request?.method || 'GET',
                                        url: messageObj.request?.url || '',
                                        contentType: messageObj.request?.headers?.['content-type'] || 'application/json',
                                        // Send the FULL request headers + body so evidence that lives
                                        // in the request (e.g. a swapped auth token / cookie for broken
                                        // authorization) is available to the model, not just response.
                                        headers: messageObj.request?.headers || {},
                                        body: messageObj.request?.body || '',
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
                                    },
                                    // Explicit hints: exactly what the test changed, and the other attempts.
                                    modifiedRequestValues: requestDiff,
                                    siblingAttempts: siblingAttempts
                                };

                                const response = await testingApi.analyzeVulnerability(
                                    JSON.stringify(testResultData),
                                    'redteaming'
                                );

                                const analysisData = response.analysisResult || response;

                                const baseMeta = {
                                    vulnerabilityType: selectedTestRunResult?.testCategoryId || selectedTestRunResult?.testCategory || 'UNKNOWN',
                                    severity: issueDetails?.severity || 'HIGH'
                                };

                                let segmentsToUse = [];
                                if (analysisData?.vulnerableSegments?.length > 0) {
                                    segmentsToUse = analysisData.vulnerableSegments.map(segment => ({
                                        ...segment,
                                        ...baseMeta
                                    }));
                                } else {
                                    // Safety net: the test engine already flagged this result as
                                    // vulnerable, but the model returned no segment. Build specific
                                    // evidence deterministically from the diff + validationReason +
                                    // response proof (never a hardcoded generic sentence).
                                    segmentsToUse = buildFallbackSegments(messageObj, requestDiff, resultValidationReason).map(seg => ({ ...seg, ...baseMeta }));
                                }

                                if (segmentsToUse.length > 0) {
                                    setVulnerabilityHighlights(prev => ({
                                        ...prev,
                                        [idx]: segmentsToUse
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
        }, [selectedTestRunResult?.id]);

    // Component that renders the request/response editors for the selected result.
    const ValuesTabContent = React.memo(({ isAgentic = false, runAutomatedTests = false } = {}) => {
        if (dataExpired && !selectedTestRunResult?.vulnerable) {
            return dataExpiredComponent;
        }

        if (!selectedTestRunResult?.testResults) {
            return null;
        }

        const firstError = selectedTestRunResult?.testResults?.find(result =>
          result.errors && result.errors.length > 0
        )?.errors?.join(", ");

        return (
            <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}>
                <VerticalStack gap="3">
                    <AiExecutionJourney runAutomatedTests={runAutomatedTests} aiSummaryTraces={selectedTestRunResult?.aiSummaryTraces} />
                    <Box padding="3" background="bg-surface-secondary" borderRadius="2">
                        <LegendLabel />
                    </Box>
                    <VerticalStack gap="0">
                        <SampleDataList
                            key="Sample values"
                            heading={"Attempt"}
                            minHeight={"30vh"}
                            vertical={true}
                            sampleData={
                                selectedTestRunResult?.testResults.map((result, idx) => {
                                    const validationReason = result.validationReason || "";
                                    if (result.errors && result.errors.length > 0) {
                                        let errorList = result.errors.join(", ");
                                        return { errorList: errorList, validationReason }
                                    }
                                    // Add vulnerability highlights only for response
                                    let vulnerabilitySegments = vulnerabilityHighlights[idx] || [];
                                    if (result.originalMessage || result.message) {
                                        if(isAgentic){
                                            return {
                                                originalMessage: result.message,
                                                message: result.message,
                                                validationReason,
                                            }
                                        }
                                        return {
                                            originalMessage: result.originalMessage,
                                            message: result.message,
                                            vulnerabilitySegments,
                                            validationReason,
                                        }
                                    }
                                    return { errorList: "No data found", validationReason }
                                })}
                            isNewDiff={true}
                            vulnerable={selectedTestRunResult?.vulnerable}
                            vulnerabilityAnalysisError={vulnerabilityAnalysisError}
                        />
                        {firstError && isSkippedTestError(firstError) && (
                            <Box paddingBlockStart="2" paddingInlineStart="2">
                                <Link url={SKIPPED_TEST_DOCS_URL} external>Learn more about skipped tests</Link>
                            </Box>
                        )}
                    </VerticalStack>
                </VerticalStack>
            </Box>
        );
    });

    const hasConversations = conversations?.length > 0

    const conversationTab = useMemo(() => {
        if (typeof selectedTestRunResult !== "object") return null;

        // TODO: Replace with real AI analysis from backend
        const analysis = "Your AI Agent exposed its system instructions after a follow up request framed as internal debugging. The disclosure occurred while interacting with getAutomationTestCommandLogs, indicating a multi part prompt injection vulnerability.";

        // TODO: Implement real message sending handler
        // Replace with actual API call when chat endpoint is available
        const handleSendMessage = (msg) => {
            // Future: Call testingApi.sendChatMessage(issueDetails.id, msg)
        };

        return {
            id: 'evidence',
            content: "Evidence",
            component: <TestRunResultChat
                analysis={analysis}
                conversations={conversations}
                onSendMessage={handleSendMessage}
                isStreaming={false}
                testResults={selectedTestRunResult?.testResults || []}
                runAutomatedTests={runAutomatedTests}
                selectedTestRunResult={selectedTestRunResult}
            />
        }
    }, [selectedTestRunResult, conversations, runAutomatedTests])

    const attemptTabForConversations = useMemo(() => {
        if (!hasConversations) return null;
        const hasTestResultsMessages = selectedTestRunResult?.testResults?.some(result => result.message);
        if(!hasTestResultsMessages) return null;
        return {
            id: 'attempt',
            content: "Attempt",
            component: <ValuesTabContent isAgentic runAutomatedTests={runAutomatedTests} />
        };
    }, [hasConversations, selectedTestRunResult])

    const ValuesTab = useMemo(() => {
        if (typeof selectedTestRunResult !== "object") return null;
        return {
            id: 'values',
            content: "Evidence",
            component: <ValuesTabContent runAutomatedTests={runAutomatedTests} />
        }
    }, [selectedTestRunResult, dataExpired, issueDetails, refreshFlag, runAutomatedTests])

    const resultTabs = hasConversations
        ? [conversationTab, attemptTabForConversations].filter(Boolean)
        : [ValuesTab]

    const finalResultTab = hasConversations ? conversationTab : ValuesTab

    function RowComp({ cardObj }) {
        const { title, value, tooltipContent } = cardObj
        return (
            value ? <Box width="224px">
                <VerticalStack gap={"2"}>
                    <TitleWithInfo
                        textProps={{ variant: "bodyMd", fontWeight: "semibold" }}
                        titleText={title}
                        tooltipContent={tooltipContent}
                    />
                    {value}
                </VerticalStack>
            </Box> : null
        )
    }

    const testResultDetailsComp = (
        <GridRows columns={3} items={rowItems} CardComponent={RowComp} />
    )

    function MoreInformationComp({ infoState }) {
        infoState = infoState.filter((item) => item.title !== 'Jira')

        return (
            <VerticalStack gap={"2"}>
                {
                    infoState.map((item, index) => {
                        const { title, content, tooltipContent, icon } = item

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
                        textProps={{ variant: "bodyMd", fontWeight: "semibold", color: "subdued" }}
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
        component: (selectedTestRunResult.errors && selectedTestRunResult.errors.length > 0) && <Box padding={"4"}>
            {
                selectedTestRunResult?.errors?.map((error, i) => {
                    if (error) {
                        let data = {
                            original: error
                        }
                        return (
                            <SampleData key={i} data={data} language="yaml" minHeight="450px" wordWrap={false} />
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
            tabs={issueDetails?.id ? [overviewTab, timelineTab, ...resultTabs, remediationTab].filter(Boolean) : (hasConversations ? [conversationTab, attemptTabForConversations].filter(Boolean) : [attemptTab])}
            currTab={() => { }}
        />
    )

    const isAktoUser = window.USER_NAME && window.USER_NAME.endsWith('@akto.io')

    const askAktoComp = isAktoUser ? (
        <AskAktoSection
            aiSummary={aiSummary}
            aiSummaryLoading={aiSummaryLoading}
            aiMessages={aiMessages}
            aiLoading={aiLoading}
            onGenerateAiOverview={onGenerateAiOverview}
            onSendFollowUp={onSendFollowUp}
        />
    ) : null

    const currentComponents = showForbidden
        ? [<Box padding="4"><ForbiddenRole /></Box>]
        : [<TitleComponent />, askAktoComp, tabsComponent].filter(Boolean)

    const title = isIssuePage ? "Issue details" : mapLabel('Test result', getDashboardCategory())

    return (
        <>
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
            <CompulsoryDescriptionModal
                open={compulsoryDescriptionModal}
                onClose={() => setCompulsoryDescriptionModal(false)}
                onConfirm={handleIgnoreWithDescription}
                reasonLabel={pendingIgnoreAction?.ignoreReason}
                description={mandatoryDescription}
                onChangeDescription={setMandatoryDescription}
                loading={modalLoading}
            />
        </>
    )
}

export default TestRunResultFlyout