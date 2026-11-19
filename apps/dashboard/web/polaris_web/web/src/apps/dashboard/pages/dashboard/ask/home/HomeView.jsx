import { useMemo, useState } from "react"
import { Box, Divider, HorizontalStack, Icon, Text, TextField, VerticalStack } from "@shopify/polaris"
import { AnalyticsMajor, ChevronRightMinor } from "@shopify/polaris-icons"
import RecommendationTiles from "./RecommendationTiles"
import ResultsList, { RESULTS_LISTBOX_ID, resultOptionId } from "./ResultsList"
import Pressable from "../components/Pressable"
import ScrollArea from "../components/ScrollArea"
import KeyHint from "../components/KeyHint"
import IconTile from "../components/IconTile"
import Dot from "../components/Dot"
import { buildResults } from "../palette/resolveCommand"
import { applyNavigationSideEffects } from "../palette/paletteHelpers"
import { suggestedPrompts } from "../palette/commandRegistry"
import useDashboardCategory from "../palette/useDashboardCategory"

// Sheet height min(700px, 100vh - 80px) minus the input row and footer.
const BODY_HEIGHT = "min(595px, calc(100vh - 185px))"

const FOOTER_HINTS = [["↑↓", "Move"], ["↵", "Ask or open"], ["esc", "Close"]]

// The overlay's home surface — design_handoff_ask_akto_overlay/README.md, screens 2 and 3.
// Empty query shows tiles + "Try asking"; a non-empty query shows the typed-results list,
// arrow-key navigable, Enter running the active row. Mounted fresh on every open, so the query
// starts empty and the input autofocuses.
export default function HomeView({ tiles, tilesLoading, tilesError, onRetryTiles, onAsk, onOpenRoute, onClose }) {
    const [query, setQuery] = useState("")
    const [active, setActive] = useState(0)
    const category = useDashboardCategory()

    const results = useMemo(() => buildResults(query, category), [query, category])
    const hasQuery = query.trim().length > 0
    const clampedActive = Math.min(active, Math.max(results.length - 1, 0))

    const runResult = (option) => {
        if (!option) return
        if (option.kind === "ASK_AKTO" || option.kind === "PROMPT") {
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

    const handleQueryChange = (value) => {
        setQuery(value)
        setActive(0)
    }

    const prompts = suggestedPrompts(category)

    return (
        <>
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="3" paddingBlockEnd="3" onKeyDown={handleKeyDown}>
                <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                    <Box background="bg-primary-subdued-hover" borderRadius="full" padding="3">
                        <Dot background="bg-primary" />
                    </Box>
                    <Box width="100%">
                        <TextField
                            label="Ask Akto or jump to a page"
                            labelHidden
                            borderless
                            autoFocus
                            autoComplete="off"
                            placeholder="What do you want to look into?"
                            value={query}
                            onChange={handleQueryChange}
                            role="combobox"
                            ariaExpanded={hasQuery}
                            ariaControls={RESULTS_LISTBOX_ID}
                            ariaActiveDescendant={hasQuery ? resultOptionId(clampedActive) : undefined}
                            ariaAutocomplete="list"
                        />
                    </Box>
                    <Pressable onClick={onClose} accessibilityLabel="Close" borderRadius="1">
                        <KeyHint>esc</KeyHint>
                    </Pressable>
                </HorizontalStack>
            </Box>
            <Divider borderColor="border-ask-divider" />

            <ScrollArea height={BODY_HEIGHT}>
                {hasQuery ? (
                    <ResultsList results={results} active={clampedActive} onSelect={runResult} onHover={setActive} />
                ) : (
                    <VerticalStack gap="6">
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
                                <Pressable
                                    key={p}
                                    onClick={() => onAsk(p)}
                                    hoverBackground="bg-primary-subdued-hover"
                                    borderRadius="full"
                                    paddingBlockStart="1_5-experimental"
                                    paddingBlockEnd="1_5-experimental"
                                    paddingInlineStart="2"
                                    paddingInlineEnd="2"
                                >
                                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                        <IconTile source={AnalyticsMajor} background="bg-primary-subdued-hover" borderRadius="full" />
                                        <Box width="100%">
                                            <Text as="span" variant="bodyMd">{p}</Text>
                                        </Box>
                                        <Icon source={ChevronRightMinor} color="subdued" />
                                    </HorizontalStack>
                                </Pressable>
                            ))}
                        </VerticalStack>
                    </VerticalStack>
                )}
            </ScrollArea>

            <Divider borderColor="border-ask-divider" />
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="2" paddingBlockEnd="2">
                <HorizontalStack align="space-between" blockAlign="center" gap="4">
                    <HorizontalStack gap="4" blockAlign="center">
                        {FOOTER_HINTS.map(([key, label]) => (
                            <HorizontalStack key={key} gap="1_5-experimental" blockAlign="center">
                                <KeyHint>{key}</KeyHint>
                                <Text as="span" variant="bodySm" color="subdued">{label}</Text>
                            </HorizontalStack>
                        ))}
                    </HorizontalStack>
                    <Text as="span" variant="bodySm" color="subdued">Nothing changes without your confirmation</Text>
                </HorizontalStack>
            </Box>
        </>
    )
}
