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
  HorizontalStack, Icon, Box, Badge, LegacyCard, Link, List
  } from '@shopify/polaris';
import TestingStore from '../testingStore';
import api from '../api';
import transform from '../transform';
import { useParams, useNavigate } from 'react-router-dom';
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

  const selectedTestRunResult = TestingStore(state => state.selectedTestRunResult);
  const setSelectedTestRunResult = TestingStore(state => state.setSelectedTestRunResult);
  const subCategoryFromSourceConfigMap = TestingStore(state => state.subCategoryFromSourceConfigMap);
  const [issueDetails, setIssueDetails] = useState({});
  const subCategoryMap = TestingStore(state => state.subCategoryMap);
  const params = useParams()
  const hexId = params.hexId;
  const hexId2 = params.hexId2;
  const [infoState, setInfoState] = useState(moreInfoSections)
  useEffect(() => {
    let testRunResult = selectedTestRunResult;
    async function fetchData() {
      if (Object.keys(subCategoryMap) != 0 && Object.keys(subCategoryFromSourceConfigMap) != 0 ) {
      await api.fetchTestRunResultDetails(hexId2).then(({ testingRunResult }) => {
        testRunResult = transform.prepareTestRunResult(hexId, testingRunResult, subCategoryMap, subCategoryFromSourceConfigMap)
        setSelectedTestRunResult(testRunResult)
      })
      
      await api.fetchIssueFromTestRunResultDetails(hexId2).then((resp) => {
        if (resp.runIssues) {
          setIssueDetails(...[resp.runIssues]);
          moreInfoSections[0].content = (
            <Text color='subdued'>
              {subCategoryMap[resp.runIssues.id?.testSubCategory]?.issueImpact || "No impact found"}
            </Text>
          )
          moreInfoSections[1].content = (
            <HorizontalStack gap="2">
              {
                subCategoryMap[resp.runIssues.id.testSubCategory]?.issueTags.map((tag, index) => {
                  return (
                    <Badge progress="complete" key={index}>{tag}</Badge>
                  )
                })
              }
            </HorizontalStack>
          )
          moreInfoSections[3].content = (
            <List type='bullet' spacing="extraTight">
              {
                subCategoryMap[resp.runIssues.id?.testSubCategory]?.references.map((reference) => {
                  return (
                    <List.Item key={reference}>
                      <Link key={reference} url={reference} monochrome removeUnderline>
                        <Text color='subdued'>
                          {reference}
                        </Text>
                      </Link>
                    </List.Item>
                  )
                })
              }
            </List>
          )
          api.fetchAffectedEndpoints(resp.runIssues.id).then((resp1) => {
            let similarlyAffectedIssues = resp1['similarlyAffectedIssues'];
            moreInfoSections[2].content = (
              <List type='bullet'>
                {
                  similarlyAffectedIssues.map((item, index) => {
                    return (
                      <List.Item key={index}>
                        <Text color='subdued'>
                          {item.id.apiInfoKey.method} {item.id.apiInfoKey.url}
                        </Text>
                      </List.Item>)
                  })
                }
              </List>
            )
            setInfoState([...moreInfoSections]);
          })
        } else {
          setIssueDetails(...[{}]);
        }
      })
    }
    }
    fetchData();
  }, [subCategoryMap, subCategoryFromSourceConfigMap])

  const navigate = useNavigate();
  function navigateBack() {
    navigate("/dashboard/testing/" + hexId)
  }

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
                  <Badge key={item} status={func.getStatus(item)}>
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
    backAction = {{onAction:navigateBack}}
    primaryAction = {<Button primary>Create issue</Button>}
    secondaryActions = {<Button disclosure>Dismiss alert</Button>}
    components = {[
      issueDetails.id &&
      <LegacyCard title="Description" sectioned key="description">
        {parse(subCategoryMap[issueDetails.id?.testSubCategory]?.issueDetails || "No details found")}
      </LegacyCard>
    ,
    selectedTestRunResult.testResults &&
    <SampleDataList
      key="attempt"
      sampleData={selectedTestRunResult?.testResults.filter((result) => {
        return result.message
      }).map((result) => {
        return {message:result.message, highlightPathMap:{}}
      })}
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