import { useCallback, useState } from "react"
import AskOverlay from "./AskOverlay"
import useAskShortcut from "./palette/useAskShortcut"
import "./askOverlay.css"

// The one thing each dashboard needs to mount to get the overlay: a header button, the ⌘K
// binding, and the overlay itself. Owns open/close state so nothing else has to — AskOverlay
// itself stays mounted regardless (see its own header comment), this is just the visibility
// toggle. `domain` — "API" (default) | "AGENTIC" | "ENDPOINT" — picks which dashboard's
// recommendation tiles and default insight groups the overlay shows; pass the one matching the
// page this button lives on.
export default function AskOverlayButton({ domain = "API" }) {
    const [open, setOpen] = useState(false)
    const handleToggle = useCallback(() => setOpen((o) => !o), [])
    const handleClose = useCallback(() => setOpen(false), [])

    useAskShortcut(handleToggle)

    return (
        <>
            <button type="button" className="ask-trigger-btn" onClick={handleToggle}>
                <span className="ask-trigger-dot" />
                <span>Ask Akto</span>
                <span className="ask-trigger-kbd">⌘K</span>
            </button>
            <AskOverlay open={open} onClose={handleClose} domain={domain} />
        </>
    )
}
