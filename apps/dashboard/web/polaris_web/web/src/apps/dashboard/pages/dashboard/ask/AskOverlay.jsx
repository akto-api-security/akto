import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Box, HorizontalGrid, Portal, ScrollLock } from "@shopify/polaris"
import useAskData from "./useAskData"
import useAskChat from "./chat/useAskChat"
import HomeView from "./home/HomeView"
import ChatView from "./chat/ChatView"
import useDashboardCategory from "./palette/useDashboardCategory"

const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

// Same layers Polaris Modal uses (--p-z-index-10 / -11).
const BACKDROP_Z_INDEX = "518"
const SHEET_Z_INDEX = "519"

// Centers the sheet at up to 960px. A grid, not HorizontalStack align="center": a stack's align
// is an inherited CSS custom property, so it would center every HorizontalStack inside the sheet
// that doesn't set align itself.
const SHEET_COLUMNS = "minmax(0, 1fr) minmax(0, 960px) minmax(0, 1fr)"

// The whole overlay — design_handoff_ask_akto_overlay/README.md. A custom sheet rather than
// Polaris Modal: the design's 760px width, header/footer chrome and lack of a title bar don't fit
// any Modal size, so the focus trap and Escape handling Modal provides are done here.
//
// The sheet unmounts when closed; the conversation survives because useAskChat lives here, and
// this component stays mounted with the page.
export default function AskOverlay({ open, onClose }) {
    const navigate = useNavigate()
    const category = useDashboardCategory()
    const chat = useAskChat()
    const [mode, setMode] = useState("home")
    // Tiles only appear on the home view, so reopening into a chat (or staying in one) fetches
    // nothing; going back via "New question" loads them then.
    const { tiles, loading: tilesLoading, error: tilesError, refetch } = useAskData(open && mode === "home", category)
    const sheetRef = useRef(null)

    useEffect(() => {
        if (!open) return
        const onKeyDown = (e) => {
            if (e.key === "Escape") {
                e.preventDefault()
                onClose()
                return
            }
            if (e.key !== "Tab" || !sheetRef.current) return
            const focusable = Array.from(sheetRef.current.querySelectorAll(FOCUSABLE_SELECTOR))
            if (!focusable.length) return
            const first = focusable[0]
            const last = focusable[focusable.length - 1]
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault()
                last.focus()
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault()
                first.focus()
            }
        }
        document.addEventListener("keydown", onKeyDown)
        return () => document.removeEventListener("keydown", onKeyDown)
    }, [open, onClose])

    // Navigating from inside the overlay closes it first, so no stale sheet is left behind.
    const handleOpenRoute = useCallback((route, params) => {
        onClose()
        if (!route) return
        const query = Object.entries(params || {})
            .filter(([, v]) => v !== undefined && v !== null && v !== "")
            .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(Array.isArray(v) ? v.join(",") : v)}`)
            .join("&")
        navigate(query ? `${route}?${query}` : route)
    }, [navigate, onClose])

    const { ask, reset } = chat
    const handleAsk = useCallback((prompt) => {
        setMode("chat")
        ask(prompt)
    }, [ask])

    const handleNewQuestion = useCallback(() => {
        reset()
        setMode("home")
    }, [reset])

    if (!open) return null

    return (
        <Portal idPrefix="ask-akto">
            <ScrollLock />
            <Box
                position="fixed"
                insetBlockStart="0"
                insetBlockEnd="0"
                insetInlineStart="0"
                insetInlineEnd="0"
                zIndex={BACKDROP_Z_INDEX}
                background="bg-ask-backdrop"
                onClick={onClose}
            />
            <Box
                position="fixed"
                insetBlockStart="16"
                insetInlineStart="4"
                insetInlineEnd="4"
                zIndex={SHEET_Z_INDEX}
                onClick={onClose}
            >
                <HorizontalGrid columns={SHEET_COLUMNS}>
                    <Box />
                    <Box
                        ref={sheetRef}
                        role="dialog"
                        aria-modal="true"
                        aria-label="Ask Akto"
                        background="bg"
                        borderRadius="3"
                        shadow="2xl"
                        overflowX="hidden"
                        overflowY="hidden"
                        onClick={(e) => e.stopPropagation()}
                    >
                        {mode === "home" ? (
                            <HomeView
                                tiles={tiles}
                                tilesLoading={tilesLoading}
                                tilesError={tilesError}
                                onRetryTiles={refetch}
                                onAsk={handleAsk}
                                onOpenRoute={handleOpenRoute}
                                onClose={onClose}
                            />
                        ) : (
                            <ChatView
                                chat={chat}
                                onClose={onClose}
                                onNewQuestion={handleNewQuestion}
                                onOpenRoute={handleOpenRoute}
                            />
                        )}
                    </Box>
                    <Box />
                </HorizontalGrid>
            </Box>
        </Portal>
    )
}
