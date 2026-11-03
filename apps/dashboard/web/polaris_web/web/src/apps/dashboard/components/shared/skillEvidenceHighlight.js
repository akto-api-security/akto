// ─── Skill evidence highlighting ─────────────────────────────────────────────
// Cyborg ingests a skill with skill_annotations: one entry per quoted line of evidence, with
// start/end offsets (UTF-16, end exclusive) into skill_content. rehypeSkillEvidence wraps exactly
// those characters of the *rendered* markdown in <mark data-evidence="i">, so a highlight sits on
// the text the reader sees — in a paragraph, a list item, a heading or a code block alike.
//
// Every text node the markdown parser emits remembers where it came from in the source. A node's
// text usually equals its source slice (plain text), or sits inside it (inline code, whose source
// carries the backticks); a code block's text has no position of its own but sits inside its
// parent's. In each case the node maps one to one onto the source and an offset range converts
// directly. The rare node that does not (a paragraph's soft-wrapped lines are trimmed) falls back
// to finding the quoted characters in the node's text.

// The offsets to highlight in `markdown`, which starts with `contentOffset` characters of its own
// (the "# name / description" header) before skill_content. Only SKILL.md entries carry offsets:
// the scripts a skill runs are not ingested, so their evidence is listed instead of highlighted.
export function evidenceRanges(annotations, contentOffset = 0) {
    return (annotations || [])
        .map((a, index) => ({ a, index }))
        .filter(({ a }) => Number.isInteger(a?.start) && Number.isInteger(a?.end) && a.end > a.start)
        .map(({ a, index }) => ({ start: a.start + contentOffset, end: a.end + contentOffset, index }))
        .sort((x, y) => x.start - y.start)
}

export function rehypeSkillEvidence({ source, ranges }) {
    return (tree) => {
        if (!source || !ranges?.length) return
        highlightChildren(tree, source, ranges)
    }
}

function highlightChildren(node, source, ranges) {
    if (!node.children) return
    const out = []
    for (const child of node.children) {
        if (child.type === 'text') {
            out.push(...splitText(child, node, source, ranges))
        } else {
            highlightChildren(child, source, ranges)
            out.push(child)
        }
    }
    node.children = out
}

function offsetsOf(node) {
    const start = node?.position?.start?.offset
    const end = node?.position?.end?.offset
    return Number.isInteger(start) && Number.isInteger(end) ? { start, end } : null
}

function splitText(text, parent, source, ranges) {
    const value = text.value || ''
    const where = offsetsOf(text) || offsetsOf(parent)
    if (!value || !where) return [text]

    const overlapping = ranges.filter(r => r.start < where.end && r.end > where.start)
    if (!overlapping.length) return [text]

    const slice = source.slice(where.start, where.end)
    const at = slice === value ? 0 : slice.indexOf(value)

    // Local [from, to) inside value for each overlapping range; the ranges may overlap one another.
    const pieces = []
    let searchFrom = 0
    for (const r of overlapping) {
        let from, to
        if (at >= 0) {
            const base = where.start + at
            from = Math.max(r.start, base) - base
            to = Math.min(r.end, base + value.length) - base
        } else {
            const quoted = source.slice(Math.max(r.start, where.start), Math.min(r.end, where.end)).trim()
            from = quoted ? value.indexOf(quoted, searchFrom) : -1
            to = from + quoted.length
        }
        if (from < 0 || to <= from || to > value.length) continue
        pieces.push({ from, to, index: r.index })
        searchFrom = from
    }
    if (!pieces.length) return [text]

    // Cut at every piece's edges, so text that several pieces cover becomes one mark naming all of them
    // (data-evidence="2 5") rather than losing all but the first.
    const cuts = [...new Set([0, value.length, ...pieces.flatMap(p => [p.from, p.to])])].sort((x, y) => x - y)
    const out = []
    for (let i = 0; i + 1 < cuts.length; i++) {
        const from = cuts[i], to = cuts[i + 1]
        const segment = { type: 'text', value: value.slice(from, to) }
        const indices = pieces.filter(p => p.from <= from && p.to >= to).map(p => p.index)
        out.push(indices.length
            ? { type: 'element', tagName: 'mark', properties: { dataEvidence: indices.join(' ') }, children: [segment] }
            : segment)
    }
    return out
}
