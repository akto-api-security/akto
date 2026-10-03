import { useCallback, useEffect, useRef, useState } from "react"
import api from "./api"
import { toTileViewModels } from "./transform"

// Owns the overlay's landing-state fetch — recommendations + CRITICAL/HIGH insight tiles, in one
// round trip. Loads only while the overlay is open (see `enabled`), and guards against setState
// after the overlay closes mid-flight — the same unmountedRef pattern InsightDetailView.jsx
// uses, since the sheet unmounting on close is exactly that case. `domain` picks which
// dashboard's tile set comes back — see AskOverlayButton's own prop.
export default function useAskData(enabled, domain) {
    const [tiles, setTiles] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(false)
    const unmountedRef = useRef(false)

    useEffect(() => () => { unmountedRef.current = true }, [])

    const load = useCallback(async () => {
        setLoading(true)
        setError(false)
        try {
            const overlay = await api.fetchAskOverlay(domain)
            if (unmountedRef.current) return
            setTiles(toTileViewModels(overlay))
        } catch (e) {
            if (unmountedRef.current) return
            setError(true)
        } finally {
            if (!unmountedRef.current) setLoading(false)
        }
    }, [domain])

    useEffect(() => {
        if (!enabled) return
        load()
    }, [enabled, load])

    return { tiles, loading, error, refetch: load }
}
