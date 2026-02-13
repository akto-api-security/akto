import { useEffect, useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { Box, IndexFiltersMode, Text, HorizontalStack, Badge, Card, VerticalStack, Divider, Button } from "@shopify/polaris"
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable"
import func from "@/util/func"
import api from "./api"
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered"
import TooltipText from "@/apps/dashboard/components/shared/TooltipText"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import LiveBrowserView from './LiveBrowserView'

const headers = [
    {
        title: "Pages crawled",
        text: "Pages crawled",
        value: "sourceUrlComp",
        textValue: "sourceUrl",
        filterKey: "sourceUrl",
        showFilter: true
    },
    {
        title: "Discovered Links",
        text: "Discovered Links",
        value: "urlComp",
        textValue: "url",
        filterKey: "url",
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

function DastProgressSingle() {
    const { "crawlId": crawlId } = useParams()
    const navigate = useNavigate()
    const [loading, setLoading] = useState(false)
    const [data, setData] = useState([])
    const [crawlDetails, setCrawlDetails] = useState(null)
    const [scanMetadata, setScanMetadata] = useState(null)
    const [showLiveBrowser, setShowLiveBrowser] = useState(false)
    const [crawlerStatus, setCrawlerStatus] = useState(null)

    const fetchDastScan = async () => {
        try {
            setLoading(true)
            const resp = await api.fetchDastScan(crawlId)

            // Fetch all scans to get metadata for this specific crawl
            const allScans = await api.fetchAllDastScans()
            const currentScan = allScans?.find(scan => scan.crawlId === crawlId)

            if (currentScan) {
                setScanMetadata({
                    moduleName: currentScan.moduleName || "Internal DAST (Akto)",
                    applicationPages: currentScan.applicationPages || "-",
                    urlTemplatePatterns: currentScan.urlTemplatePatterns || "-"
                })
                setCrawlerStatus(currentScan.status)
            }

            const crawlerUrls = resp || []
            const formattedData = crawlerUrls.map((urlItem, index) => {
                const status = urlItem.accepted ? "Non-terminal" : "Terminal"
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
                    sourceXpath: urlItem.sourceXpath || '-',
                    sourceXpathComp: (
                        <Box maxWidth="30vw">
                            <TooltipText
                                tooltip={urlItem.sourceXpath || 'N/A'}
                                text={urlItem.sourceXpath || '-'}
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
            primaryAction={crawlerStatus === 'RUNNING' && (
                <Button onClick={() => setShowLiveBrowser(!showLiveBrowser)}>
                    {showLiveBrowser ? 'Hide Live View' : 'Show Live View'}
                </Button>
            )}
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
                                {scanMetadata && (
                                    <>
                                        <Divider />
                                        <VerticalStack gap="3">
                                            <Box>
                                                <Text variant="bodyMd" color="subdued">Module Name</Text>
                                                <Text variant="bodyMd" fontWeight="semibold">{scanMetadata.moduleName}</Text>
                                            </Box>
                                            <Box>
                                                <Text variant="bodyMd" color="subdued">Application Pages</Text>
                                                <Text variant="bodyMd" fontWeight="regular">{scanMetadata.applicationPages}</Text>
                                            </Box>
                                            <Box>
                                                <Text variant="bodyMd" color="subdued">URL Template Patterns</Text>
                                                <Text variant="bodyMd" fontWeight="regular" breakWord>{scanMetadata.urlTemplatePatterns}</Text>
                                            </Box>
                                        </VerticalStack>
                                    </>
                                )}
                            </VerticalStack>
                        </Card>
                    </Box>
                ),
                showLiveBrowser && crawlerStatus === 'RUNNING' && (
                    <Box paddingBlockEnd="4" key="live-view">
                        <LiveBrowserView crawlId={crawlId} />
                    </Box>
                ),

                // todo("dast graph")

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