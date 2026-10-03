import { useEffect, useMemo, useRef, useState } from "react"
import { Icon, Text, VerticalStack } from "@shopify/polaris"
import { AnalyticsMajor, ChevronRightMinor } from "@shopify/polaris-icons"
import RecommendationTiles from "./RecommendationTiles"
import ResultsList from "./ResultsList"
import { buildResults } from "../palette/resolveCommand"
import { applyNavigationSideEffects } from "../palette/paletteHelpers"
import { SUGGESTED_PROMPTS_BY_DOMAIN } from "../palette/commandRegistry"

// The overlay's home surface — design_handoff_ask_akto_overlay/README.md, screens 2 and 3.
// One input drives two bodies: empty query shows tiles + "Try asking"; a non-empty query
// replaces the body with the typed-results list (Ask Akto / Go to / Suggested questions),
// arrow-key navigable, Enter running whichever row is active. Owns `query`/`active` because both
// the input and the results list need them in lockstep — splitting that state across two
// components would just be prop-drilling the same two values back and forth.
export default function HomeView({ open, domain, tiles, tilesLoading, tilesError, onRetryTiles, onAsk, onOpenRoute }) {
    const [query, setQuery] = useState("")
    const [active, setActive] = useState(0)
    const inputRef = useRef(null)

    // HomeView stays mounted across close/reopen (see AskOverlay's header comment on why —
    // conversation persistence needs the chat state alive, and this component is the sibling
    // that pays for it), so a fresh query + focus on every reopen has to be driven by `open`
    // rather than mount, which only fires once.
    useEffect(() => {
        if (!open) return
        setQuery("")
        setActive(0)
        inputRef.current?.focus()
    }, [open])

    const results = useMemo(() => buildResults(query, domain), [query, domain])
    const hasQuery = query.trim().length > 0
    const clampedActive = Math.min(active, Math.max(results.length - 1, 0))

    const runResult = (option) => {
        if (!option) return
        if (option.kind === "ASK_AKTO") {
            onAsk(option.query)
            return
        }
        if (option.kind === "PROMPT") {
            onAsk(option.query)
            return
        }
        applyNavigationSideEffects(option)
        onOpenRoute(option.route, option.params)
    }

    const handleKeyDown = (e) => {
        if (!hasQuery) return
        if (e.key === "ArrowDown") {
            e.preventDefault()
            setActive((a) => Math.min(a + 1, results.length - 1))
        } else if (e.key === "ArrowUp") {
            e.preventDefault()
            setActive((a) => Math.max(a - 1, 0))
        } else if (e.key === "Enter") {
            e.preventDefault()
            runResult(results[clampedActive])
        }
    }

    const prompts = SUGGESTED_PROMPTS_BY_DOMAIN[domain] || []

    return (
        <>
            <div className="ask-input-row">
                <span className="ask-input-icon"><span className="ask-input-icon-dot" /></span>
                <input
                    ref={inputRef}
                    className="ask-home-input"
                    value={query}
                    onChange={(e) => { setQuery(e.target.value); setActive(0) }}
                    onKeyDown={handleKeyDown}
                    placeholder="What do you want to look into?"
                    aria-label="Ask Akto or jump to a page"
                    role="combobox"
                    aria-expanded={hasQuery}
                    aria-controls="ask-results-listbox"
                    aria-activedescendant={hasQuery ? `ask-result-${clampedActive}` : undefined}
                    autoComplete="off"
                />
                <span className="ask-kbd">esc</span>
            </div>

            <div className="ask-body">
                {hasQuery ? (
                    <div id="ask-results-listbox" role="listbox">
                        <ResultsList results={results} active={clampedActive} onSelect={runResult} onHover={setActive} />
                    </div>
                ) : (
                    <>
                        <RecommendationTiles
                            tiles={tiles}
                            loading={tilesLoading}
                            error={tilesError}
                            onAsk={onAsk}
                            onRetry={onRetryTiles}
                        />
                        <VerticalStack gap="1">
                            <Text variant="headingSm" as="h3">Try asking</Text>
                            {prompts.map((p) => (
                                <button key={p} type="button" className="ask-prompt-row" onClick={() => onAsk(p)}>
                                    <span className="ask-prompt-icon"><Icon source={AnalyticsMajor} /></span>
                                    <span className="ask-prompt-text">{p}</span>
                                    <span className="ask-chevron"><Icon source={ChevronRightMinor} /></span>
                                </button>
                            ))}
                        </VerticalStack>
                    </>
                )}
            </div>

            <div className="ask-footer">
                <span className="ask-footer-hint"><span className="ask-kbd">↑↓</span>Move</span>
                <span className="ask-footer-hint"><span className="ask-kbd">↵</span>Ask or open</span>
                <span className="ask-footer-hint"><span className="ask-kbd">esc</span>Close</span>
                <span className="ask-spacer" />
                <span>Nothing changes without your confirmation</span>
            </div>
        </>
    )
}
