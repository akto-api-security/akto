import React, { useEffect, useRef, useState, useMemo } from 'react'
import transform from '../transform'
import func from '@/util/func'
import api from '../api'
import { IndexTable, useIndexResourceState, LegacyCard, HorizontalStack, Pagination } from '@shopify/polaris'
import { GithubRow } from '../../../components/tables/rows/GithubRow'
import observeFunc from "../../observe/transform"
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper'
import {
    getCachedRunStatusCounts,
    getValidRunStatusMap,
    setCachedRunStatusCounts,
} from '../TestRunsPage/runStatusCache'
import { shouldFetchRunStatusSummary } from '../TestRunsPage/runStatusUtils'

function SummaryTable({testingRunResultSummaries, setSummary}) {

    const [data, setData] = useState([])
    const [runStatusUpdateKey, setRunStatusUpdateKey] = useState("")
    const pendingRunStatusRequestRef = useRef(null)

    // Memoize headers to prevent recreation on every render
    const headers = useMemo(() => [
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
            title: mapLabel("Test", getDashboardCategory()) + " run status",
            value: 'run_message',
            maxWidth: '220px',
        },
        {
            title: `Total ${mapLabel('APIs Tested', getDashboardCategory())}`,
            value: 'totalApis',
        },
        {
            title: 'External API Tokens',
            value: 'totalExternalApiTokens',
        },
    ], []);

    // Ensure all headings have unique id properties to avoid duplicate key warnings
    const processedHeadings = useMemo(() => {
        return headers.map((heading, index) => ({
            ...heading,
            id: heading.id || heading.value || heading.title || `heading-${index}`        }));
    }, [headers]);

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
        setData(transform.prepareSummaryTableRows(testingRunResultSummaries, getValidRunStatusMap()))
    }, [testingRunResultSummaries, runStatusUpdateKey])

    useEffect(() => {
        const summaryHexIds = [...new Set(
            (testingRunResultSummaries || [])
                .filter(shouldFetchRunStatusSummary)
                .map((summary) => summary.hexId)
                .filter((hexId) => hexId && getCachedRunStatusCounts(hexId) === undefined)
        )];

        if (summaryHexIds.length === 0) {
            return;
        }

        const requestKey = summaryHexIds.slice().sort().join(',');
        if (pendingRunStatusRequestRef.current === requestKey) {
            return;
        }

        pendingRunStatusRequestRef.current = requestKey;
        const chunkSize = 10;
        const chunks = [];
        for (let i = 0; i < summaryHexIds.length; i += chunkSize) {
            chunks.push(summaryHexIds.slice(i, i + chunkSize));
        }

        Promise.all(chunks.map((chunk) =>
            api.fetchTestRunStatusSummaries(chunk)
                .then((statusSummaries) => {
                    if (pendingRunStatusRequestRef.current !== requestKey) {
                        return;
                    }
                    setCachedRunStatusCounts(statusSummaries);
                    setRunStatusUpdateKey(Date.now().toString());
                })
                .catch(() => {})
        )).finally(() => {
            if (pendingRunStatusRequestRef.current === requestKey) {
                pendingRunStatusRequestRef.current = null;
            }
        });
    }, [testingRunResultSummaries])

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
                key={data.id || `row-${index}`}
                id={data.id}
                dataObj={data}
                index={index}
                headers={headers}
                page={page}
                newRow={true}
                headings={processedHeadings}
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
                <LegacyCard.Section flush
                    key="summary-table-section"
                >
                    <IndexTable
                        key="summary-table"
                        headings={processedHeadings}
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
                <LegacyCard.Section
                    key="pagination-section"
                >
                    <HorizontalStack
                        key="pagination-stack"
                        align="center">
                        <Pagination
                            key="pagination"
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
