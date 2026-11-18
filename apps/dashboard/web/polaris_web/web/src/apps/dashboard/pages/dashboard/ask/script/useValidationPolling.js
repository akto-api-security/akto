import { useCallback, useEffect, useRef, useState } from "react"
import api from "../../../testing/api"

const POLL_MS = 3000
// Longer than the 10-minute waits a script may use, so a slow script is not mistaken for a lost run.
const POLL_LIMIT_MS = 12 * 60 * 1000

const IDLE = { phase: "idle", result: null, error: null, transientError: false }

// Polls api/fetchScriptValidation until a mini-testing module reports a result. A module reports only when its
// run ends, so "waiting" covers both not yet picked up and still running. Phases:
//   idle | waiting | done (result) | failed (error: the dashboard's reason) | timedOut (no result in 12 minutes)
// A failed request is retried until the limit; only an answer carrying the dashboard's own error is final.
export default function useValidationPolling() {
    const [state, setState] = useState(IDLE)
    const timer = useRef(null)
    // Each start or stop bumps this, so a loop that was cancelled never writes state.
    const generation = useRef(0)

    const stop = useCallback(() => {
        generation.current += 1
        clearTimeout(timer.current)
        setState(IDLE)
    }, [])

    const start = useCallback((playgroundId) => {
        generation.current += 1
        const mine = generation.current
        clearTimeout(timer.current)
        const deadline = Date.now() + POLL_LIMIT_MS
        setState({ phase: "waiting", result: null, error: null, transientError: false })

        const tick = async () => {
            if (mine !== generation.current) return
            try {
                const resp = await api.fetchScriptValidation(playgroundId)
                if (mine !== generation.current) return
                if (!resp?.scriptValidationPending) {
                    const result = resp?.scriptValidationResult || null
                    setState(result
                        ? { phase: "done", result, error: null, transientError: false }
                        : { phase: "failed", result: null, error: "The module reported no result.", transientError: false })
                    return
                }
                setState((s) => ({ ...s, phase: "waiting", transientError: false }))
            } catch (e) {
                if (mine !== generation.current) return
                const reason = e?.response?.data?.actionErrors?.[0]
                if (reason) {
                    setState({ phase: "failed", result: null, error: reason, transientError: false })
                    return
                }
                setState((s) => ({ ...s, phase: "waiting", transientError: true }))
            }
            if (Date.now() >= deadline) {
                setState((s) => ({ ...s, phase: "timedOut" }))
                return
            }
            timer.current = setTimeout(tick, POLL_MS)
        }
        tick()
    }, [])

    useEffect(() => () => {
        generation.current += 1
        clearTimeout(timer.current)
    }, [])

    return { ...state, start, stop }
}
