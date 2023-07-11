import React, { useState, useRef, useEffect } from 'react'
import {
  SearchMinor,
  FraudProtectMinor,
  LinkMinor,
  ProductsMinor,
  FlagMajor,
  MarketingMajor,
  ClipboardMinor,
  MobileBackArrowMajor
} from '@shopify/polaris-icons';
import {
  Text,
  Button,
  VerticalStack,
  ButtonGroup,
  HorizontalStack, Icon, Box, Badge, LegacyCard, Link, List, Card, HorizontalGrid,
  Pagination, Key
} from '@shopify/polaris';
import TestingStore from '../testingStore';
import api from '../api';
import transform from '../transform';
import { useParams } from 'react-router-dom';
import func from "@/util/func"
import parse from 'html-react-parser';
import { editor } from "monaco-editor/esm/vs/editor/editor.api"
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/comment/browser/comment';
import 'monaco-editor/esm/vs/editor/contrib/codelens/browser/codelensController';
// import 'monaco-editor/esm/vs/editor/contrib/colorPicker/browser/color';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import 'monaco-editor/esm/vs/editor/contrib/lineSelection/browser/lineSelection';
import 'monaco-editor/esm/vs/editor/contrib/indentation/browser/indentation';
// import 'monaco-editor/esm/vs/editor/contrib/inlineCompletions/browser/inlineCompletionsController';
import 'monaco-editor/esm/vs/editor/contrib/snippet/browser/snippetController2'
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/esm/vs/editor/contrib/wordHighlighter/browser/wordHighlighter';
import "monaco-editor/esm/vs/language/json/monaco.contribution"
import "monaco-editor/esm/vs/language/json/json.worker"
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from '../PageWithMultipleCards';

let headers = [
  {
    name: {
      text: "Test name",
      value: "name",
      item_order: 0,

    }
  },
  {
    severityList: {
      text: 'Severity',
      value: 'severity',
      item_order: 1,
    }
  },
  {
    icon: {
      text: "",
      value: "",
      row_order: 0,
    },
    details: [
      {
        text: "Detected time",
        value: "detected_time",
        item_order: 2,
        icon: SearchMinor,
      },
      {
        text: 'Test category',
        value: 'testCategory',
        item_order: 2,
        icon: FraudProtectMinor
      },
      {
        text: 'url',
        value: 'url',
        item_order: 2,
        icon: LinkMinor
      },
    ]
  }
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

function formatJSON(val = {}) {
  try {
    const res = JSON.parse(val);
    Object.keys(res).forEach((key) => {
      res[key] = formatJSON(res[key])
    })
    return res;
  } catch {
    return val;
  }
}

function AttemptComponent(props) {

  const requestRef = useRef("");
  const responseRef = useRef("");
  const [refText, setRefText] = useState([])
  const [page, setPage] = useState(0);

  function createEditor(ref, options) {
    let text = null
    text = editor.create(ref, options)
    text.setValue("");
    setRefText((old) => [...old, text]);
  }
  useEffect(()=>{
    if(refText.length==0){
      [requestRef, responseRef].map((ref) => {
        createEditor(ref.current, {
          language: "json",
          minimap: { enabled: false },
          wordWrap: true,
          automaticLayout: true,
          colorDecorations: true,
          scrollBeyondLastLine: false,
          readOnly: true,
        })
      })
    }
    if (props.results?.[page]?.originalMessage && refText.length==2) {
      let message = formatJSON(props.results?.[page]?.originalMessage);
      let res = {}, req = {}
      Object.keys(message).forEach((key) => {
        if (key.startsWith("req") || key.startsWith("query")) {
          req[key] = message[key]
        } else if (key.startsWith("res")) {
          res[key] = message[key]
        }
      })
        refText[0].setValue(JSON.stringify(req, null, 2))
        refText[1].setValue(JSON.stringify(res, null, 2))
    }
  }, [props.results, page, refText])

  return (
    <VerticalStack gap="4">
      <HorizontalStack align='space-between'>
      <Text variant='headingLg'>
        Attempt
      </Text>
      <Pagination
              label={
                props.results?.length==0 ? 'No test runs found' :
                `${page+1} of ${props.results?.length}`
              }
              hasPrevious = {page < 0}
              previousKeys={[Key.LeftArrow]}
              onPrevious={() => {setPage((old) => (old-1))}}
              hasNext = {props.results?.length > (page+1)}
              nextKeys={[Key.RightArrow]}
              onNext={() => {setPage((old) => (old+1))}}
            />
      </HorizontalStack>
      <HorizontalGrid columns="2" gap="2">
        {
          [requestRef, responseRef].map((ref, index) => {
            return (
              <Box key={index}>
                <LegacyCard>
                  <LegacyCard.Section flush>
                    <Box padding={"2"}>
                      <HorizontalStack padding="2" align='space-between'>
                        {index == 0 ? "Request" : "Response"}
                        <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                          <Icon source={ClipboardMinor} />
                        </div>
                      </HorizontalStack>
                    </Box>
                  </LegacyCard.Section>
                  <LegacyCard.Section flush>
                    <Box padding={"2"} ref={ref} minHeight="300px">
                    </Box>
                  </LegacyCard.Section>
                </LegacyCard>
              </Box>
            )
          })
        }
      </HorizontalGrid>
    </VerticalStack>
  )
}

function TestRunResultPage(props) {

  const selectedTestRun = TestingStore(state => state.selectedTestRun);
  const setSelectedTestRun = TestingStore(state => state.setSelectedTestRun);
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
        testRunResult = transform.prepareTestRunResult(testingRunResult, subCategoryMap, subCategoryFromSourceConfigMap)
        setSelectedTestRunResult(testRunResult)
      })
      
      await api.fetchIssueFromTestRunResultDetails(hexId2).then((resp) => {
        if (resp.runIssues) {
          setIssueDetails(...[resp.runIssues]);
          moreInfoSections[0].content = (
            <Text color='subdued'>
              {subCategoryMap[resp.runIssues.id?.testSubCategory].issueImpact}
            </Text>
          )
          moreInfoSections[1].content = (
            <HorizontalStack gap="2">
              {
                subCategoryMap[resp.runIssues.id.testSubCategory].issueTags.map((tag, index) => {
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
                subCategoryMap[resp.runIssues.id?.testSubCategory].references.map((reference) => {
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
                  <Badge key={item.confidence} status={func.getStatus(item)}>
                    <Text fontWeight="regular">
                    {item.count ? item.count : ""} {func.toSentenceCase(item.confidence)}
                    </Text></Badge>
                )
            }
          </HorizontalStack>
          <HorizontalStack gap='2' align="start" >
            {
              headers[2]?.details &&
              headers[2]?.details.map((detail) => {
                return (
                  <HorizontalStack key={detail.value} gap="1">
                    <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                      <Icon source={detail.icon} color="subdued" />
                    </div>
                    <Text as="div" variant="bodySm" color="subdued" fontWeight='regular'>
                      {selectedTestRunResult[detail.value]}
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
      <LegacyCard title="Description" sectioned>
        {parse(subCategoryMap[issueDetails.id?.testSubCategory]?.issueDetails || "")}
      </LegacyCard>
    ,
    selectedTestRunResult.testResults &&
    <AttemptComponent
      results={selectedTestRunResult?.testResults}
      vulnerable={selectedTestRunResult?.vulnerable}
    />,
      issueDetails.id &&
      <MoreInformationComponent
        sections={infoState}
      />
    ]}
    />
  )
}

export default TestRunResultPage