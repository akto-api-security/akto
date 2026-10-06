import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import { AnalyticsMajor, HomeMajor, SearchMinor } from "@shopify/polaris-icons"
import Pressable from "../components/Pressable"
import IconTile from "../components/IconTile"
import KeyHint from "../components/KeyHint"

export const RESULTS_LISTBOX_ID = "ask-results-listbox"
export const resultOptionId = (index) => `ask-result-${index}`

const ICONS = { ASK_AKTO: AnalyticsMajor, PAGE: HomeMajor, PROMPT: SearchMinor }

// The typed-query results list — design_handoff_ask_akto_overlay/README.md, "3. Home view
// (typing)". Renders exactly the order resolveCommand.buildResults() returns: "Ask Akto: …"
// first, then "Go to" pages, then "Suggested questions". Section headers come from each row's own
// `header` field, which keeps the list flat so `active` is a single index.
export default function ResultsList({ results, active, onSelect, onHover }) {
    return (
        <Box id={RESULTS_LISTBOX_ID} role="listbox">
            <VerticalStack gap="0">
                {results.map((r, i) => {
                    const isActive = i === active
                    const isAsk = r.kind === "ASK_AKTO"
                    return (
                        <VerticalStack key={r.id} gap="0">
                            {r.header ? (
                                <Box paddingInlineStart="2" paddingBlockStart="3" paddingBlockEnd="1">
                                    <Text as="p" variant="bodySm" fontWeight="semibold" color="subdued">{r.header}</Text>
                                </Box>
                            ) : null}
                            <Box id={resultOptionId(i)} role="option" aria-selected={isActive}>
                                <Pressable
                                    onClick={() => onSelect(r)}
                                    onHover={() => onHover(i)}
                                    background={isActive ? "bg-hover" : undefined}
                                    borderRadius="2"
                                    padding="2"
                                >
                                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                        <IconTile
                                            source={ICONS[r.kind] || HomeMajor}
                                            background={isAsk ? "bg-primary-subdued-hover" : "bg-hover"}
                                            borderRadius="1_5-experimental"
                                        />
                                        <Box width="100%">
                                            <VerticalStack gap="0">
                                                <Text as="span" variant="bodyMd" fontWeight={isAsk ? "semibold" : "regular"} truncate>
                                                    {r.label}
                                                </Text>
                                                <Text as="span" variant="bodySm" color="subdued">
                                                    {r.kind === "PROMPT" ? "Suggested question" : (r.sub || r.breadcrumb || "")}
                                                </Text>
                                            </VerticalStack>
                                        </Box>
                                        {isActive ? <KeyHint>↵</KeyHint> : null}
                                    </HorizontalStack>
                                </Pressable>
                            </Box>
                        </VerticalStack>
                    )
                })}
            </VerticalStack>
        </Box>
    )
}
