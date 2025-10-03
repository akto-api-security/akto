import React, { useEffect, useState } from 'react'
import transform from '../transform'
import func from '@/util/func'
import api from '../api'
import { IndexTable, useIndexResourceState, LegacyCard, HorizontalStack, Pagination } from '@shopify/polaris'
import { GithubRow } from '../../../components/tables/rows/GithubRow'
import observeFunc from "../../observe/transform"
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper'

function SummaryTable({testingRunResultSummaries, setSummary}) {

    const [data, setData] = useState([])

    const headers = [
        {
            title: mapLabel("Test", getDashboardCategory()) + " started",
            value: 'startTime',
        },
        {
            title: mapLabel("Results", getDashboardCategory()) + " count",
            value: 'testResultsCount'
        },
        {
            title: 'Vulnerabilities',
            value: 'prettifiedSeverities',
            isCustom: true
        },
        {
            title: `Total ${mapLabel('APIs Tested', getDashboardCategory())}`,
            value: 'totalApis',
        },
    ]

    const pageLimit = 10
    const [page, setPage] = useState(0)

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

    const resourceIDResolver = (data) => {
        return data.id;
    };

    const { selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(data, {
      resourceIDResolver,
    });
    const handleRowClick = (x) => {
        setSummary(x)
    }

    let tmp = data && data.length <= pageLimit ? data :
    data.slice(page * pageLimit, Math.min((page + 1) * pageLimit, data.length))
    let rowMarkup = tmp
        .map(
        (
            data,
            index,
        ) => (
            <GithubRow
                key={data.id}
                id={data.id}
                dataObj={data}
                index={index}
                headers={headers}
                page={page}
                newRow={true}
                headings={headers}
                selectedResources={selectedResources}
                onRowClick={handleRowClick}
            />
        ),
    );

    const onPageNext = () => {
        setPage((page) => (page + 1));
    }

    const onPagePrevious = () => {
        setPage((page) => (page - 1));
    }
    const total = data.length
    let tableClass =  "new-table removeHeaderColor condensed-row" 

    return (
        <div className={tableClass}>
            <LegacyCard>
                <LegacyCard.Section flush>
                    <IndexTable
                        headings={headers}
                        resourceName={{ singular: 'summary', plural: 'summaries' }}
                        hasZebraStriping={true}
                        itemCount={total}
                        selectedItemsCount={
                            allResourcesSelected ? 'All' : selectedResources.length
                        }
                        promotedBulkActions={promotedBulkActions(selectedResources)}
                        onSelectionChange={handleSelectionChange}
                    >
                        {rowMarkup}
                    </IndexTable>
                </LegacyCard.Section>
                <LegacyCard.Section>
                    <HorizontalStack
                        align="center">
                        <Pagination
                            label={
                                total === 0 ? 'No data found' :
                                    <div data-testid="pagination-label">
                                        {`Showing ${observeFunc.formatNumberWithCommas(page * pageLimit + Math.min(1, total))}-${observeFunc.formatNumberWithCommas(Math.min((page + 1) * pageLimit, total))} of ${observeFunc.formatNumberWithCommas(total)}`}
                                    </div>
                            }
                            hasPrevious={page > 0}
                            onPrevious={onPagePrevious}
                            hasNext={total > (page + 1) * pageLimit}
                            onNext={onPageNext}
                        />
                    </HorizontalStack>
                    </LegacyCard.Section>
            </LegacyCard>
        </div>
    )
}

export default SummaryTable