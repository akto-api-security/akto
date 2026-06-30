// Builds guardrail "denied topic" objects from conversation topics, seeded with
// real sample phrases pulled from observability (LLM) traffic.
//
// Given a list of topics and a scope (a single session, or a user/device pair),
// this queries the same prompt-search backend the LLM Messages tab uses and
// extracts clean, human-readable prompt text to use as sample phrases.
//
// Pure except for the searchPrompts API call.

import llmApi from "../observe/llm/api";
import { parsePromptText } from "../observe/llm/constants";
import guardrailApi from "./api";

// Canonical policy that collects every topic-derived denied topic (per contextSource).
export const TOPIC_GUARDRAIL_POLICY_NAME = "Akto-Topic-Guardrail";

// How many rows to pull per topic. We only need 3 good phrases, but raw rows can
// contain JSON-blob fallbacks we drop, so we over-fetch a little.
const FETCH_LIMIT = 20;

// Max phrases to keep per topic.
const MAX_PHRASES = 3;

export const CONVERSATION_ORIGIN = "CONVERSATION";

// Translates the caller's `scope` into the searchPrompts param shape.
//   { sessionId }            → top-level sessionId param (Traces scope)
//   { userName, deviceId }   → filters.userName / filters.deviceId (Endpoints scope)
// Returns { sessionId, filters } merged with the per-topic filter.
function scopeToSearchParams(topic, scope) {
    const s = scope || {};
    const filters = { topic: [topic] };
    if (s.userName) filters.userName = [s.userName];
    if (s.deviceId) filters.deviceId = [s.deviceId];
    return {
        sessionId: s.sessionId || "",
        filters,
    };
}

// Extracts up to MAX_PHRASES de-duplicated (case-insensitive) sample phrases
// from a list of searchPrompts rows, using parsePromptText's output as-is.
function extractSamplePhrases(rows) {
    const phrases = [];
    const seen = new Set();
    for (const row of rows) {
        const text = parsePromptText(row.queryPayload) || row._promptText || "";
        const trimmed = (text || "").trim();
        if (!trimmed) continue;
        const key = trimmed.toLowerCase();
        if (seen.has(key)) continue;
        seen.add(key);
        phrases.push(trimmed);
        if (phrases.length >= MAX_PHRASES) break;
    }
    return phrases;
}

/**
 * Builds one denied-topic object per input topic, seeded with sample phrases
 * pulled from observability traffic matching that topic and the given scope.
 *
 * @param {string[]} topics  Topic names to build denied-topic objects for.
 * @param {Object}   scope   One of { sessionId } (Traces) or { userName, deviceId } (Endpoints).
 * @param {Object}   dateRange  { startTime, endTime } in epoch seconds.
 * @returns {Promise<Array<{ topic: string, samplePhrases: string[], origin: string }>>}
 *          One object per input topic (topics with no matching phrases get an
 *          empty samplePhrases array — they are never dropped).
 */
export async function buildDeniedTopicsFromTopics(topics, scope, dateRange) {
    const list = Array.isArray(topics) ? topics : [];
    const { startTime, endTime } = dateRange || {};

    return Promise.all(
        list.map(async (topic) => {
            const { sessionId, filters } = scopeToSearchParams(topic, scope);
            let samplePhrases = [];
            try {
                const { value } = await llmApi.searchPrompts({
                    startTime,
                    endTime,
                    sessionId,
                    filters,
                    limit: FETCH_LIMIT,
                });
                samplePhrases = extractSamplePhrases(value || []);
            } catch (_) {
                samplePhrases = [];
            }
            // Denied topics require a non-empty description to save; seed a sensible default.
            return { topic, description: topic, samplePhrases, origin: CONVERSATION_ORIGIN };
        })
    );
}

// Case-insensitive, trimmed key for a topic name.
function topicKey(topic) {
    return (topic || "").trim().toLowerCase();
}

/**
 * Merges new denied topics into an existing list, deduping by topic name
 * (case-insensitive, trimmed). For a topic already present, its samplePhrases
 * are unioned with the new ones (case-insensitive dedupe) and re-capped at 3.
 * New topics are appended.
 *
 * @returns {{ deniedTopics: Array, addedCount: number }}
 *          deniedTopics is the merged full list; addedCount is the number of
 *          brand-new topics added (used for the success toast).
 */
export function mergeDeniedTopics(existingTopics, newTopics) {
    const merged = (existingTopics || []).map(t => ({ ...t }));
    const indexByKey = new Map();
    merged.forEach((t, i) => indexByKey.set(topicKey(t.topic), i));

    let addedCount = 0;

    (newTopics || []).forEach(incoming => {
        const key = topicKey(incoming.topic);
        if (indexByKey.has(key)) {
            const existing = merged[indexByKey.get(key)];
            const seen = new Set();
            const unioned = [];
            [...(existing.samplePhrases || []), ...(incoming.samplePhrases || [])].forEach(p => {
                const pk = (p || "").trim().toLowerCase();
                if (!pk || seen.has(pk)) return;
                seen.add(pk);
                unioned.push(p);
            });
            existing.samplePhrases = unioned.slice(0, MAX_PHRASES);
        } else {
            const idx = merged.push({ ...incoming }) - 1;
            indexByKey.set(key, idx);
            addedCount += 1;
        }
    });

    return { deniedTopics: merged, addedCount };
}

/**
 * Decides whether to create a fresh canonical topic-guardrail policy or append
 * to the existing one, then performs the append (the create path is delegated
 * to the caller via the returned action so navigation can be handled in React).
 *
 * @param {Array} deniedTopics  denied-topic objects from buildDeniedTopicsFromTopics.
 * @returns {Promise<Object>} one of:
 *   { action: "create", prefill: { name, deniedTopics } }                — no existing policy
 *   { action: "append", addedCount }                                     — appended (or no-op) to existing policy
 */
export async function createOrAppendTopicGuardrail(deniedTopics) {
    const resp = await guardrailApi.fetchGuardrailPolicyByName(TOPIC_GUARDRAIL_POLICY_NAME);
    const existing = resp?.guardrailPolicy;

    if (!existing) {
        return {
            action: "create",
            prefill: {
                name: TOPIC_GUARDRAIL_POLICY_NAME,
                deniedTopics,
                description: "Blocks conversation topics flagged from LLM traffic.",
                severity: "HIGH",
                behaviour: "block",
                blockedMessage: "This action has been blocked for restricted topics.",
                active: true,
                applyOnRequest: true,
                applyOnResponse: true,
                applyToAllServers: true,
            },
        };
    }

    const { deniedTopics: mergedTopics, addedCount } = mergeDeniedTopics(existing.deniedTopics || [], deniedTopics);

    // Server replaces deniedTopics wholesale; send the full existing policy with merged topics.
    const updatedPolicy = { ...existing, deniedTopics: mergedTopics };
    await guardrailApi.createGuardrailPolicy({ policy: updatedPolicy, hexId: existing.hexId });

    return { action: "append", addedCount };
}
