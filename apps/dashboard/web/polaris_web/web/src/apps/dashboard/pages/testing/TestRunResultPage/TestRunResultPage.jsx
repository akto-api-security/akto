import React, { useState, useEffect } from 'react'
import {CircleTickMajor,ArchiveMinor,LinkMinor} from '@shopify/polaris-icons';
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

let headerDetails = [
  {
    text: "",
    value: "icon",
    itemOrder:0,
  },
  {
    text: "Name",
    value: "name",
    itemOrder:1,
    dataProps: {variant:"headingLg"}
  },
  {
    text: "Severity",
    value: "severity",
    itemOrder:2,
    dataProps: {fontWeight:"regular"}
  },
  {
    text: "Detected time",
    value: "detected_time",
    itemOrder:3,
    dataProps:{fontWeight:'regular'},
    icon: CircleTickMajor,
  },
  {
    text: 'Test category',
    value: 'testCategory',
    itemOrder:3,
    dataProps:{fontWeight:'regular'},
    icon: ArchiveMinor
  },
  {
    text: 'url',
    value: 'url',
    itemOrder:3,
    dataProps:{fontWeight:'regular'},
    icon: LinkMinor
  },
]

function TestRunResultPage(props) {

  let {testingRunResult, runIssues, testSubCategoryMap} = props;

  const location = useLocation()
  const selectedTestRunResult = TestingStore(state => state.selectedTestRunResult);
  const setSelectedTestRunResult = TestingStore(state => state.setSelectedTestRunResult);
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const [issueDetails, setIssueDetails] = useState({});
  const [jiraIssueUrl, setJiraIssueUrl] = useState({});
  const [azureBoardsWorkItemUrl, setAzureBoardsWorkItemUrl] = useState({});
  const [serviceNowTicketUrl, setServiceNowTicketUrl] = useState({});
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
  const [showForbidden, setShowForbidden] = useState(false)

  const useFlyout = location.pathname.includes("test-editor") ? false : true

  const setToastConfig = Store(state => state.setToastConfig)
  const setToast = (isActive, isError, message) => {
      setToastConfig({
        isActive: isActive,
        isError: isError,
        message: message
      })
  }
  
  function getDescriptionText(fullDescription){

    let tmp = testSubCategoryMap ? testSubCategoryMap : subCategoryMap

    let str = parse(tmp[issueDetails.id?.testSubCategory]?.issueDetails || "No details found");
    let finalStr = ""

    if(typeof(str) !== 'string'){
      str?.forEach((element) =>{
        if(typeof(element) === 'object'){
          if(element?.props?.children !== null){
            finalStr = finalStr + element.props.children
          }
        }else{
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

  async function createJiraTicketApiCall(hostStr, endPointStr, issueUrl, issueDescription, issueTitle, testingIssueId,projId, issueType, additionalIssueFields, labels) {
    
    const jiraMetaData = {
      issueTitle,hostStr,endPointStr,issueUrl,issueDescription,testingIssueId, additionalIssueFields, labels
    }
    let jiraInteg = await api.createJiraTicket(jiraMetaData, projId, issueType);
    return jiraInteg.jiraTicketKey
  }

  async function attachFileToIssue(origReq, testReq, issueId) {
    let jiraInteg = await api.attachFileToIssue(origReq, testReq, issueId);
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
      if(testingRunResult?.testResults?.length > 0){
        let conversationId = testingRunResult.testResults[0].conversationId;
        if(conversationId){
          let res = await api.fetchConversationsFromConversationId(conversationId);
          if(res && res.length > 0){
            const conversationsList = transform.prepareConversationsList(res)
            setConversations(conversationsList);
          }
        }
      }
      setShowDetails(true)
    }
    setData(testingRunResult, runIssues);
  setTimeout(() => {
    setLoading(false);
  }, 500)
}
  async function createJiraTicket(issueId, projId, issueType, labels){

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

    setToast(true,false,"Creating Jira Ticket")

    let jiraTicketKey = ""

    const additionalIssueFields = {};
    try {
      const customIssueFields = issuesFunctions.prepareCustomIssueFields(projId, issueType);
      additionalIssueFields["customIssueFields"] = customIssueFields;
    } catch (error) {
      setToast(true, true, "Please fill all required fields before creating a Jira ticket.");
      return;
    }

    await createJiraTicketApiCall("Host - "+hostName, pathname, window.location.href, description, issueTitle, issueId, projId, issueType, additionalIssueFields, labels).then(async(res)=> {
      if(res?.errorMessage) {
        setToast(true, true, res?.errorMessage)
      }
      jiraTicketKey = res
      await fetchData();
      setToast(true,false,"Jira Ticket Created, scroll down to view")
    })

    if (selectedTestRunResult == null || selectedTestRunResult.testResults == null || selectedTestRunResult.testResults.length === 0) {
      return
    }

    let sampleData = selectedTestRunResult.testResults[0]
    attachFileToIssue(sampleData.originalMessage, sampleData.message, jiraTicketKey)

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
      const moreInfoSections = transform.getInfoSectionsHeaders()
      setJiraIssueUrl(jiraIssueCopy)
      setAzureBoardsWorkItemUrl(azureBoardsWorkItemUrlCopy)
      setServiceNowTicketUrl(serviceNowTicketUrlCopy)
      setInfoState(transform.fillMoreInformation(subCategoryMap[runIssues?.id?.testSubCategory],moreInfoSections, runIssuesArr, jiraIssueCopy, onClickButton))
      setRemediation(subCategoryMap[runIssues?.id?.testSubCategory]?.remediation)
      // setJiraIssueUrl(jiraIssueUrl)
      // setInfoState(transform.fillMoreInformation(subCategoryMap[runIssues?.id?.testSubCategory],moreInfoSections, runIssuesArr))
    } else {
      setIssueDetails(...[{}]);
    }
  }

  useEffect(() => {
    fetchData();
  }, [subCategoryMap, subCategoryFromSourceConfigMap, props, hexId2])

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
      jiraIssueUrl={jiraIssueUrl}
      hexId={hexId}
      source={props?.source}
      setShowDetails={setShowDetails}
      showDetails={showDetails}
      isIssuePage={location.pathname.includes("issues")}
      azureBoardsWorkItemUrl={azureBoardsWorkItemUrl}
      serviceNowTicketUrl={serviceNowTicketUrl}
      conversations={conversations}
      showForbidden={showForbidden}
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
      createJiraTicket={createJiraTicket} 
      jiraIssueUrl={jiraIssueUrl} 
      hexId={hexId} 
      source={props?.source}
      setShowDetails={setShowDetails}
      showDetails={showDetails}
    />
  )
}

export default TestRunResultPage