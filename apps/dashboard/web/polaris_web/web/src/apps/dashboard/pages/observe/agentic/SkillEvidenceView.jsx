import React, { useCallback, useMemo, useRef } from "react";
import { Badge, Banner, Box, Button, HorizontalStack, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import MarkdownViewer from "../../../components/shared/MarkdownViewer";
import { evidenceRanges, rehypeSkillEvidence } from "../../../components/shared/skillEvidenceHighlight";

// Skill content as the reader sees it, with each line of evidence cyborg kept highlighted in place
// and explained on hover: which tag it backs, the criterion and severity, and why. Evidence from
// the scripts the skill runs is listed below the content, since only SKILL.md is ingested.
//
// evidence: { annotations, verdict, contentOffset } from the ingested sample (see
// parseSkillSample). Without annotations or a malicious verdict this is a plain MarkdownViewer.

const SKILL_FILE = "SKILL.md";
const FLASH_MS = 1600;

// Red for the lines behind the malicious verdict, orange for behaviour the skill's purpose does
// not explain, yellow for behaviour it does.
function toneOf(annotation) {
    if (annotation.malicious) return "critical";
    return annotation.purposeAligned === false ? "warning" : "attention";
}

const TONE_RANK = { critical: 0, warning: 1, attention: 2 };

function worstTone(annotations) {
    return annotations.map(toneOf).sort((x, y) => TONE_RANK[x] - TONE_RANK[y])[0];
}

// A mark's data-evidence lists every annotation behind its text, space separated.
function evidenceIndices(value) {
    return String(value || "").split(" ").filter(Boolean);
}

function whyText(annotation) {
    if (annotation.malicious) return "Backs the malicious verdict";
    return annotation.purposeAligned === false ? "Not explained by the skill's purpose" : "Explained by the skill's purpose";
}

function EvidenceTooltip({ annotation }) {
    return (
        <VerticalStack gap="1">
            <Text variant="bodyMd" fontWeight="semibold">{annotation.tag}</Text>
            <Text variant="bodySm" color="subdued">
                {[annotation.criterion, annotation.severity].filter(Boolean).join(" · ")}
            </Text>
            {annotation.reason && <Text variant="bodySm">{annotation.reason}</Text>}
            <Text variant="bodySm" color="subdued">{whyText(annotation)}</Text>
        </VerticalStack>
    );
}

// One chip per tag, worst tone first, with how many lines back it.
function groupByTag(annotations) {
    const groups = new Map();
    annotations.forEach((a, index) => {
        if (!a?.tag) return;
        const group = groups.get(a.tag) || { tag: a.tag, tone: toneOf(a), indices: [] };
        if (TONE_RANK[toneOf(a)] < TONE_RANK[group.tone]) group.tone = toneOf(a);
        group.indices.push(index);
        groups.set(a.tag, group);
    });
    return [...groups.values()].sort((x, y) => TONE_RANK[x.tone] - TONE_RANK[y.tone]);
}

function VerdictBanner({ verdict }) {
    if (!verdict?.isMalicious) return null;
    const score = Number(verdict.score);
    return (
        <Banner status="critical" title={`Malicious — ${verdict.label || verdict.criterion || "skill"}`}>
            <VerticalStack gap="1">
                {verdict.reason && <Text variant="bodyMd">{verdict.reason}</Text>}
                <Text variant="bodySm" color="subdued">
                    {[verdict.criterion, Number.isFinite(score) ? `risk score ${score.toFixed(2)}` : null]
                        .filter(Boolean).join(" · ")}
                </Text>
            </VerticalStack>
        </Banner>
    );
}

// entries: [{ a, index }], index being the annotation's position, so a tag chip can jump here too
function ScriptEvidence({ entries }) {
    if (!entries.length) return null;
    return (
        <VerticalStack gap="2">
            <Text variant="headingSm">Scripts this skill runs</Text>
            <Text variant="bodySm" color="subdued">Not ingested — the evidence is quoted with its file and line.</Text>
            {entries.map(({ a, index }) => (
                <Box key={index} padding="2" borderColor="border-subdued" borderWidth="1" borderRadius="2">
                    <VerticalStack gap="1">
                        <HorizontalStack gap="2" blockAlign="center">
                            <Badge size="small" status={toneOf(a)}>{a.tag}</Badge>
                            <Text variant="bodySm" color="subdued">{`${a.file} L${a.line}`}</Text>
                        </HorizontalStack>
                        <Box className="violation-evidence-pre">
                            <mark className={`skill-evidence skill-evidence--${toneOf(a)}`} data-evidence={String(index)}>{a.text}</mark>
                        </Box>
                        {a.reason && <Text variant="bodySm">{a.reason}</Text>}
                    </VerticalStack>
                </Box>
            ))}
        </VerticalStack>
    );
}

export default function SkillMarkdownViewer({ markdown, evidence }) {
    const containerRef = useRef(null);
    const annotations = useMemo(() => evidence?.annotations || [], [evidence]);
    const verdict = evidence?.verdict;
    const contentOffset = evidence?.contentOffset || 0;

    const ranges = useMemo(() => evidenceRanges(annotations, contentOffset), [annotations, contentOffset]);
    const rehypePlugins = useMemo(
        () => (ranges.length ? [[rehypeSkillEvidence, { source: markdown, ranges }]] : undefined),
        [markdown, ranges]
    );
    const extraComponents = useMemo(() => ({
        // eslint-disable-next-line no-unused-vars
        mark: ({ node, children, ...props }) => {
            const marked = evidenceIndices(props["data-evidence"]).map(i => annotations[Number(i)]).filter(Boolean);
            if (!marked.length) return <mark>{children}</mark>;
            const tooltip = (
                <VerticalStack gap="3">
                    {marked.map((annotation, i) => <EvidenceTooltip key={i} annotation={annotation} />)}
                </VerticalStack>
            );
            return (
                <Tooltip content={tooltip} preferredPosition="above">
                    <mark className={`skill-evidence skill-evidence--${worstTone(marked)}`} data-evidence={props["data-evidence"]}>
                        {children}
                    </mark>
                </Tooltip>
            );
        },
    }), [annotations]);

    const tags = useMemo(() => groupByTag(annotations), [annotations]);
    const scriptEntries = useMemo(
        () => annotations.map((a, index) => ({ a, index })).filter(({ a }) => a?.file && a.file !== SKILL_FILE),
        [annotations]
    );

    // Scrolls to the tag's first highlight and pulses every highlight that backs it.
    const focusTag = useCallback((group) => {
        const root = containerRef.current;
        if (!root) return;
        const wanted = new Set(group.indices.map(String));
        const marks = [...root.querySelectorAll("mark.skill-evidence[data-evidence]")]
            .filter(m => evidenceIndices(m.getAttribute("data-evidence")).some(i => wanted.has(i)));
        if (!marks.length) return;
        marks[0].scrollIntoView({ behavior: "smooth", block: "center" });
        marks.forEach(m => m.classList.add("skill-evidence--flash"));
        setTimeout(() => marks.forEach(m => m.classList.remove("skill-evidence--flash")), FLASH_MS);
    }, []);

    if (!annotations.length && !verdict?.isMalicious) {
        return <MarkdownViewer markdown={markdown} inertLinks />;
    }

    return (
        <VerticalStack gap="4">
            <VerdictBanner verdict={verdict} />
            {tags.length > 0 && (
                <VerticalStack gap="2">
                    <HorizontalStack gap="2" wrap>
                        {tags.map(group => (
                            <Button key={group.tag} size="slim" plain monochrome removeUnderline onClick={() => focusTag(group)}>
                                <Badge status={group.tone}>{`${group.tag} ×${group.indices.length}`}</Badge>
                            </Button>
                        ))}
                    </HorizontalStack>
                    <Text variant="bodySm" color="subdued">
                        Hover a highlighted line to see which tag it backs and why. Click a tag to jump to its lines.
                    </Text>
                </VerticalStack>
            )}
            <div ref={containerRef}>
                <VerticalStack gap="4">
                    <MarkdownViewer markdown={markdown} inertLinks rehypePlugins={rehypePlugins} extraComponents={extraComponents} />
                    <ScriptEvidence entries={scriptEntries} />
                </VerticalStack>
            </div>
        </VerticalStack>
    );
}
