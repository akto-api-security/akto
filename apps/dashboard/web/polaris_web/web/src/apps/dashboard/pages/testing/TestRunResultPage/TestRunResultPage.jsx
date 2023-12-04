import React, { useState, useEffect } from 'react'
import {
  CircleTickMinor,
  ArchiveMinor,
  LinkMinor,
  ResourcesMajor,
  CollectionsMajor,
  FlagMajor,
  CreditCardSecureMajor,
  MarketingMajor,
  FraudProtectMajor} from '@shopify/polaris-icons';
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack, Icon, LegacyCard
  } from '@shopify/polaris';
import TestingStore from '../testingStore';
import api from '../api';
import transform from '../transform';
import { useLocation, useParams } from 'react-router-dom';
import func from "@/util/func"
import parse from 'html-react-parser';
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import SampleDataList from '../../../components/shared/SampleDataList';
import GithubCell from '../../../components/tables/cells/GithubCell';
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import PersistStore from '../../../../main/PersistStore';
import Store from '../../../store';

const headerDetails = [
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
    icon: CircleTickMinor,
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

function MoreInformationComponent(props) {
  return (
    <VerticalStack gap={"4"}>
      <Text variant='headingMd'>
        More information
      </Text>
      <LegacyCard>
        <LegacyCard.Section>
          {
            props?.sections?.map((section) => {
              return (<LegacyCard.Subsection key={section.title}>
                <VerticalStack gap="3">
                  <HorizontalStack gap="2" align="start" blockAlign='start'>
                    <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                      {section?.icon && <Icon source={section.icon}></Icon>}
                    </div>
                    <Text variant='headingSm'>
                      {section?.title || "Heading"}
                    </Text>
                  </HorizontalStack>
                  {section.content}
                </VerticalStack>
              </LegacyCard.Subsection>)
            })
          }
        </LegacyCard.Section>
      </LegacyCard>
    </VerticalStack>
  )
}

function TestRunResultPage(props) {

  let {testingRunResult, runIssues, testSubCategoryMap} = props;

  const selectedTestRunResult = TestingStore(state => state.selectedTestRunResult);
  console.log("selected run res")
  console.log(selectedTestRunResult)
  const setSelectedTestRunResult = TestingStore(state => state.setSelectedTestRunResult);
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const [issueDetails, setIssueDetails] = useState({});
  const [jiraIssueUrl, setJiraIssueUrl] = useState({});
  const subCategoryMap = PersistStore(state => state.subCategoryMap);
  const params = useParams()
  const hexId = params.hexId;
  const hexId2 = params.hexId2;
  const [infoState, setInfoState] = useState([])
  const [fullDescription, setFullDescription] = useState(false);
  const [loading, setLoading] = useState(true);

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
    }
    setData(testingRunResult, runIssues);
  setTimeout(() => {
    setLoading(false);
  }, 500)
}
  async function createJiraTicket(issueDetails){

    console.log(issueDetails)
    if (Object.keys(issueDetails).length == 0) {
      return
    }
    let url = issueDetails.id.apiInfoKey.url
    let issueId = issueDetails.id
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

    console.log("jira ticket " + jiraTicketKey)

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
      console.log("runIssues", runIssues)
      const moreInfoSections = transform.getInfoSectionsHeaders()
      console.log("jiraIssueCopy" + jiraIssueCopy)
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
  }, [subCategoryMap, subCategoryFromSourceConfigMap, props])

  const testErrorComponent = (
    <LegacyCard title="Errors" sectioned key="test-errors">
      {
        selectedTestRunResult?.errors?.map((error, i) => {
          return (
            <Text key={i}>{error}</Text>
          )
        })
      }
    </LegacyCard>
  )

  const components = loading ? [<SpinnerCentered key="loading" />] : [
      issueDetails.id &&
      <LegacyCard title="Description" sectioned key="description">
        {
          getDescriptionText(fullDescription) 
        }
        <Button plain onClick={() => setFullDescription(!fullDescription)}> {fullDescription ? "Less" : "More"} information</Button>
      </LegacyCard>
    ,
    ( selectedTestRunResult.errors && selectedTestRunResult.errors.length > 0 ) ? testErrorComponent : <></>,
    selectedTestRunResult.testResults &&
    <SampleDataList
      key={"sampleData"}
      sampleData={selectedTestRunResult?.testResults.map((result) => {
        return {originalMessage: result.originalMessage, message:result.message, highlightPaths:[]}
      })}
      isNewDiff={true}
      vulnerable={selectedTestRunResult?.vulnerable}
      heading={"Attempt"}
      isVulnerable={selectedTestRunResult.vulnerable}
    />,
      issueDetails.id &&
      <MoreInformationComponent
        key="info"
        sections={infoState}
      />
  ]

  return (
    <PageWithMultipleCards
    title = {
      <GithubCell
      key="heading"
      width="65vw"
      nameWidth="50vw"
      data={selectedTestRunResult}
      headers={headerDetails}
      getStatus={func.getTestResultStatus}
      />
    }
    divider= {true}
    backUrl = {props?.source == "editor" ? undefined : (hexId=="issues" ? "/dashboard/issues" : `/dashboard/testing/${hexId}`)}
    isFirstPage = {props?.source == "editor"}
    primaryAction = {<Button primary onClick={()=>createJiraTicket(issueDetails)} disabled={jiraIssueUrl != "" || window.JIRA_INTEGRATED != "true"} >Create Jira Ticket</Button>}
    // secondaryActions = {props.source == "editor" ? "" : <Button disclosure>Dismiss alert</Button>}
    components = {components}
    />
  )
}

export default TestRunResultPage