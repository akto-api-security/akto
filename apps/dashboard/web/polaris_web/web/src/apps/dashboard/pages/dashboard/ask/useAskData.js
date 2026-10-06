import { useCallback, useEffect, useRef, useState } from "react"
import api from "./api"
import { applyCuration, toTileViewModels } from "./transform"

// The overlay's landing-state fetch: computed tiles first, then — without blocking them — the AI
// curation that reorders them and rewrites the question each fires. Loads every time the overlay
// opens, and again if the dashboard category changes while open. A response that belongs to an
// earlier load (closed and reopened, or switched dashboards) is discarded.
export default function useAskData(enabled, category) {
    const [tiles, setTiles] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(false)
    const loadIdRef = useRef(0)

    useEffect(() => () => { loadIdRef.current += 1 }, [])

    const load = useCallback(async () => {
        const loadId = ++loadIdRef.current
        const isCurrent = () => loadId === loadIdRef.current
        setLoading(true)
        setError(false)

        let computed
        try {
            computed = toTileViewModels(await api.fetchAskOverlay())
        } catch (e) {
            if (isCurrent()) {
                setError(true)
                setLoading(false)
            }
            return
        }
        if (!isCurrent()) return
        setTiles(computed)
        setLoading(false)

        if (computed.length < 2) return
        try {
            const curation = await api.fetchAskOverlayCuration()
            if (isCurrent()) setTiles(applyCuration(computed, curation))
        } catch (e) {
            // Curation is optional; the computed tiles are already on screen.
        }
    }, [])

    useEffect(() => {
        if (!enabled) return
        load()
    }, [enabled, category, load])

    return { tiles, loading, error, refetch: load }
}
