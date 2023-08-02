import React, { useState, useEffect } from 'react'
import {
  SearchMinor,
  FraudProtectMinor,
  LinkMinor,
  ProductsMinor,
  FlagMajor,
  MarketingMajor} from '@shopify/polaris-icons';
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack, Icon, Box, Badge, LegacyCard
  } from '@shopify/polaris';
import TestingStore from '../testingStore';
import api from '../api';
import transform from '../transform';
import { useParams } from 'react-router-dom';
import func from "@/util/func"
import parse from 'html-react-parser';
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import SampleDataList from '../../../components/shared/SampleDataList';

let headerDetails = [
  {
    text: "Detected time",
    value: "detected_time",
    icon: SearchMinor,
  },
  {
    text: 'Test category',
    value: 'testCategory',
    icon: FraudProtectMinor
  },
  {
    text: 'url',
    value: 'url',
    icon: LinkMinor
  },
]

let moreInfoSections = [
  {
    icon: FlagMajor,
    title: "Impact",
    content: ""
  },
  {
    icon: ProductsMinor,
    title: "Tags",
    content: ""
  },
  {
    icon: MarketingMajor,
    title: "API endpoints affected",
    content: ""
  },
  {
    icon: LinkMinor,
    title: "References",
    content: ""
  }
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
            props.sections.map((section) => {
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

  let {testingRunResult, runIssues} = props;

  const selectedTestRunResult = TestingStore(state => state.selectedTestRunResult);
  const setSelectedTestRunResult = TestingStore(state => state.setSelectedTestRunResult);
  const subCategoryFromSourceConfigMap = TestingStore(state => state.subCategoryFromSourceConfigMap);
  const [issueDetails, setIssueDetails] = useState({});
  const subCategoryMap = TestingStore(state => state.subCategoryMap);
  const params = useParams()
  const hexId = params.hexId;
  const hexId2 = params.hexId2;
  const [infoState, setInfoState] = useState(moreInfoSections)
  const [fullDescription, setFullDescription] = useState(false);
  
  function getDescriptionText(fullDescription){
    let str = parse(subCategoryMap[issueDetails.id?.testSubCategory]?.issueDetails || "No details found");
    return fullDescription ? str : str[0] + " "
  }

  async function setData(testingRunResult, runIssues) {
    if (testingRunResult) {
      let testRunResult = transform.prepareTestRunResult(hexId, testingRunResult, subCategoryMap, subCategoryFromSourceConfigMap)
      setSelectedTestRunResult(testRunResult)
    } else {
      setSelectedTestRunResult({})
    }
    if (runIssues) {
      setIssueDetails(...[runIssues]);
      setInfoState(await transform.fillMoreInformation(runIssues, subCategoryMap, moreInfoSections))
    } else {
      setIssueDetails(...[{}]);
    }
  }

  useEffect(() => {
    async function fetchData() {
      if (Object.keys(subCategoryMap) != 0 && Object.keys(subCategoryFromSourceConfigMap) != 0) {
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
      } else {
        transform.setTestMetadata();
      }
    }
    fetchData();
  }, [subCategoryMap, subCategoryFromSourceConfigMap, props])

  return (
    <PageWithMultipleCards
    title = {
        <VerticalStack gap="3">
          <HorizontalStack gap="2" align="start" blockAlign='start'>
            {selectedTestRunResult?.icon &&
              <Box> {<Icon color="primary" source={selectedTestRunResult.icon}></Icon>}
              </Box>
            }
            <Text variant='headingLg'>
              {
                selectedTestRunResult?.name || "Test run name"
              }
            </Text>
            {
              selectedTestRunResult?.severity &&
              selectedTestRunResult.severity
                .map((item) =>
                  <Badge key={item} status={func.getTestResultStatus(item)}>
                    <Text fontWeight="regular">
                    {item}
                    </Text></Badge>
                )
            }
          </HorizontalStack>
          <HorizontalStack gap='2' align="start" >
            {
              headerDetails?.map((header) => {
                return (
                  <HorizontalStack key={header.value} gap="1">
                    <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                      <Icon source={header.icon} color="subdued" />
                    </div>
                    <Text as="div" variant="bodySm" color="subdued" fontWeight='regular'>
                      {selectedTestRunResult[header.value]}
                    </Text>
                  </HorizontalStack>
                )
              })
            }
          </HorizontalStack>
        </VerticalStack>
    }
    isFirstPage = {props.source == "editor" ? true : false}
    primaryAction = {props.source == "editor" ? "" : <Button primary>Create issue</Button>}
    secondaryActions = {props.source == "editor" ? "" : <Button disclosure>Dismiss alert</Button>}
    components = {[
      issueDetails.id &&
      <LegacyCard title="Description" sectioned key="description">
        {
          getDescriptionText(fullDescription) 
        }
        <Button plain onClick={() => setFullDescription(!fullDescription)}> {fullDescription ? "Less" : "More"} information</Button>
      </LegacyCard>
    ,
    selectedTestRunResult.testResults &&
    <SampleDataList
      key={"sampleData"}
      sampleData={selectedTestRunResult?.testResults.map((result) => {
        return {originalMessage: result.originalMessage, message:result.message, highlightPaths:[]}
      })}
      showDiff={true}
      vulnerable={selectedTestRunResult?.vulnerable}
      heading={"Attempt"}
    />,
      issueDetails.id &&
      <MoreInformationComponent
        key="info"
        sections={infoState}
      />
    ]}
    />
  )
}

export default TestRunResultPage