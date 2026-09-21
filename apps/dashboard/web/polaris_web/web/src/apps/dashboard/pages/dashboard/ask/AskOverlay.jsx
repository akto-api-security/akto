import { useCallback, useState } from "react"
import { Modal, Box, Text, VerticalStack } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"
import useAskData from "./useAskData"
import RecommendationTiles from "./RecommendationTiles"
import ChangeFeedTable from "./ChangeFeedTable"
import CommandPalette from "./palette/CommandPalette"

// The whole overlay: Polaris Modal (large — backdrop, focus trap, Escape all come free) hosting
// the palette/chat at the top and the "worth doing now" / "what changed" evidence below it. No
// route, no redirect, no per-user layout preference — this is a centered overlay over whatever
// page the user was already on.
export default function AskOverlay({ open, onClose, domain }) {
    const navigate = useNavigate()
    const { tiles, changes, loading, error, refetch } = useAskData(open, domain)
    const [seedPrompt, setSeedPrompt] = useState(null)

    // Navigating from inside the overlay closes it first — leaving it open behind a page
    // navigation would strand a stale Modal in the DOM.
    const handleOpenRoute = useCallback((route, params, opts) => {
        onClose()
        if (!route) return
        if (opts?.fullUrl) {
            navigate(route)
            return
        }
        const query = Object.entries(params || {})
            .filter(([, v]) => v !== undefined && v !== null && v !== "")
            .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(Array.isArray(v) ? v.join(",") : v)}`)
            .join("&")
        navigate(query ? `${route}?${query}` : route)
    }, [navigate, onClose])

    const handleAsk = useCallback((prompt) => {
        setSeedPrompt({ prompt, nonce: Date.now() })
    }, [])

    return (
        <Modal open={open} onClose={onClose} title="Ask Akto" titleHidden large limitHeight>
            <Box padding="5">
                <VerticalStack gap="6">
                    <CommandPalette onOpenRoute={handleOpenRoute} seedPrompt={seedPrompt} />

                    <VerticalStack gap="3">
                        <Text variant="headingSm" color="subdued">Worth doing now</Text>
                        <RecommendationTiles
                            tiles={tiles}
                            loading={loading}
                            error={error}
                            onAsk={handleAsk}
                            onOpenRoute={handleOpenRoute}
                            onRetry={refetch}
                        />
                    </VerticalStack>

                    <VerticalStack gap="3">
                        <Text variant="headingSm" color="subdued">What changed</Text>
                        <ChangeFeedTable rows={changes} loading={loading} onOpenRoute={handleOpenRoute} />
                    </VerticalStack>
                </VerticalStack>
            </Box>
        </Modal>
    )
}
