import React, { useEffect, useState } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GithubCell from '../../../components/tables/cells/GithubCell'
import func from '@/util/func'
import transform from '../transform'
import SampleDataList from '../../../components/shared/SampleDataList'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs'
import { Avatar, Box, Button, Divider, HorizontalStack, Icon, Popover, Text, VerticalStack } from '@shopify/polaris'
import api from '../../observe/api'
import GridRows from '../../../components/shared/GridRows'

function TestRunResultFlyout(props) {


    const { selectedTestRunResult, loading, issueDetails ,getDescriptionText, infoState, headerDetails, createJiraTicket, jiraIssueUrl, hexId, source, showDetails, setShowDetails} = props

    const [testRunResult, setTestRunResult] = useState(selectedTestRunResult)
    const [fullDescription, setFullDescription] = useState(false)
    const [rowItems, setRowItems] = useState([])
    const [popoverActive, setPopoverActive] = useState(false)

    headerDetails[1]['dataProps'] = {variant: 'headingSm'}
    // modify testing run result and headers
    const infoStateFlyout = infoState && infoState.length > 0 ? infoState.filter((item) => item.title !== 'Jira') : []
    const fetchApiInfo = async(apiInfoKey) => {
        let apiInfo = {}
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
        setRowItems(transform.getRowInfo(issueDetails.severity,apiInfo,jiraIssueUrl,sensitiveParam))
    }

    useEffect(()=> {
        setTestRunResult(transform.getTestingRunResult(selectedTestRunResult))
    },[selectedTestRunResult])

    useEffect(() => {
       if(issueDetails && Object.keys(issueDetails).length > 0){
            fetchApiInfo(issueDetails.id.apiInfoKey)
       }
    },[issueDetails])

    function ignoreAction(ignoreReason){
        api.bulkUpdateIssueStatus([issueDetails.id], "IGNORED", ignoreReason ).then((res) => {
            func.setToast(true, false, `Issue ignored`)
        })
    }
    
    let issues = [{
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

    const actionsComp = (
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
                        <Button plain monochrome removeUnderline>
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
                        {issues.map((issue, index) => {
                            return(
                                <Button plain removeUnderline monochrome onClick={() => issue.onAction()} key={index}>
                                    {issue.content}
                                </Button>
                            )
                        })}
                    </VerticalStack>
                </Popover.Section>
            </Popover.Pane>
        </Popover>
    )

    const titleComponent = (
        <div style={{display: 'flex', justifyContent: "space-between", gap:"8px"}}>
            <GithubCell
                key="heading"
                width="35vw"
                data={testRunResult}
                headers={headerDetails}
                getStatus={func.getTestResultStatus}
                moreActions={actionsComp}
            />
            {actionsComp}
        </div>
    )

    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: selectedTestRunResult && selectedTestRunResult.testResults && 
        <Box paddingBlockStart={3} ><SampleDataList
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
                        <Divider />
                    </VerticalStack>
                )
            })}
        </VerticalStack>
        : null
    )

    function RowComp ({cardObj}){
        const {title, value} = cardObj
        return(
            <VerticalStack gap={"2"}>
                <Text variant="bodyMd" fontWeight="semibold" color="subdued">{title}</Text>
                {value}
            </VerticalStack>
        )
    }

    const testResultDetailsComp = (
        <GridRows columns={3} items={rowItems} CardComponent={RowComp} />
    )

    const overviewComp = (
        <Box paddingBlockStart={"4"}>
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
        component: overviewComp
    }

    const tabsComponent = (
        <LayoutWithTabs
            key="tab-comp"
            tabs={[overviewTab,ValuesTab]}
            currTab = {() => {}}
        />
    )

    const currentComponents = [
        titleComponent, tabsComponent
    ]

    return (
        <FlyLayout
            title={"Test result"}
            show={showDetails}
            setShow={setShowDetails}
            components={currentComponents}
            loading={loading}
            showDivider={true}
        />
    )
}

export default TestRunResultFlyout