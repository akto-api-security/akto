import { useCallback, useState } from "react"
import { Button, Icon } from "@shopify/polaris"
import { SearchMinor } from "@shopify/polaris-icons"
import AskOverlay from "./AskOverlay"
import useAskShortcut from "./palette/useAskShortcut"

// The one thing each dashboard needs to mount to get the overlay: a header button, the ⌘K
// binding, and the overlay itself. Owns open/close state so nothing else has to. `domain` — "API"
// (default) | "AGENTIC" | "ENDPOINT" — picks which dashboard's recommendation tiles and default
// insight groups the overlay shows; pass the one matching the page this button lives on.
export default function AskOverlayButton({ domain = "API" }) {
    const [open, setOpen] = useState(false)
    const handleOpen = useCallback(() => setOpen(true), [])
    const handleClose = useCallback(() => setOpen(false), [])

    useAskShortcut(handleOpen)

    return (
        <>
            <Button icon={<Icon source={SearchMinor} />} onClick={handleOpen}>
                Ask Akto
            </Button>
            <AskOverlay open={open} onClose={handleClose} domain={domain} />
        </>
    )
}
