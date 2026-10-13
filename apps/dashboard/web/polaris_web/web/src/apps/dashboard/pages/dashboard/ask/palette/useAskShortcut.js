import { useEffect } from "react"

// ⌘K / Ctrl+K toggles the overlay from anywhere on the page (design_handoff_ask_akto_overlay/
// README.md: "⌘K / Ctrl+K toggles the popup from anywhere") — audited every keydown listener in
// web/src before adding this: there is no existing ⌘K or bare "/" handler anywhere in the repo.
// Fires even while focus is inside another input (the universal convention, and it's how you'd
// jump back to the palette from, say, a page's own search field); preventDefault so it doesn't
// also open the browser's own search/bookmark UI.
export default function useAskShortcut(onToggle) {
    useEffect(() => {
        const onKeyDown = (e) => {
            const isCmdK = (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k"
            if (!isCmdK) return
            e.preventDefault()
            onToggle()
        }
        document.addEventListener("keydown", onKeyDown)
        return () => document.removeEventListener("keydown", onKeyDown)
    }, [onToggle])
}
