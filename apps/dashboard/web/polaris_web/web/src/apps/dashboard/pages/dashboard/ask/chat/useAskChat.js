import { useCallback, useEffect, useRef, useState } from "react"
import { sendQuery } from "@/apps/dashboard/pages/agentic/services/agenticService"

const CONVERSATION_TYPE = "COMMAND_PALETTE"
const CONFIRM_MESSAGE = "Yes, go ahead — please proceed."
const UNDO_MESSAGE = "Please undo the change you just made."

// The overlay's conversation state. Owned by AskOverlay (which stays mounted with the page) so a
// close + reopen keeps the conversation; only reset() — "New question" — clears it.
//
// Reuses the same agenticService.sendQuery every other chat surface uses. Confirming or undoing a
// write sends a follow-up over the same conversation without adding a visible turn: the result is
// shown inline in the action row. Only confirmAction ever sends the confirmation, and only from
// the user's own click — the two-phase write contract.
export default function useAskChat() {
    const [messages, setMessages] = useState([])
    const [loading, setLoading] = useState(false)
    const [acts, setActs] = useState({})
    const conversationIdRef = useRef(null)
    const mountedRef = useRef(true)
    // Bumped on reset(), so a reply that lands after "New question" is dropped instead of
    // appearing in the fresh conversation.
    const generationRef = useRef(0)

    useEffect(() => () => { mountedRef.current = false }, [])

    const isStale = (generation) => !mountedRef.current || generation !== generationRef.current

    const send = useCallback(async (text) => {
        const res = await sendQuery(text, conversationIdRef.current, CONVERSATION_TYPE)
        conversationIdRef.current = res?.conversationId || conversationIdRef.current
        return res
    }, [])

    const ask = useCallback(async (text) => {
        const trimmed = (text || "").trim()
        if (!trimmed) return
        const generation = generationRef.current
        setMessages((prev) => [...prev, { id: `u_${Date.now()}`, role: "user", message: trimmed }])
        setLoading(true)
        let reply
        try {
            const res = await send(trimmed)
            reply = res?.response || "I couldn't get an answer just now — please try again."
        } catch (e) {
            reply = "Something went wrong reaching Ask Akto. Please try again."
        }
        if (isStale(generation)) return
        setMessages((prev) => [...prev, { id: `a_${Date.now()}`, role: "assistant", message: reply, userPrompt: trimmed }])
        setLoading(false)
    }, [send])

    // The sheet unmounts on close, so without this every past answer would replay its
    // word-by-word streaming on reopen. Only answers that finished streaming are marked.
    const markStreamed = useCallback((id) => {
        setMessages((prev) => prev.map((m) => (m.id === id && !m.streamed ? { ...m, streamed: true } : m)))
    }, [])

    const setActState = (id, patch) => setActs((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }))

    const reviewAction = useCallback((id) => setActState(id, { state: "confirm" }), [])
    const cancelAction = useCallback((id) => setActState(id, { state: "idle" }), [])

    const runAction = useCallback(async (id, text, stateOnError) => {
        const generation = generationRef.current
        setActState(id, { state: "running" })
        try {
            const res = await send(text)
            if (isStale(generation)) return
            setActState(id, { state: "done", doneText: res?.response || "Done." })
        } catch (e) {
            if (isStale(generation)) return
            setActState(id, stateOnError)
        }
    }, [send])

    const confirmAction = useCallback((id) => runAction(id, CONFIRM_MESSAGE, { state: "confirm", doneText: null }), [runAction])
    const undoAction = useCallback((id) => runAction(id, UNDO_MESSAGE, { state: "done" }), [runAction])

    const reset = useCallback(() => {
        generationRef.current += 1
        conversationIdRef.current = null
        setMessages([])
        setActs({})
        setLoading(false)
    }, [])

    return { messages, loading, acts, ask, markStreamed, reviewAction, cancelAction, confirmAction, undoAction, reset }
}
