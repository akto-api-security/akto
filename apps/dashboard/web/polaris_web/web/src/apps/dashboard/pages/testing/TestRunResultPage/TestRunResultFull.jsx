import { Box, Button, Collapsible, Divider, HorizontalStack, Icon, LegacyCard, Scrollable, Text, VerticalStack } from '@shopify/polaris'
import React, { useState, useEffect } from 'react'
import SpinnerCentered from '../../../components/progress/SpinnerCentered'
import { ChevronDownMinor, ChevronUpMinor } from '@shopify/polaris-icons';
import SampleDataList from '../../../components/shared/SampleDataList';
import { tokens } from "@shopify/polaris-tokens"
import GithubCell from '../../../components/tables/cells/GithubCell';
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards';
import func from "@/util/func"
import ForbiddenRole from '../../../components/shared/ForbiddenRole';
import TestRunResultChat from './TestRunResultChat';
import MarkdownViewer from '../../../components/shared/MarkdownViewer';

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

function TestRunResultFull(props) {

    const {
        selectedTestRunResult, testingRunResult, loading, issueDetails, getDescriptionText, infoState, headerDetails,
        hexId, source,
        remediationSrc, conversations, conversationRemediationText, showForbidden    } = props

    const [fullDescription, setFullDescription] = useState(false)
    const [remediationText, setRemediationText] = useState("")

    useEffect(() => {
        if (remediationSrc) {
            setRemediationText(remediationSrc)
        } else if (conversationRemediationText) {
            setRemediationText(conversationRemediationText)
        } else {
            setRemediationText("")
        }
    }, [remediationSrc, conversationRemediationText])

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

      const [testLogsCollapsibleOpen, setTestLogsCollapsibleOpen] = useState(false)
      const iconSource = testLogsCollapsibleOpen ? ChevronUpMinor : ChevronDownMinor
      const testLogsComponent = (
        <LegacyCard key="testLogsComponent">
          <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued"></Text>}>
            <HorizontalStack align="space-between">
              <Text fontWeight="semibold" variant="bodyMd">Test Logs</Text>
              <Button plain monochrome icon={iconSource} onClick={() => setTestLogsCollapsibleOpen(!testLogsCollapsibleOpen)} />
            </HorizontalStack>
              <Collapsible open={testLogsCollapsibleOpen} transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}>
                <LegacyCard.Subsection>
                  <Box paddingBlockStart={3}><Divider /></Box>
    
                <Scrollable style={{maxHeight: '40vh'}}>
                  <VerticalStack gap={1}>
                      
                        {testingRunResult && testingRunResult["testLogs"] && testingRunResult["testLogs"].map((x) => <div style={{fontFamily:tokens.font["font-family-mono"], fontWeight: tokens.font["font-weight-medium"],fontSize: '12px', letterSpacing: "0px", textAlign: "left"}}>
                          {"[" + x["timestamp"] + "] [" + x["testLogType"] + "] " +x["message"]}
                          </div>)}
                  </VerticalStack>
                </Scrollable>
                </LegacyCard.Subsection>
              </Collapsible>
          </LegacyCard.Section>
        </LegacyCard>
      )

    const errorsPresent = selectedTestRunResult?.testResults?.some((result) => result.errors && result.errors.length > 0)

    const hasConversations = conversations?.length > 0

    const evidenceCard = hasConversations && (
        <LegacyCard title="Evidence" sectioned key="evidence">
            <TestRunResultChat
                analysis={null}
                conversations={conversations}
                onSendMessage={() => {}}
                isStreaming={false}
            />
        </LegacyCard>
    )

    const attemptCard = !hasConversations && (func.showTestSampleData(selectedTestRunResult) && selectedTestRunResult.testResults) &&
        <SampleDataList
          key={"sampleData"}
          sampleData={selectedTestRunResult?.testResults.map((result) => {
            if (result.errors && result.errors.length > 0) {
              let errorList = result.errors.join(", ");
              return { errorList: errorList }
            }
            if (result.originalMessage || result.message) {
              return { originalMessage: result.originalMessage, message: result.message, highlightPaths: [] }
            }
            return { errorList: "No data found" }
          })}
          isNewDiff={true}
          vertical={errorsPresent}
          vulnerable={selectedTestRunResult?.vulnerable}
          heading={"Attempt"}
          isVulnerable={selectedTestRunResult.vulnerable}
        />

    const attemptCardForConversations = hasConversations && (func.showTestSampleData(selectedTestRunResult) && selectedTestRunResult.testResults) &&
        <SampleDataList
          key={"sampleDataAgentic"}
          sampleData={selectedTestRunResult?.testResults.map((result) => {
            if (result.errors && result.errors.length > 0) {
              let errorList = result.errors.join(", ");
              return { errorList: errorList }
            }
            if (result.message) {
              return { originalMessage: result.message, message: result.message, highlightPaths: [] }
            }
            return { errorList: "No data found" }
          })}
          isNewDiff={true}
          vertical={false}
          vulnerable={false}
          heading={"Attempt"}
          isVulnerable={false}
        />

    const remediationCard = remediationText && selectedTestRunResult?.vulnerable && (
        <LegacyCard title="Remediation" sectioned key="remediation">
            <MarkdownViewer markdown={remediationText} />
        </LegacyCard>
    )

    const mainComponents = [
          issueDetails.id &&
          <LegacyCard title="Description" sectioned key="description">
            {getDescriptionText(fullDescription)}
            <Button plain onClick={() => setFullDescription(!fullDescription)}> {fullDescription ? "Less" : "More"} information</Button>
          </LegacyCard>
        ,
        (testingRunResult && testingRunResult["testLogs"] && testingRunResult["testLogs"].length > 0) ? testLogsComponent : null,
        !hasConversations && (!func.showTestSampleData(selectedTestRunResult)) && testErrorComponent,
        attemptCard,
        evidenceCard,
        attemptCardForConversations,
        remediationCard,
        issueDetails.id &&
          <MoreInformationComponent
            key="info"
            sections={infoState}
          />
      ].filter(Boolean)

    const components = loading
      ? [<SpinnerCentered key="loading" />]
      : showForbidden
        ? [<Box key="forbidden" padding="4"><ForbiddenRole /></Box>]
        : mainComponents 

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
                divWrap={true}
                />
            }
            divider= {true}
            backUrl = {source === "editor" ? undefined : (hexId ==="issues" ? "/dashboard/issues" : `/dashboard/testing/${hexId}`)}
            isFirstPage = {source === "editor"}
            components = {components}
        />
    )
}

export default TestRunResultFull