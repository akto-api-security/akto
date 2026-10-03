import { useCallback, useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { useNavigate } from "react-router-dom"
import useAskData from "./useAskData"
import HomeView from "./home/HomeView"
import ChatView from "./chat/ChatView"
import "./askOverlay.css"

const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

// The whole overlay — design_handoff_ask_akto_overlay/README.md. A custom sheet, not Polaris
// Modal: the design's exact sizing (max-width 760px, height min(700px, 100vh-7vh-16px), 7vh top
// offset), custom header/footer chrome and no Polaris title bar don't fit any Modal size variant
// (`large` is a fixed ~980px), and the README explicitly allows this ("a custom sheet if Modal's
// chrome gets in the way" — it does here). That trade means the backdrop/focus-trap/Escape
// Modal gives for free have to be hand-rolled below.
//
// Stays mounted at all times (rendered via portal regardless of `open`, hidden with `.ask-hidden`
// rather than unmounted) so `mode`/messages/conversationId in ChatView survive a close + reopen —
// "Keep the conversation while the page stays mounted, and clear it on 'New question'" per the
// design. Only an explicit "New question" click (handleNewQuestion) resets it.
export default function AskOverlay({ open, onClose, domain }) {
    const navigate = useNavigate()
    const { tiles, loading: tilesLoading, error: tilesError, refetch } = useAskData(open, domain)
    const [mode, setMode] = useState("home")
    const [seed, setSeed] = useState(null)
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
        const prevOverflow = document.body.style.overflow
        document.body.style.overflow = "hidden"
        return () => {
            document.removeEventListener("keydown", onKeyDown)
            document.body.style.overflow = prevOverflow
        }
    }, [open, onClose])

    // Navigating from inside the overlay closes it first — leaving it open behind a page
    // navigation would strand a stale sheet in the DOM.
    const handleOpenRoute = useCallback((route, params) => {
        onClose()
        if (!route) return
        const query = Object.entries(params || {})
            .filter(([, v]) => v !== undefined && v !== null && v !== "")
            .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(Array.isArray(v) ? v.join(",") : v)}`)
            .join("&")
        navigate(query ? `${route}?${query}` : route)
    }, [navigate, onClose])

    const handleAsk = useCallback((prompt) => {
        setMode("chat")
        setSeed({ prompt, nonce: Date.now() })
    }, [])

    const handleNewQuestion = useCallback(() => {
        setMode("home")
        setSeed(null)
    }, [])

    return createPortal(
        <div className="ask-overlay-root">
            <div
                className={`ask-backdrop${open ? "" : " ask-hidden"}`}
                onClick={onClose}
                role="presentation"
            >
                <div
                    ref={sheetRef}
                    className="ask-sheet"
                    onClick={(e) => e.stopPropagation()}
                    role="dialog"
                    aria-modal="true"
                    aria-label="Ask Akto"
                >
                    {mode === "home" ? (
                        <HomeView
                            open={open}
                            domain={domain}
                            tiles={tiles}
                            tilesLoading={tilesLoading}
                            tilesError={tilesError}
                            onRetryTiles={refetch}
                            onAsk={handleAsk}
                            onOpenRoute={handleOpenRoute}
                        />
                    ) : (
                        <ChatView
                            domain={domain}
                            seed={seed}
                            onClose={onClose}
                            onCollapse={handleNewQuestion}
                            onOpenRoute={handleOpenRoute}
                        />
                    )}
                </div>
            </div>
        </div>,
        document.body
    )
}
