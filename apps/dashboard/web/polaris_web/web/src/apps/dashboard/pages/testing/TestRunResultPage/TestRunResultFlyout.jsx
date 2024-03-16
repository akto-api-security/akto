import React, { useEffect, useState } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GithubCell from '../../../components/tables/cells/GithubCell'
import func from '@/util/func'
import observeFunc from "../../observe/transform"

function TestRunResultFlyout(props) {


    const { selectedTestRunResult, loading, issueDetails ,getDescriptionText, infoState, headerDetails, createJiraTicket, jiraIssueUrl, hexId, source, showDetails, setShowDetails} = props

    const [testRunResult, setTE]

    headerDetails[1]['dataProps'] = {variant: 'headingSm'}
    let hostNameObj = headerDetails.filter(ind => ind.value === 'url')[0]
    const pos = headerDetails.map(e => e.value).indexOf('hostName');
    if(pos === -1 && Object.keys(selectedTestRunResult).length > 0){
      hostNameObj['text'] = 'host'
      hostNameObj['value'] = 'hostName'
      headerDetails.push(hostNameObj)
      let temp = {...selectedTestRunResult}
      const urlObj = func.toMethodUrlObject(temp.url)
      temp['hostName'] = observeFunc.getHostName(urlObj.url)
      temp['url'] = urlObj.method + " " + observeFunc.getTruncatedUrl(urlObj.url)
      console.log(temp)
    }
    // modify testing run result and headers

    const titleComponent = (
        <GithubCell
            key="heading"
            width="40vw"
            data={testRunResult}
            headers={headerDetails}
            getStatus={func.getTestResultStatus}
        />
    )

    const currentComponents = [
        titleComponent
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