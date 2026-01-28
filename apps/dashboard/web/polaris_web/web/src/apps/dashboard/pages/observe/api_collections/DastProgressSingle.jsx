import { useEffect, useState, useCallback } from "react"
import { useParams } from "react-router-dom"
import { Box, IndexFiltersMode, Text, HorizontalStack, Badge, Card, VerticalStack, Divider } from "@shopify/polaris"
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable"
import func from "@/util/func"
import api from "./api"
import TooltipText from "@/apps/dashboard/components/shared/TooltipText"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"

const headers = [
    {
        title: "URL",
        text: "URL",
        value: "urlComp",
        textValue: "url",
        filterKey: "url",
        showFilter: true
    },
    {
        title: "Source URL",
        text: "Source URL",
        value: "sourceUrlComp",
        textValue: "sourceUrl",
        filterKey: "sourceUrl",
        showFilter: true
    },
    {
        title: "Button Text",
        text: "Button Text",
        value: "buttonTextComp",
        textValue: "buttonText",
        filterKey: "buttonText",
        showFilter: true
    },
    {
        title: "Terminal",
        text: "Status",
        value: "statusComp",
        textValue: "status",
        filterKey: "status",
        showFilter: true
    },
    {
        title: "Discovered At",
        text: "Discovered At",
        value: "discoveredAt",
        sortKey: "timestamp"
    }
]

const sortOptions = [
    { label: 'Discovered At', value: 'timestamp asc', directionLabel: 'Recent first', sortKey: 'timestamp', columnIndex: 5 },
    { label: 'Discovered At', value: 'timestamp desc', directionLabel: 'Oldest first', sortKey: 'timestamp', columnIndex: 5 }
]

const resourceName = {
    singular: 'URL',
    plural: 'URLs',
}

const statusFilterChoices = [
    { label: 'Non-terminal', value: 'Non-terminal' },
    { label: 'Terminal', value: 'Terminal' }
]

function DastProgressSingle() {
    const { "crawlId": crawlId } = useParams()
    const [loading, setLoading] = useState(false)
    const [crawlDetails, setCrawlDetails] = useState(null)

    const formatCrawlerUrls = (crawlerUrls) => {
        return crawlerUrls.map((urlItem, index) => {
            const status = urlItem.accepted ? "Non-terminal" : "Terminal"
            const statusColor = urlItem.accepted ? "success" : "warning"

            return {
                id: `${urlItem.crawlId}-${urlItem.timestamp}-${index}`,
                url: urlItem.url,
                urlComp: (
                    <Box maxWidth="50vw">
                        <TooltipText
                            tooltip={urlItem.url}
                            text={urlItem.url}
                            textProps={{ fontWeight: 'medium' }}
                        />
                    </Box>
                ),
                sourceUrl: urlItem.sourceUrl || '-',
                sourceUrlComp: (
                    <Box maxWidth="30vw">
                        <TooltipText
                            tooltip={urlItem.sourceUrl || 'N/A'}
                            text={urlItem.sourceUrl || '-'}
                            textProps={{ fontWeight: 'regular' }}
                        />
                    </Box>
                ),
                buttonText: urlItem.buttonText || '-',
                buttonTextComp: (
                    <Box maxWidth="20vw">
                        <TooltipText
                            tooltip={urlItem.buttonText || 'N/A'}
                            text={urlItem.buttonText || '-'}
                            textProps={{ fontWeight: 'regular' }}
                        />
                    </Box>
                ),
                accepted: urlItem.accepted,
                status: status,
                statusComp: (
                    <Badge status={statusColor}>{status}</Badge>
                ),
                discoveredAt: func.prettifyEpoch(urlItem.timestamp),
                timestamp: urlItem.timestamp,
                crawlId: urlItem.crawlId
            }
        })
    }

    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => {
        setLoading(true)
        try {
            const resp = await api.fetchDastScan(crawlId, skip, limit, filters, queryValue, sortKey, sortOrder)
            const crawlerUrls = resp?.crawlerUrls || []
            const totalCount = resp?.totalCount || 0

            const formattedData = formatCrawlerUrls(crawlerUrls)

            setLoading(false)
            return { value: formattedData, total: totalCount }
        } catch (error) {
            func.setToast(true, true, "Failed to fetch DAST scan details")
            setLoading(false)
            return { value: [], total: 0 }
        }
    }, [crawlId, crawlDetails])

    useEffect(() => {
        setCrawlDetails(null)
        const fetchCounts = async () => {
            try {
                const resp = await api.fetchDastScanCounts(crawlId)
                if (resp) {
                    setCrawlDetails({
                        crawlId: crawlId,
                        totalUrls: resp.totalCount || 0,
                        acceptedUrls: resp.acceptedCount || 0,
                        rejectedUrls: resp.rejectedCount || 0
                    })
                }
            } catch (error) {
                func.setToast(true, true, "Failed to fetch DAST scan counts")
            }
        }
        if (crawlId) {
            fetchCounts()
        }
    }, [crawlId])

    const filters = [
        {
            key: 'status',
            label: 'Status',
            title: 'Status',
            choices: statusFilterChoices
        }
    ]

    return (
        <PageWithMultipleCards
            title={
                <Text variant="headingLg" as="h1">DAST Scan Details</Text>
            }
            components={[
                crawlDetails && (
                    <Box paddingBlockEnd="4" key="crawl-details">
                        <Card>
                            <VerticalStack gap="4">
                                <HorizontalStack gap="8" wrap={false}>
                                    <Box>
                                        <Text variant="bodyMd" color="subdued">Crawl ID</Text>
                                        <Text variant="bodyMd" fontWeight="semibold">{crawlDetails.crawlId}</Text>
                                    </Box>
                                    <Divider orientation="vertical" />
                                    <Box>
                                        <Text variant="bodyMd" color="subdued">Total Pages</Text>
                                        <Text variant="bodyMd" fontWeight="semibold">{crawlDetails.totalUrls}</Text>
                                    </Box>
                                    <Divider orientation="vertical" />
                                    <Box>
                                        <Text variant="bodyMd" color="subdued">Non-terminal Pages</Text>
                                        <Text variant="bodyMd" fontWeight="semibold" color="success">{crawlDetails.acceptedUrls}</Text>
                                    </Box>
                                    <Divider orientation="vertical" />
                                    <Box>
                                        <Text variant="bodyMd" color="subdued">Terminal Pages</Text>
                                        <Text variant="bodyMd" fontWeight="semibold" color="warning">{crawlDetails.rejectedUrls}</Text>
                                    </Box>
                                </HorizontalStack>
                            </VerticalStack>
                        </Card>
                    </Box>
                ),
                <GithubServerTable
                    key={crawlId}
                    pageLimit={50}
                    fetchData={fetchTableData}
                    sortOptions={sortOptions}
                    resourceName={resourceName}
                    headers={headers}
                    headings={headers}
                    loading={loading}
                    filters={filters}
                    mode={IndexFiltersMode.Default}
                    useNewRow={true}
                    condensedHeight={true}
                    disambiguateLabel={(key, value) => func.convertToDisambiguateLabelObj(value, null, 2)}
                />
            ]}
        />
    )
}

export default DastProgressSingle;