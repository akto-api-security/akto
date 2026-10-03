import { useCallback, useEffect, useRef, useState } from "react"
import { Icon, Text } from "@shopify/polaris"
import { CancelMinor, ChevronLeftMinor } from "@shopify/polaris-icons"
import AgenticThinkingBox from "@/apps/dashboard/pages/agentic/components/AgenticThinkingBox"
import AgenticSearchInput from "@/apps/dashboard/pages/agentic/components/AgenticSearchInput"
import { sendQuery } from "@/apps/dashboard/pages/agentic/services/agenticService"
import ChatMessage from "./ChatMessage"

const CONFIRM_MESSAGE = "Yes, go ahead — please proceed."
const UNDO_MESSAGE = "Please undo the change you just made."

// The overlay's chat mode — design_handoff_ask_akto_overlay/README.md, "4. Answer view". Reuses
// the SAME agenticService.sendQuery the full Ask Akto page and every other chat surface in the
// app already use (not a fourth chat implementation) via `conversationType=COMMAND_PALETTE`.
//
// Owns `acts` (per-message write-action state) here rather than in ChatMessage because
// confirming or undoing a write sends a FOLLOW-UP message over the exact same conversation, but
// deliberately does NOT push it into the visible `messages` list as its own turn — the design
// shows the result inline in the action row's done panel, not as a second "Yes, go ahead" bubble
// cluttering the thread. That silent round trip still goes through the real two-phase contract:
// only this component's onConfirmAction ever sends the confirmation, and only after the user's
// own click.
export default function ChatView({ domain, seed, onClose, onCollapse, onOpenRoute }) {
    const [messages, setMessages] = useState([])
    const [loading, setLoading] = useState(false)
    const [draft, setDraft] = useState("")
    const [acts, setActs] = useState({})
    const conversationIdRef = useRef(null)
    const unmountedRef = useRef(false)
    const scrollRef = useRef(null)
    const seededRef = useRef(null)

    useEffect(() => () => { unmountedRef.current = true }, [])

    useEffect(() => {
        const el = scrollRef.current
        if (!el) return
        requestAnimationFrame(() => { el.scrollTop = el.scrollHeight })
    }, [messages, loading, acts])

    const ask = useCallback(async (text) => {
        const trimmed = (text || "").trim()
        if (!trimmed) return
        setMessages((prev) => [...prev, { id: Date.now(), role: "user", message: trimmed }])
        setLoading(true)
        try {
            const res = await sendQuery(trimmed, conversationIdRef.current, "COMMAND_PALETTE")
            if (unmountedRef.current) return
            conversationIdRef.current = res?.conversationId || conversationIdRef.current
            setMessages((prev) => [...prev, {
                id: Date.now() + 1,
                role: "assistant",
                message: res?.response || "I couldn't get an answer just now — please try again.",
                userPrompt: trimmed,
            }])
        } catch (e) {
            if (unmountedRef.current) return
            setMessages((prev) => [...prev, { id: Date.now() + 1, role: "assistant", message: "Something went wrong reaching Ask Akto. Please try again." }])
        } finally {
            if (!unmountedRef.current) setLoading(false)
        }
    }, [])

    useEffect(() => {
        if (!seed || seed.nonce === seededRef.current) return
        seededRef.current = seed.nonce
        ask(seed.prompt)
    }, [seed, ask])

    const setActState = (id, patch) => setActs((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }))

    const onReviewAction = (id) => setActState(id, { state: "confirm" })
    const onCancelAction = (id) => setActState(id, { state: "idle" })

    const onConfirmAction = useCallback(async (id) => {
        setActState(id, { state: "running" })
        try {
            const res = await sendQuery(CONFIRM_MESSAGE, conversationIdRef.current, "COMMAND_PALETTE")
            if (unmountedRef.current) return
            conversationIdRef.current = res?.conversationId || conversationIdRef.current
            setActState(id, { state: "done", doneText: res?.response || "Done." })
        } catch (e) {
            if (unmountedRef.current) return
            setActState(id, { state: "confirm", doneText: null })
        }
    }, [])

    const onUndoAction = useCallback(async (id) => {
        setActState(id, { state: "running" })
        try {
            const res = await sendQuery(UNDO_MESSAGE, conversationIdRef.current, "COMMAND_PALETTE")
            if (unmountedRef.current) return
            conversationIdRef.current = res?.conversationId || conversationIdRef.current
            setActState(id, { state: "done", doneText: res?.response || "Done." })
        } catch (e) {
            if (unmountedRef.current) return
            setActState(id, { state: "done" })
        }
    }, [])

    const handleDraftSubmit = (value) => {
        if (value && value.trim()) {
            ask(value.trim())
            setDraft("")
        }
    }

    return (
        <>
            <div className="ask-chat-header">
                <button type="button" className="ask-chat-header-btn" onClick={onCollapse}>
                    <Icon source={ChevronLeftMinor} />
                    <span>New question</span>
                </button>
                <span className="ask-spacer" />
                <button type="button" className="ask-chat-close-btn" onClick={onClose} aria-label="Close">
                    <Icon source={CancelMinor} />
                </button>
            </div>

            <div ref={scrollRef} className="ask-messages" aria-live="polite" aria-atomic="false" aria-busy={loading}>
                {messages.map((msg) => msg.role === "user" ? (
                    <div key={msg.id} className="ask-user-bubble"><Text as="span">{msg.message}</Text></div>
                ) : (
                    <ChatMessage
                        key={msg.id}
                        message={msg.message}
                        userPrompt={msg.userPrompt}
                        domain={domain}
                        actionState={acts[msg.id]}
                        onReviewAction={() => onReviewAction(msg.id)}
                        onCancelAction={() => onCancelAction(msg.id)}
                        onConfirmAction={() => onConfirmAction(msg.id)}
                        onUndoAction={() => onUndoAction(msg.id)}
                        onOpenRoute={onOpenRoute}
                        onAsk={ask}
                    />
                ))}
                {loading ? <AgenticThinkingBox /> : null}
            </div>

            <div className="ask-composer-wrap">
                <AgenticSearchInput
                    value={draft}
                    onChange={setDraft}
                    onSubmit={handleDraftSubmit}
                    placeholder="Ask a follow-up"
                    isStreaming={loading}
                    isFixed={false}
                    inputWidth="100%"
                    containerStyle={{ display: "block" }}
                    helperText="Actions that change data always show a preview first."
                />
            </div>
        </>
    )
}
