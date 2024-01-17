import React, { useEffect, useState } from 'react'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import transform from '../transform'
import func from '@/util/func'
import api from '../api'
import { IndexFiltersMode } from '@shopify/polaris'

function SummaryTable({testingRunResultSummaries}) {

    const [data, setData] = useState([])

    const headers = [
        {
            title: 'Test started',
            value: 'startTime',
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
            title: 'Total apis tested',
            value: 'totalApis',
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
            headers={headers}
            pageLimit={10}
            data={data}
            selectable={true}
            resourceName={resourceName}
            filters={[]}
            promotedBulkActions={promotedBulkActions}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
        />
    )
}

export default SummaryTable