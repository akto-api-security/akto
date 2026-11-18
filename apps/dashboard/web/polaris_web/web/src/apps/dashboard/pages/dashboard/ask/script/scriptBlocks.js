// Parses the ```akto-script fences an Ask Akto script-writing reply carries
// (test-editor-services skills/test-scripts/SKILL.md, "Output format"). Pure.
//
// A block's first line is a header comment:
//   // @akto-script type=POST_REQUEST collection=123 name=poll-ask-queries
// Everything in the header is model output: type and name are checked here, and the collection id
// is only a default the user can change. The backend checks access to it again on Validate.

import { SCRIPT_TYPES } from "../../../testing/scripts/scriptTypes"

const FENCE = /```akto-script[^\n]*\n([\s\S]*?)```/g
const HEADER = /^\s*\/\/\s*@akto-script\b(.*)$/
const NAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 _.-]*$/
const MAX_NAME_LENGTH = 64
const RESERVED_PREFIXES = ["ai-draft-", "default-test-"]

function parseHeader(line) {
    const match = HEADER.exec(line || "")
    if (!match) return null
    const fields = {}
    for (const part of match[1].trim().split(/\s+/)) {
        const eq = part.indexOf("=")
        if (eq > 0) fields[part.slice(0, eq)] = part.slice(eq + 1)
    }
    return fields
}

/** A name the library would accept, or "" so the user is asked for one. */
export function cleanScriptName(raw) {
    const name = (raw || "").trim().slice(0, MAX_NAME_LENGTH)
    if (!NAME_PATTERN.test(name)) return ""
    if (RESERVED_PREFIXES.some((p) => name.startsWith(p))) return ""
    return name
}

/**
 * Returns { text, blocks }. text is the reply with each fence replaced by a one-line pointer (the
 * card renders the script); blocks are { index, type, apiCollectionId, name, javascript, headerError }.
 * A block whose header is missing or wrong keeps its script and gets headerError, so the user picks
 * the type instead of losing it.
 */
export function parseScriptBlocks(message) {
    const blocks = []
    const text = (message || "").replace(FENCE, (_, body) => {
        const javascript = body.replace(/\s+$/, "")
        const header = parseHeader(javascript.split("\n")[0])
        const type = header && Object.values(SCRIPT_TYPES).includes(header.type) ? header.type : null
        const collection = header && /^-?\d+$/.test(header.collection || "") ? Number(header.collection) : null
        const index = blocks.length
        blocks.push({
            index,
            type,
            apiCollectionId: collection,
            name: cleanScriptName(header?.name),
            javascript,
            headerError: type ? null : "Couldn't read the script type from this block; choose it before validating.",
        })
        return `\n_Script ${index + 1} is shown below._\n`
    })
    return { text, blocks }
}

/** The newest script of each type across the conversation's replies, for pairing pre with post. */
export function latestScriptsByType(messages) {
    const latest = {}
    for (const msg of messages || []) {
        if (msg.role !== "assistant") continue
        for (const block of parseScriptBlocks(msg.message).blocks) {
            if (block.type) latest[block.type] = block
        }
    }
    return latest
}
