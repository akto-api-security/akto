import React, { useEffect, useState } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GithubCell from '../../../components/tables/cells/GithubCell'
import func from '@/util/func'
import transform from '../transform'
import SampleDataList from '../../../components/shared/SampleDataList'
import LayoutWithTabs from '../../../components/layouts/LayoutWithTabs'

function TestRunResultFlyout(props) {


    const { selectedTestRunResult, loading, issueDetails ,getDescriptionText, infoState, headerDetails, createJiraTicket, jiraIssueUrl, hexId, source, showDetails, setShowDetails} = props

    const [testRunResult, setTestRunResult] = useState(selectedTestRunResult)

    headerDetails[1]['dataProps'] = {variant: 'headingSm'}
    // modify testing run result and headers

    useEffect(()=> {
        setTestRunResult(transform.getTestingRunResult(selectedTestRunResult))
    },[selectedTestRunResult])

    const titleComponent = (
        <GithubCell
            key="heading"
            width="40vw"
            data={testRunResult}
            headers={headerDetails}
            getStatus={func.getTestResultStatus}
        />
    )

    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: selectedTestRunResult && selectedTestRunResult.testResults && <SampleDataList
            key="Sample values"
            heading={"Attempt"}
            minHeight={"25vh"}
            vertical={true}
            sampleData={selectedTestRunResult?.testResults.map((result) => {
                return {originalMessage: result.originalMessage, message:result.message, highlightPaths:[]}
            })}
            isNewDiff={true}
            vulnerable={selectedTestRunResult?.vulnerable}
            isVulnerable={selectedTestRunResult.vulnerable}
        />
    }

    const tabsComponent = (
        <LayoutWithTabs
            key="tab-comp"
            tabs={[ValuesTab]}
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
        />
    )
}

export default TestRunResultFlyout