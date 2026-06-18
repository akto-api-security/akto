import { useEffect, useState } from "react"
import { Box, Card, Text, Badge, VerticalStack, Spinner, HorizontalStack } from "@shopify/polaris"
import api from "./api"

function LiveBrowserView({ crawlId }) {
    const [frame, setFrame] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        if (!crawlId) return

        let mounted = true

        const pollFrame = async () => {
            try {
                const frameData = await api.getLatestCrawlerFrame(crawlId)

                if (mounted && frameData && frameData.frameData) {
                    setFrame(frameData)
                    setLoading(false)
                    setError(null)
                }
            } catch (err) {
                if (mounted) {
                    setError("Failed to fetch frame")
                    console.error("Error fetching frame:", err)
                }
            }
        }

        // Initial fetch
        pollFrame()

        // Poll every 4 seconds
        const interval = setInterval(pollFrame, 4000)

        return () => {
            mounted = false
            clearInterval(interval)
        }
    }, [crawlId])

    if (loading) {
        return (
            <Card>
                <Box padding="8" width="100%">
                    <HorizontalStack align="center" blockAlign="center">
                        <VerticalStack align="center" inlineAlign="center" gap={2}>
                            <Spinner size="large" />
                            <Text>Loading live view...</Text>
                        </VerticalStack>
                    </HorizontalStack>
                </Box>
            </Card>
        )
    }

    if (error) {
        return (
            <Card>
                <Box padding="8" width="100%">
                    <HorizontalStack align="center" blockAlign="center">
                        <Text color="critical">{error}</Text>
                    </HorizontalStack>
                </Box>
            </Card>
        )
    }

    if (!frame || !frame.frameData) {
        return (
            <Card>
                <Box padding="8" width="100%">
                    <HorizontalStack align="center" blockAlign="center">
                        <VerticalStack align="center" inlineAlign="center" gap={2}>
                            <Spinner size="large" />
                            <Text>Waiting for first frame...</Text>
                        </VerticalStack>
                    </HorizontalStack>
                </Box>
            </Card>
        )
    }

    return (
        <Card>
            <VerticalStack gap="4">
                <HorizontalStack align="space-between">
                    <Text variant="headingSm">Live Browser View</Text>
                    <Badge status="success">LIVE</Badge>
                </HorizontalStack>

                <Text variant="bodyMd" color="subdued" breakWord>
                    <strong>URL:</strong> {frame.currentUrl || 'N/A'}
                </Text>

                <Box>
                    <img
                        src={`data:image/jpeg;base64,${frame.frameData}`}
                        alt="Live browser"
                        style={{
                            width: '100%',
                            height: 'auto',
                            border: '1px solid #e1e3e5',
                            borderRadius: '8px'
                        }}
                    />
                </Box>

                <Text variant="bodySm" color="subdued">
                    Last updated: {new Date(frame.timestamp * 1000).toLocaleTimeString()}
                </Text>
            </VerticalStack>
        </Card>
    )
}

export default LiveBrowserView
