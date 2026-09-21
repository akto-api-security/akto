import { useCallback, useEffect, useRef, useState } from "react"
import api from "./api"
import { toChangeRows, toOmittedGroups, toTileViewModels } from "./transform"

// Owns the overlay's landing-state fetch — tiles (recommendations + CRITICAL/HIGH insights) and
// the change feed, in one round trip. Loads only while the overlay is open (see `enabled`), and
// guards against setState after the overlay closes mid-flight — the same unmountedRef pattern
// InsightDetailView.jsx uses, since Modal unmount/remount on every open is exactly that case.
// `domain` picks which dashboard's tile set comes back — see AskOverlayButton's own prop.
export default function useAskData(enabled, domain) {
    const [tiles, setTiles] = useState([])
    const [changes, setChanges] = useState([])
    const [omittedGroups, setOmittedGroups] = useState([])
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
            setChanges(toChangeRows(overlay))
            setOmittedGroups(toOmittedGroups(overlay))
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

    return { tiles, changes, omittedGroups, loading, error, refetch: load }
}
