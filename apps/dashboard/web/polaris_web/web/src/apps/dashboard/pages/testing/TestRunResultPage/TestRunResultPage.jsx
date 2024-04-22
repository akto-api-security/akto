import React, { useState, useEffect } from 'react'
import {CircleTickMajor,ArchiveMinor,LinkMinor} from '@shopify/polaris-icons';
import TestingStore from '../testingStore';
import api from '../api';
import transform from '../transform';
import { useLocation, useParams } from 'react-router-dom';
import parse from 'html-react-parser';
import PersistStore from '../../../../main/PersistStore';
import Store from '../../../store';
import TestRunResultFull from './TestRunResultFull';
import TestRunResultFlyout from './TestRunResultFlyout';
import SingleTestRunPage from '../SingleTestRunPage/SingleTestRunPage';
import IssuesPage from '../../issues/IssuesPage/IssuesPage';

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
  const subCategoryMap = PersistStore(state => state.subCategoryMap);
  const params = useParams()
  const hexId = params.hexId;
  const hexId2 = params.hexId2;
  const [infoState, setInfoState] = useState([])
  const [loading, setLoading] = useState(true);
  const [showDetails, setShowDetails] = useState(true)

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

  async function createJiraTicketApiCall(hostStr, endPointStr, issueUrl, issueDescription, issueTitle, testingIssueId) {
    let jiraInteg = await api.createJiraTicket(hostStr, endPointStr, issueUrl, issueDescription, issueTitle, testingIssueId);
    return jiraInteg.jiraTicketKey
  }

  async function attachFileToIssue(origReq, testReq, issueId) {
    let jiraInteg = await api.attachFileToIssue(origReq, testReq, issueId);
  }

  async function fetchData() {
    if (hexId2 != undefined) {
      if (testingRunResult == undefined) {
        let res = await api.fetchTestRunResultDetails(hexId2)
        testingRunResult = res.testingRunResult;
      }
      if (runIssues == undefined) {
        let res = await api.fetchIssueFromTestRunResultDetails(hexId2)
        runIssues = res.runIssues;
      }
      setShowDetails(true)
    }
    setData(testingRunResult, runIssues);
  setTimeout(() => {
    setLoading(false);
  }, 500)
}
  async function createJiraTicket(issueDetails){

    if (Object.keys(issueDetails).length == 0) {
      return
    }
    let url = issueDetails.id.apiInfoKey.url
    let pathname = "Endpoint - " + new URL(url).pathname;
    let host =  "Host - " + new URL(url).host
    // break into host and path
    let description = "Description - " + getDescriptionText(true)
    
    let tmp = testSubCategoryMap ? testSubCategoryMap : subCategoryMap
    let issueTitle = tmp[issueDetails.id?.testSubCategory]?.testName

    setToast(true,false,"Creating Jira Ticket")

    let jiraTicketKey = ""
    await createJiraTicketApiCall(host, pathname, window.location.href, description, issueTitle, issueDetails.id).then(async(res)=> {
      jiraTicketKey = res
      await fetchData();
      setToast(true,false,"Jira Ticket Created, scroll down to view")
    })

    if (selectedTestRunResult == null || selectedTestRunResult.testResults == null || selectedTestRunResult.testResults.length == 0) {
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
      const moreInfoSections = transform.getInfoSectionsHeaders()
      setJiraIssueUrl(jiraIssueCopy)
      setInfoState(transform.fillMoreInformation(subCategoryMap[runIssues?.id?.testSubCategory],moreInfoSections, runIssuesArr, jiraIssueCopy, onClickButton))
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
    {location.pathname.includes("issues") ? <IssuesPage /> :<SingleTestRunPage />}
    <TestRunResultFlyout
      selectedTestRunResult={selectedTestRunResult} 
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