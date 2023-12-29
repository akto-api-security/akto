import React, { useEffect, useState } from 'react'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import transform from '../transform'
import func from '@/util/func'
import api from '../api'

function SummaryTable({testingRunResultSummaries}) {

    const [data, setData] = useState([])

    const headers = [
        {
            title: 'Total apis tested',
            value: 'totalApis',
        },
        {
            title: 'Results count',
            value: 'testResultsCount'
        },
        {
            title: 'Vulnerabilities',
            value: 'prettifiedSeverities',
            isCustom: true
        },
        {
            title: 'Test started',
            value: 'startTime',
        },
    ]

    const resourceName = {
        singular: 'summary',
        plural: 'summaries',
    };

    const promotedBulkActions = (selectedTestRuns) => { 
        return [
        {
          content: `Delete ${selectedTestRuns.length} test summar${selectedTestRuns.length > 1 ? "ies" : "y"}`,
          onAction: async() => {
            await api.deleteTestRunsFromSummaries(selectedTestRuns);
            func.setToast(true, false, `${selectedTestRuns.length} test summar${selectedTestRuns.length > 1 ? "ies" : "y"} deleted successfully`)
            window.location.reload();
          },
        },
      ]};

    useEffect(() => {
        setData(transform.prettifySummaryTable(testingRunResultSummaries))
    },[testingRunResultSummaries])

    return (
        <GithubSimpleTable
            hasZebraStriping={true}
            headings={headers}
            headers={headers}
            useSummaryRow={true}
            pageLimit={10}
            data={data}
            selectable={true}
            resourceName={resourceName}
            filters={[]}
            promotedBulkActions={promotedBulkActions}
        />
    )
}

export default SummaryTable