import { useEffect, useState } from "react"
import { Box, Card, Text, VerticalStack, HorizontalStack, Button, Spinner } from "@shopify/polaris"
import api from "./api"

/**
 * Shows the navigation graph (Mermaid flowchart source) the crawler uploaded
 * when the crawl finished. The graph is persisted on the CrawlerRun in the DB
 * and fetched via /api/getCrawlerGraph. We render the Mermaid source with copy
 * and download actions plus a link to open it in mermaid.live for a visual.
 */
function CrawlGraphView({ crawlId }) {
    const [graph, setGraph] = useState("")
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        if (!crawlId) return
        let mounted = true
        api.getCrawlerGraph(crawlId)
            .then((g) => { if (mounted) { setGraph(g || ""); setLoading(false) } })
            .catch(() => { if (mounted) setLoading(false) })
        return () => { mounted = false }
    }, [crawlId])

    if (loading) {
        return (
            <Card>
                <Box padding="6" width="100%">
                    <HorizontalStack align="center" blockAlign="center" gap="2">
                        <Spinner size="small" />
                        <Text>Loading navigation graph...</Text>
                    </HorizontalStack>
                </Box>
            </Card>
        )
    }

    if (!graph) {
        return (
            <Card>
                <Box padding="4">
                    <Text color="subdued">Navigation graph not available yet. It is shared when the crawl finishes.</Text>
                </Box>
            </Card>
        )
    }

    const download = () => {
        const blob = new Blob([graph], { type: "text/plain" })
        const url = URL.createObjectURL(blob)
        const a = document.createElement("a")
        a.href = url
        a.download = `navigation-${crawlId}.mmd`
        a.click()
        URL.revokeObjectURL(url)
    }

    // mermaid.live encodes the diagram as base64 of a JSON {code, mermaid:{...}}
    const openInMermaidLive = () => {
        try {
            const payload = JSON.stringify({ code: graph, mermaid: { theme: "default" } })
            const b64 = window.btoa(unescape(encodeURIComponent(payload)))
            window.open(`https://mermaid.live/edit#base64:${b64}`, "_blank", "noopener")
        } catch (e) {
            /* ignore */
        }
    }

    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingsm" fontWeight="semibold">Navigation graph</Text>
                        <HorizontalStack gap="2">
                            <Button size="slim" onClick={() => navigator.clipboard?.writeText(graph)}>Copy</Button>
                            <Button size="slim" onClick={download}>Download .mmd</Button>
                            <Button size="slim" primary onClick={openInMermaidLive}>Open diagram</Button>
                        </HorizontalStack>
                    </HorizontalStack>
                    <Box background="bg-subdued" padding="3" borderRadius="2" overflowX="scroll" overflowY="scroll" maxHeight="320px">
                        <pre style={{ margin: 0, fontFamily: "monospace", fontSize: "12px", whiteSpace: "pre" }}>{graph}</pre>
                    </Box>
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default CrawlGraphView
