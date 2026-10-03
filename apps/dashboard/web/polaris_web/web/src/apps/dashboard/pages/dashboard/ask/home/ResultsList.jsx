import { Icon, Text, VerticalStack } from "@shopify/polaris"
import { AnalyticsMajor, HomeMajor, SearchMinor } from "@shopify/polaris-icons"

// The typed-query results list — design_handoff_ask_akto_overlay/README.md, "3. Home view
// (typing)". Renders exactly the order resolveCommand.buildResults() returns: "Ask Akto: …"
// always first and selected by default, then "Go to" pages, then "Suggested questions". Section
// headers come from each row's own `header` field (set on the first row of each group), not a
// separate grouping pass here — keeping the list flat is what makes `active` a single array
// index instead of a (group, index) pair.
const ICONS = { ASK_AKTO: AnalyticsMajor, PAGE: HomeMajor, PROMPT: SearchMinor }

export default function ResultsList({ results, active, onSelect, onHover }) {
    return (
        <VerticalStack gap="0">
            {results.map((r, i) => (
                <div key={r.id}>
                    {r.header ? <div className="ask-result-header">{r.header}</div> : null}
                    <button
                        type="button"
                        id={`ask-result-${i}`}
                        role="option"
                        aria-selected={i === active}
                        className="ask-result-row"
                        data-active={i === active}
                        onClick={() => onSelect(r)}
                        onMouseEnter={() => onHover(i)}
                    >
                        <span className={`ask-result-icon${r.kind === "ASK_AKTO" ? " ask-result-icon-ask" : ""}`}>
                            <Icon source={ICONS[r.kind] || HomeMajor} />
                        </span>
                        <span className="ask-result-label">
                            <Text variant="bodyMd" as="span" fontWeight={r.kind === "ASK_AKTO" ? "semibold" : "regular"} truncate>
                                {r.label}
                            </Text>
                            <Text variant="bodySm" as="span" color="subdued">
                                {r.kind === "PROMPT" ? "Suggested question" : (r.sub || r.breadcrumb || "")}
                            </Text>
                        </span>
                        {i === active ? <span className="ask-kbd">↵</span> : null}
                    </button>
                </div>
            ))}
        </VerticalStack>
    )
}
