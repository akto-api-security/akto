import React, { useState, useEffect, useRef } from 'react'
import { CircleTickMajor, ArchiveMinor, LinkMinor } from '@shopify/polaris-icons';
import TestingStore from '../testingStore';
import api from '../api';
import transform from '../transform';
import { useLocation, useParams, useSearchParams } from 'react-router-dom';
import parse from 'html-react-parser';
import PersistStore from '../../../../main/PersistStore';
import Store from '../../../store';
import TestRunResultFull from './TestRunResultFull';
import TestRunResultFlyout from './TestRunResultFlyout';
import LocalStore from '../../../../main/LocalStorageStore';
import observeFunc from "../../observe/transform"
import issuesFunctions from '@/apps/dashboard/pages/issues/module';
import issuesApi from "../../issues/api";
import { sendQuery } from '../../agentic/services/agenticService';

let headerDetails = [
  {
    text: "",
    value: "icon",
    itemOrder: 0,
  },
  {
    text: "Name",
    value: "name",
    itemOrder: 1,
    dataProps: { variant: "headingLg" }
  },
  {
    text: "Severity",
    value: "severity",
    itemOrder: 2,
    dataProps: { fontWeight: "regular" }
  },
  {
    text: "Detected time",
    value: "detected_time",
    itemOrder: 3,
    dataProps: { fontWeight: 'regular' },
    icon: CircleTickMajor,
  },
  {
    text: 'Test category',
    value: 'testCategory',
    itemOrder: 3,
    dataProps: { fontWeight: 'regular' },
    icon: ArchiveMinor
  },
  {
    text: 'url',
    value: 'url',
    itemOrder: 3,
    dataProps: { fontWeight: 'regular' },
    icon: LinkMinor
  },
]

function TestRunResultPage(props) {

  let { testingRunResult, runIssues, testSubCategoryMap } = props;

  const location = useLocation()
  const selectedTestRunResult = TestingStore(state => state.selectedTestRunResult);
  const setSelectedTestRunResult = TestingStore(state => state.setSelectedTestRunResult);
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const [issueDetails, setIssueDetails] = useState({});
  const [jiraIssueUrl, setJiraIssueUrl] = useState({});
  const [azureBoardsWorkItemUrl, setAzureBoardsWorkItemUrl] = useState({});
  const [serviceNowTicketUrl, setServiceNowTicketUrl] = useState({});
  const [devrevWorkUrl, setDevRevWorkUrl] = useState({});
  const subCategoryMap = LocalStore(state => state.subCategoryMap);
  const params = useParams();
  const hexId = params.hexId;
  const [searchParams, setSearchParams] = useSearchParams();
  const hexId2 = searchParams.get("result")
  const [infoState, setInfoState] = useState([])
  const [loading, setLoading] = useState(true);
  const [showDetails, setShowDetails] = useState(true)
  const [remediation, setRemediation] = useState("")
  const hostNameMap = PersistStore(state => state.hostNameMap)

  const [conversations, setConversations] = useState([])
  const [conversationRemediationText, setConversationRemediationText] = useState(null)
  const agenticConversationsRef = useRef([])
  const [showForbidden, setShowForbidden] = useState(false)

  // store key: {mcp/agent name} -> value: {tools for that mcp/agent}
  const [toolsCalls, setToolsCalls] = useState({})

  // AI Chat state
  const [aiConversationId, setAiConversationId] = useState(null)
  const [aiMessages, setAiMessages] = useState([])
  const [aiSummary, setAiSummary] = useState(null)
  const [aiLoading, setAiLoading] = useState(false)
  const [aiSummaryLoading, setAiSummaryLoading] = useState(false)
  const [aiSummaryChecked, setAiSummaryChecked] = useState(false)

  const useFlyout = location.pathname.includes("test-editor") ? false : true

  const setToastConfig = Store(state => state.setToastConfig)
  const setToast = (isActive, isError, message) => {
    setToastConfig({
      isActive: isActive,
      isError: isError,
      message: message
    })
  }

  function getDescriptionText(fullDescription) {

    let tmp = testSubCategoryMap ? testSubCategoryMap : subCategoryMap

    let str = parse(tmp[issueDetails.id?.testSubCategory]?.issueDetails || "No details found");
    let finalStr = ""

    if (typeof (str) !== 'string') {
      str?.forEach((element) => {
        if (typeof (element) === 'object') {
          if (element?.props?.children !== null) {
            finalStr = finalStr + element.props.children
          }
        } else {
          finalStr = finalStr + element
        }
      })
      finalStr = finalStr.replace(/"/g, '');
      let firstLine = finalStr.split('.')[0]
      return fullDescription ? finalStr : firstLine + ". "
    }
    str = str.replace(/"/g, '');
    let firstLine = str.split('.')[0]
    return fullDescription ? str : firstLine + ". "

  }

  async function createJiraTicketApiCall(hostStr, endPointStr, issueUrl, issueDescription, issueTitle, testingIssueId, projId, issueType, additionalIssueFields, labels) {

    const jiraMetaData = {
      issueTitle, hostStr, endPointStr, issueUrl, issueDescription, testingIssueId, additionalIssueFields, labels
    }
    let jiraInteg = await api.createJiraTicket(jiraMetaData, projId, issueType);
    return jiraInteg.jiraTicketKey
  }

  async function attachFileToIssue(origReq, testReq, issueId, agenticResult = false) {
    await api.attachFileToIssue(origReq, testReq, issueId, agenticResult);
  }

  function formatHttpMessage(rawMessage) {
    try {
      // The message is stored with literal backslash-escaped quotes ({\"request\":...})
      // Wrap in quotes so JSON.parse treats it as a JSON string and unescapes it
      let parsed;
      try {
        parsed = JSON.parse(rawMessage);
      } catch (_) {
        parsed = JSON.parse('"' + rawMessage + '"');
      }
      // Handle double-stringified case
      if (typeof parsed === 'string') {
        parsed = JSON.parse(parsed);
      }
      const req = parsed.request || {};
      const res = parsed.response || {};

      let reqHeaders = {};
      let resHeaders = {};
      try { reqHeaders = typeof req.headers === 'string' ? JSON.parse(req.headers) : (req.headers || {}); } catch (_) {}
      try { resHeaders = typeof res.headers === 'string' ? JSON.parse(res.headers) : (res.headers || {}); } catch (_) {}

      let reqBody = req.body || '';
      let resBody = res.body || '';
      try { reqBody = JSON.stringify(JSON.parse(reqBody), null, 2); } catch (_) {}
      try { resBody = JSON.stringify(JSON.parse(resBody), null, 2); } catch (_) {}

      const formatHeaders = (headers) =>
        Object.entries(headers).map(([k, v]) => `  ${k}: ${v}`).join('\n') || '  (none)';

      return [
        '=== REQUEST ===',
        `${req.method || 'GET'} ${req.url || ''}`,
        req.queryParams ? `Query Params: ${req.queryParams}` : null,
        '\nHeaders:',
        formatHeaders(reqHeaders),
        '\nBody:',
        reqBody || '(empty)',
        '',
        '=== RESPONSE ===',
        `Status: ${res.statusCode || ''}`,
        '\nHeaders:',
        formatHeaders(resHeaders),
        '\nBody:',
        resBody || '(empty)',
      ].filter(line => line !== null).join('\n');
    } catch (_) {
      return rawMessage;
    }
  }

  /** Keep /api/chatAndStore + MCP /chat body small; oversized payloads return 422 (Struts ERROR). */
  const MAX_AI_HTTP_MSG_CHARS = 20000;
  const MAX_AI_AGENTIC_CONTEXT_CHARS = 24000;

  function truncateForAiContext(text, maxChars) {
    if (text == null || text === '') return null;
    if (text.length <= maxChars) return text;
    return `${text.slice(0, maxChars)}\n\n[... truncated for context size ...]`;
  }

  function toPlainMetadataScalar(val) {
    if (val == null || val === undefined) return '';
    if (Array.isArray(val)) return val.map(String).join(', ');
    if (typeof val === 'string' || typeof val === 'number' || typeof val === 'boolean') return String(val);
    return '';
  }

  function formatHttpMessageIfPresent(raw) {
    if (raw == null || raw === '') return null;
    return truncateForAiContext(formatHttpMessage(raw), MAX_AI_HTTP_MSG_CHARS);
  }

  /** Same structure as Jira agentic attachment; reused for AI metadata. */
  function buildAgenticConversationText(agenticConversations) {
    if (!agenticConversations?.length) return '';
    const separator = `\n\n${'='.repeat(60)}\n\n`;
    return agenticConversations.map((conv, idx) => {
      let turn = `Turn ${idx + 1}\n\nTested Interaction:\n${conv.finalSentPrompt}\n\nAI Agent:\n${conv.response}`;
      if (conv.validationMessage && conv.validationMessage !== 'null') {
        turn += `\n\nValidation Message:\n${conv.validationMessage}`;
      }
      return turn;
    }).join(separator);
  }

  function buildTestResultMetadata() {
    const testResults = selectedTestRunResult?.testResults;
    const tr0 = testResults?.[testResults.length - 1];
    const isAgentic = Boolean(tr0?.resultTypeAgentic);
    const rawAgentic = agenticConversationsRef.current;
    const agenticText = isAgentic && rawAgentic?.length
      ? truncateForAiContext(buildAgenticConversationText(rawAgentic), MAX_AI_AGENTIC_CONTEXT_CHARS)
      : null;

    const data = {
      testName: toPlainMetadataScalar(selectedTestRunResult?.name),
      testCategory: toPlainMetadataScalar(selectedTestRunResult?.testCategory),
      testCategoryId: toPlainMetadataScalar(selectedTestRunResult?.testCategoryId),
      vulnerable: Boolean(selectedTestRunResult?.vulnerable),
      severity: toPlainMetadataScalar(issueDetails?.severity),
      url: toPlainMetadataScalar(selectedTestRunResult?.url) || '',
      originalMessage: formatHttpMessageIfPresent(tr0?.originalMessage),
      attemptMessage: formatHttpMessageIfPresent(tr0?.message),
    };
    if (agenticText) {
      data.agenticConversationContext = agenticText;
    }
    return {
      type: 'test_execution_result',
      data,
    };
  }

  async function handleGenerateAiOverview() {
    if (aiSummary || aiSummaryLoading || aiSummaryChecked) return;
    if (!selectedTestRunResult?.id || !selectedTestRunResult?.testResults?.length) {
      setToast(true, true, "Test result is still loading. Wait for the page to finish loading, then try again.");
      return;
    }
    setAiSummaryLoading(true);
    setAiSummaryChecked(true);
    try {
      const metaData = buildTestResultMetadata();
      const response = await sendQuery(
        "Analyze this test result and provide a plain text summary in 1-2 sentences. No markdown, no headers, no bullet points, no formatting. Just plain sentences.",
        null,
        "TEST_EXECUTION_RESULT",
        metaData
      );
      if (response?.conversationId) {
        setAiConversationId(response.conversationId);
      }
      if (response?.response) {
        setAiSummary(response.response);
      }
    } catch (err) {
      setAiSummary("Unable to generate AI overview. Please try again later.");
    } finally {
      setAiSummaryLoading(false);
    }
  }

  async function handleSendFollowUp(query) {
    if (!query.trim() || aiLoading) return;
    const userMsg = { _id: "user_" + Date.now(), role: "user", message: query };
    setAiMessages(prev => [...prev, userMsg]);
    setAiLoading(true);
    try {
      const response = await sendQuery(query, aiConversationId, "TEST_EXECUTION_RESULT", buildTestResultMetadata());
      if (response?.conversationId && !aiConversationId) {
        setAiConversationId(response.conversationId);
      }
      if (response?.response) {
        const aiMsg = {
          _id: "system_" + Date.now(),
          role: "system",
          message: response.response,
          isComplete: true,
          isFromHistory: false
        };
        setAiMessages(prev => [...prev, aiMsg]);
      }
    } catch (err) {
    } finally {
      setAiLoading(false);
    }
  }

  async function fetchData() {
    if (hexId2 !== undefined) {
      try {
        if (testingRunResult === undefined) {
          let res = await api.fetchTestRunResultDetails(hexId2)
          testingRunResult = res.testingRunResult;
        }
      } catch (error) {
        if (error?.response?.status === 403 || error?.status === 403) {
          setShowForbidden(true);
          setLoading(false);
          return;
        }
        throw error;
      }
      try {
        if (runIssues === undefined) {
          let res = await api.fetchIssueFromTestRunResultDetails(hexId2)
          runIssues = res.runIssues;
        }
      } catch (error) {
        if (error?.response?.status === 403 || error?.status === 403) {
          setShowForbidden(true);
          setLoading(false);
          return;
        }
        throw error;
      }
      if (testingRunResult?.testResults?.length > 0) {
        let conversationId = testingRunResult.testResults[0].conversationId;
        if (conversationId) {
          let res = await api.fetchConversationsFromConversationId(conversationId);
          if (res && res.length > 0) {
            const result = transform.prepareConversationsList(res)
            setConversations(result.conversations);
            setConversationRemediationText(result.remediationText || null)
            setToolsCalls(result.toolsCalls || {})
            agenticConversationsRef.current = res;
          } else {
            agenticConversationsRef.current = [];
          }
        } else {
          agenticConversationsRef.current = [];
        }
      } else {
        agenticConversationsRef.current = [];
      }
      setShowDetails(true)
    }
    setData(testingRunResult, runIssues);
    setTimeout(() => {
      setLoading(false);
    }, 500)
  }
  async function createJiraTicket(issueId, projId, issueType, labels) {

    if (Object.keys(issueId).length === 0) {
      return
    }
    const url = issueId.apiInfoKey.url
    const hostName = hostNameMap[issueId.apiInfoKey.id] ? hostNameMap[issueId.apiInfoKey.id] : observeFunc.getHostName(url)

    let pathname = "Endpoint - ";
    try {
      if (url.startsWith("http")) {
        pathname += new URL(url).pathname;
      } else {
        pathname += url;
      }
    } catch (err) {
      pathname += url;
    }

    // break into host and path
    let description = "Description - " + getDescriptionText(true)

    let tmp = testSubCategoryMap ? testSubCategoryMap : subCategoryMap
    let issueTitle = tmp[issueId?.testSubCategory]?.testName

    setToast(true, false, "Creating Jira Ticket")

    let jiraTicketKey = ""

    const additionalIssueFields = {};
    try {
      const customIssueFields = issuesFunctions.prepareCustomIssueFields(projId, issueType);
      additionalIssueFields["customIssueFields"] = customIssueFields;
    } catch (error) {
      setToast(true, true, "Please fill all required fields before creating a Jira ticket.");
      return;
    }

    await createJiraTicketApiCall("Host - " + hostName, pathname, window.location.href, description, issueTitle, issueId, projId, issueType, additionalIssueFields, labels).then(async (res) => {
      if (res?.errorMessage) {
        setToast(true, true, res?.errorMessage)
      }
      jiraTicketKey = res
      await fetchData();
      setToast(true, false, "Jira Ticket Created, scroll down to view")
    })

    if (selectedTestRunResult == null || selectedTestRunResult.testResults == null || selectedTestRunResult.testResults.length === 0) {
      return
    }

    let sampleData = selectedTestRunResult.testResults[0]
    if (sampleData.resultTypeAgentic) {
      const agenticConversations = agenticConversationsRef.current;
      if (agenticConversations && agenticConversations.length > 0) {
        const conversationText = buildAgenticConversationText(agenticConversations);
        attachFileToIssue(conversationText, null, jiraTicketKey, true);

        // File 2: HTTP request/response from testResults message
        if (sampleData.message) {
          attachFileToIssue(formatHttpMessage(sampleData.message), null, jiraTicketKey, true);
        }
      }
    } else {
      attachFileToIssue(sampleData.originalMessage, sampleData.message, jiraTicketKey)
    }

  }

  async function createDevRevTicket(issueId, partId, workItemType) {
    setToast(true, false, "Creating DevRev Ticket")

    await issuesApi.createDevRevTickets([issueId], partId, workItemType, window.location.origin).then(async (res) => {
      await fetchData();
      if (res?.errorMessage) {
        setToast(true, false, res.errorMessage)
      } else {
        setToast(true, false, "DevRev Ticket Created, scroll down to view")
      }
    }).catch((err) => {
      setToast(true, true, err?.response?.data?.errorMessage || err?.response?.data?.actionErrors?.[0] || "Error creating DevRev ticket")
    })
  }

  async function setData(testingRunResult, runIssues) {

    let tmp = testSubCategoryMap ? testSubCategoryMap : subCategoryMap

    if (testingRunResult) {
      let testRunResult = transform.prepareTestRunResult(hexId, testingRunResult, tmp, subCategoryFromSourceConfigMap)
      setSelectedTestRunResult(testRunResult)
    } else {
      setSelectedTestRunResult({})
    }
    if (runIssues) {
      const onClickButton = () => {
        createJiraTicket(...[runIssues])
      }
      setIssueDetails(...[runIssues]);
      let runIssuesArr = []
      await api.fetchAffectedEndpoints(runIssues.id).then((resp1) => {
        runIssuesArr = resp1['similarlyAffectedIssues'];
      })
      let jiraIssueCopy = runIssues.jiraIssueUrl || "";
      let azureBoardsWorkItemUrlCopy = runIssues.azureBoardsWorkItemUrl || "";
      let serviceNowTicketUrlCopy = runIssues.servicenowIssueUrl || "";
      let devrevWorkUrlCopy = runIssues.devrevWorkUrl || "";
      const moreInfoSections = transform.getInfoSectionsHeaders()
      setJiraIssueUrl(jiraIssueCopy)
      setAzureBoardsWorkItemUrl(azureBoardsWorkItemUrlCopy)
      setServiceNowTicketUrl(serviceNowTicketUrlCopy)
      setDevRevWorkUrl(devrevWorkUrlCopy)
      setInfoState(transform.fillMoreInformation(tmp[runIssues?.id?.testSubCategory], moreInfoSections, runIssuesArr, jiraIssueCopy, onClickButton))
      setRemediation(tmp[runIssues?.id?.testSubCategory]?.remediation)
      // setJiraIssueUrl(jiraIssueUrl)
      // setInfoState(transform.fillMoreInformation(subCategoryMap[runIssues?.id?.testSubCategory],moreInfoSections, runIssuesArr))
    } else {
      setIssueDetails(...[{}]);
    }
  }

  useEffect(() => {
    // Reset AI chat state when test result changes
    setAiConversationId(null);
    setAiMessages([]);
    setAiSummary(null);
    setAiLoading(false);
    setAiSummaryLoading(false);
    setAiSummaryChecked(false);
    setSelectedTestRunResult({});
    fetchData();
  }, [subCategoryMap, subCategoryFromSourceConfigMap, props?.testingRunResult, props?.runIssues, hexId2])

  return (
    useFlyout ?
      <>
        <TestRunResultFlyout
          selectedTestRunResult={selectedTestRunResult}
          remediationSrc={remediation}
          testingRunResult={testingRunResult}
          loading={loading}
          issueDetails={issueDetails}
          getDescriptionText={getDescriptionText}
          infoState={infoState}
          createJiraTicket={createJiraTicket}
          createDevRevTicket={createDevRevTicket}
          jiraIssueUrl={jiraIssueUrl}
          hexId={hexId}
          source={props?.source}
          setShowDetails={setShowDetails}
          showDetails={showDetails}
          isIssuePage={location.pathname.includes("issues")}
          azureBoardsWorkItemUrl={azureBoardsWorkItemUrl}
          serviceNowTicketUrl={serviceNowTicketUrl}
          devrevWorkUrl={devrevWorkUrl}
          conversations={conversations}
          conversationRemediationText={conversationRemediationText}
          showForbidden={showForbidden}
          aiSummary={aiSummary}
          aiSummaryLoading={aiSummaryLoading}
          aiMessages={aiMessages}
          aiLoading={aiLoading}
          onGenerateAiOverview={handleGenerateAiOverview}
          onSendFollowUp={handleSendFollowUp}
          toolsCalls={toolsCalls}
        />
      </>
      :
      <TestRunResultFull
        selectedTestRunResult={selectedTestRunResult}
        testingRunResult={testingRunResult}
        loading={loading}
        issueDetails={issueDetails}
        getDescriptionText={getDescriptionText}
        infoState={infoState}
        headerDetails={headerDetails}
        hexId={hexId}
        source={props?.source}
        remediationSrc={remediation}
        conversations={conversations}
        conversationRemediationText={conversationRemediationText}
        showForbidden={showForbidden}
      />
  )
}

export default TestRunResultPage