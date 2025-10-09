import { useEffect, useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { Box, IndexFiltersMode, Text, Page, Button, HorizontalStack, Badge, Card, VerticalStack, Divider } from "@shopify/polaris"
import { ArrowLeftMinor } from "@shopify/polaris-icons"
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable"
import request from "@/util/request"
import func from "@/util/func"
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered"
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
    // TODO: implement this
    // {
    //     title: "Status",
    //     text: "Status",
    //     value: "statusComp",
    //     textValue: "status",
    //     filterKey: "status",
    //     showFilter: true
    // },
    {
        title: "Discovered At",
        text: "Discovered At",
        value: "discoveredAt",
    }
]

const sortOptions = [
    { label: 'Discovered At', value: 'timestamp asc', directionLabel: 'Recent first', sortKey: 'timestamp', columnIndex: 2 },
    { label: 'Discovered At', value: 'timestamp desc', directionLabel: 'Oldest first', sortKey: 'timestamp', columnIndex: 2 }
]

const resourceName = {
    singular: 'URL',
    plural: 'URLs',
}

function DastProgressSingle() {
    const { "crawlId": crawlId } = useParams()
    const navigate = useNavigate()
    const [loading, setLoading] = useState(false)
    const [data, setData] = useState([])
    const [crawlDetails, setCrawlDetails] = useState(null)

    const fetchDastScan = async () => {
        try {
            setLoading(true)
            const resp = await request({
                url: '/api/fetchDastScan',
                method: 'post',
                data: { crawlId }
            })

            const crawlerUrls = resp.crawlerUrls || []
            const formattedData = crawlerUrls.map((urlItem, index) => {
                const status = urlItem.accepted ? "Visited" : "Rejected"
                const statusColor = urlItem.accepted ? "success" : "warning"

                return {
                    id: `${urlItem.crawlId}-${index}`,
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

            setData(formattedData)

            // Set crawl details for the header
            if (crawlerUrls.length > 0) {
                setCrawlDetails({
                    crawlId: crawlId,
                    totalUrls: crawlerUrls.length,
                    acceptedUrls: crawlerUrls.filter(u => u.accepted).length,
                    rejectedUrls: crawlerUrls.filter(u => !u.accepted).length
                })
            }

            setLoading(false)
        } catch (error) {
            func.setToast(true, true, "Failed to fetch DAST scan details")
            setLoading(false)
        }
    }

    useEffect(() => {
        if (crawlId) {
            fetchDastScan()
        }
    }, [crawlId])

    if (loading) {
        return <SpinnerCentered />
    }

    return (
        <PageWithMultipleCards
            title={
                <Text variant="headingLg" as="h1">DAST Scan Details</Text>
            }
            components={[
                crawlDetails && (
                    <Box paddingBlockEnd="4">
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
                                    {/* TODO: implement this later. */}
                                    {/* <Box>
                                        <Text variant="bodyMd" color="subdued">Visited Pages</Text>
                                        <Text variant="bodyMd" fontWeight="semibold" color="success">{crawlDetails.acceptedUrls}</Text>
                                    </Box>
                                    <Divider orientation="vertical" />
                                    <Box>
                                        <Text variant="bodyMd" color="subdued">Rejected Pages</Text>
                                        <Text variant="bodyMd" fontWeight="semibold" color="warning">{crawlDetails.rejectedUrls}</Text>
                                    </Box> */}
                                </HorizontalStack>
                            </VerticalStack>
                        </Card>
                    </Box>
                ),
                <GithubSimpleTable
                    data={data}
                    sortOptions={sortOptions}
                    resourceName={resourceName}
                    headers={headers}
                    headings={headers}
                    loading={loading}
                    mode={IndexFiltersMode.Default}
                    useNewRow={true}
                    condensedHeight={true}
                    disambiguateLabel={(_, value) => func.convertToDisambiguateLabelObj(value, null, 2)}
                />
            ]}
        />
    )
}

export default DastProgressSingle;