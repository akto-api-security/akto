import React, { useEffect, useState } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import func from '@/util/func'
import transform from '../transform'
import SampleDataList from '../../../components/shared/SampleDataList'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs'
import { Avatar, Badge, Box, Button, Divider, HorizontalStack, Icon, Popover, Text, VerticalStack } from '@shopify/polaris'
import api from '../../observe/api'
import issuesApi from "../../issues/api"
import GridRows from '../../../components/shared/GridRows'

function TestRunResultFlyout(props) {


    const { selectedTestRunResult, loading, issueDetails ,getDescriptionText, infoState, createJiraTicket, jiraIssueUrl, showDetails, setShowDetails} = props
    const [fullDescription, setFullDescription] = useState(false)
    const [rowItems, setRowItems] = useState([])
    const [popoverActive, setPopoverActive] = useState(false)
    // modify testing run result and headers
    const infoStateFlyout = infoState && infoState.length > 0 ? infoState.filter((item) => item.title !== 'Jira') : []
    const fetchApiInfo = async(apiInfoKey) => {
        let apiInfo = {}
        if(apiInfoKey !== null){
            await api.fetchEndpoint(apiInfoKey).then((res) => {
                apiInfo = JSON.parse(JSON.stringify(res))
            })
            let sensitiveParam = ""
            await api.loadSensitiveParameters(apiInfoKey.apiCollectionId,apiInfoKey.url, apiInfo.method).then((resp) => {
                resp?.data?.endpoints.forEach((x, index) => {
                    sensitiveParam += x.subTypeString.toUpperCase()
                    if(index !== resp?.data?.endpoints.length - 1){
                        sensitiveParam += ", "
                    }
                })
            })
            setRowItems(transform.getRowInfo(issueDetails.severity,apiInfo,issueDetails.jiraIssueUrl,sensitiveParam))
        }
    }

    useEffect(() => {
       if(issueDetails && Object.keys(issueDetails).length > 0){       
            fetchApiInfo(issueDetails.id.apiInfoKey)
       }
    },[selectedTestRunResult,issueDetails])

    function ignoreAction(ignoreReason){
        issuesApi.bulkUpdateIssueStatus([issueDetails.id], "IGNORED", ignoreReason ).then((res) => {
            func.setToast(true, false, `Issue ignored`)
        })
    }

    function reopenAction(){
        issuesApi.bulkUpdateIssueStatus([issueDetails.id], "OPEN", "" ).then((res) => {
            func.setToast(true, false, "Issue re-opened")
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

    const  reopen =  [{
        content: 'Reopen',
        onAction: () => { reopenAction() }
    }]
    
    function ActionsComp (){
        const issuesActions = issueDetails?.testRunIssueStatus === "IGNORED" ? [...issues, ...reopen] : issues
        return(
            issueDetails?.id &&
        <Popover
            activator={<Button disclosure plain onClick={() => setPopoverActive(!popoverActive)}>Actions</Button>}
            active={popoverActive}
            onClose={() => setPopoverActive(false)}
            autofocusTarget="first-node"
            preferredPosition="below"
            preferredAlignment="left"
        >
            <Popover.Pane fixed>
                <Popover.Section>
                    <VerticalStack gap={"4"}>
                        <Text variant="headingSm">Create</Text>
                        <Button plain monochrome removeUnderline onClick={()=>{createJiraTicket(issueDetails)}} disabled={jiraIssueUrl !== "" || window.JIRA_INTEGRATED !== "true"}>
                            <HorizontalStack gap={"2"}>
                                <Avatar shape="square" size="extraSmall" source="/public/logo_jira.svg"/>
                                <Text>Jira Ticket</Text>
                            </HorizontalStack>
                        </Button>
                    </VerticalStack>
                </Popover.Section>
                <Popover.Section>
                    <VerticalStack gap={"4"}>
                        <Text variant="headingSm">Ignore</Text>
                        {issuesActions.map((issue, index) => {
                            return(
                                <div style={{cursor: 'pointer'}} onClick={() => issue.onAction()} key={index}>
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
        const severity = (selectedTestRunResult && selectedTestRunResult.vulnerable) ? selectedTestRunResult.severity[0] : ""
        return(
            <div style={{display: 'flex', justifyContent: "space-between", gap:"24px", padding: "16px", paddingTop: '0px'}}>
                <VerticalStack gap={"2"}>
                    <Box width="100%">
                        <div style={{display: 'flex', gap: '4px'}} className='test-title'>
                            <Text variant="headingSm" alignment="start" breakWord>{selectedTestRunResult?.name}</Text>
                            {severity.length > 0 ? <Box><Badge size="small" status={func.getTestResultStatus(severity)}>{severity}</Badge></Box> : null}
                        </div>
                    </Box>
                    <HorizontalStack gap={"2"}>
                        <Text color="subdued" variant="bodySm">{transform.getTestingRunResultUrl(selectedTestRunResult)}</Text>
                        <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px'/>
                        <Text color="subdued" variant="bodySm">{selectedTestRunResult?.testCategory}</Text>
                    </HorizontalStack>
                </VerticalStack>
                <ActionsComp />
            </div>
        )
    }

    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: (!(selectedTestRunResult.errors && selectedTestRunResult.errors.length > 0 && selectedTestRunResult.errors[0].endsWith("skipping execution"))) && selectedTestRunResult.testResults &&
        <Box paddingBlockStart={3} paddingInlineEnd={4} paddingInlineStart={4}><SampleDataList
            key="Sample values"
            heading={"Attempt"}
            minHeight={"30vh"}
            vertical={true}
            sampleData={selectedTestRunResult?.testResults.map((result) => {
                return {originalMessage: result.originalMessage, message:result.message, highlightPaths:[]}
            })}
            isNewDiff={true}
            vulnerable={selectedTestRunResult?.vulnerable}
            isVulnerable={selectedTestRunResult.vulnerable}
        />
        </Box> 
    }
    const moreInfoComponent = (
        infoStateFlyout.length > 0 ?
        <VerticalStack gap={"5"}>
            {infoStateFlyout.map((item, index) => {
                return(
                    <VerticalStack gap={"5"} key={index}>
                        <VerticalStack gap={"2"} >
                            <HorizontalStack gap="1_5-experimental">
                                <Box><Icon source={item.icon} color='subdued'/></Box>
                                <Text variant="bodyMd" fontWeight="semibold" color="subdued">{item.title}</Text>
                            </HorizontalStack>
                            {item?.content}
                        </VerticalStack>
                        {index !== infoStateFlyout.length - 1 ? <Divider /> : null}
                    </VerticalStack>
                )
            })}
        </VerticalStack>
        : null
    )

    function RowComp ({cardObj}){
        const {title, value} = cardObj
        return(
            value ? <Box width="224px">
                <VerticalStack gap={"2"}>
                    <Text variant="bodyMd" fontWeight="semibold" color="subdued">{title}</Text>
                    {value}
                </VerticalStack>
            </Box>: null
        )
    }

    const testResultDetailsComp = (
        <GridRows columns={3} items={rowItems} CardComponent={RowComp} />
    )

    const overviewComp = (
        <Box padding={"4"}>
            <VerticalStack gap={"5"}>
                <VerticalStack gap={"2"}>
                    <Text variant="bodyMd" fontWeight="semibold" color="subdued">
                        Description
                    </Text>
                    <Box as="span">
                        {
                            getDescriptionText(fullDescription) 
                        }
                        <Button plain onClick={() => setFullDescription(!fullDescription)}> {fullDescription ? "Less" : "More"} information</Button>
                    </Box>
                </VerticalStack>
                <Divider />
                {testResultDetailsComp}
                <Divider />
                {moreInfoComponent}  
            </VerticalStack>
        </Box>
    )
    const overviewTab = {
        id: "overview",
        content: 'Overview',
        component: issueDetails.id && overviewComp
    }

    const errorTab = {
        id: "error",
        content: "Attempt",
        component:  ( selectedTestRunResult.errors && selectedTestRunResult.errors.length > 0 ) && <Box padding={"4"}>
            {
            selectedTestRunResult?.errors?.map((error, i) => {
              return (
                <Text key={i}>{error}</Text>
              )
            })
          }
        </Box>
    }

    const attemptTab =  ( selectedTestRunResult.errors && selectedTestRunResult.errors.length > 0 ) ? errorTab : ValuesTab

    const tabsComponent = (
        <LayoutWithTabs
            key="tab-comp"
            tabs={issueDetails?.id ? [overviewTab,ValuesTab]: [attemptTab]}
            currTab = {() => {}}
        />
    )

    const currentComponents = [
        <TitleComponent/>, tabsComponent
    ]

    return (
        <FlyLayout
            title={"Test result"}
            show={showDetails}
            setShow={setShowDetails}
            components={currentComponents}
            loading={loading}
            showDivider={true}
            newComp={true}
        />
    )
}

export default TestRunResultFlyout