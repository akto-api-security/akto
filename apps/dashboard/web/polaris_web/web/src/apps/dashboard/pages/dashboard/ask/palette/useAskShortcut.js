import { useEffect } from "react"

// ⌘K / Ctrl+K opens the overlay from anywhere on the page — audited every keydown listener in
// web/src before adding this: there is no existing ⌘K or bare "/" handler anywhere in the repo.
// Fires even while focus is inside another input (the universal convention, and it's how you'd
// jump back to the palette from, say, the change-feed table's own search field); preventDefault
// so it doesn't also open the browser's own search/bookmark UI.
export default function useAskShortcut(onOpen) {
    useEffect(() => {
        const onKeyDown = (e) => {
            const isCmdK = (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k"
            if (!isCmdK) return
            e.preventDefault()
            onOpen()
        }
        document.addEventListener("keydown", onKeyDown)
        return () => document.removeEventListener("keydown", onKeyDown)
    }, [onOpen])
}
