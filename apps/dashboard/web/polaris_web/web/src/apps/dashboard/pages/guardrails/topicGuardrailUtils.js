import func from "@/util/func";
import TOPIC_CATALOG from "./topicCatalog";

export const CONVERSATION_ORIGIN = "CONVERSATION";

// Guardrails page route — navigation target for the "Create guardrail" action on
// SessionFlyout/DeviceFlyout, carrying the prefill via router location state.
export const GUARDRAIL_POLICIES_PATH = "/dashboard/guardrails/policies";

const MAX_PHRASES = 3;

// Matches the Name field's own limit (PolicyDetailsStep.jsx helpText).
const MAX_NAME_LENGTH = 50;

const DEFAULT_BLOCKED_MESSAGE = "This request has been blocked as it relates to a restricted topic.";

export function normalizeTopicHierarchy(topicHierarchy) {
    const normalized = {};
    Object.entries(topicHierarchy || {}).forEach(([domain, subTopics]) => {
        if (Array.isArray(subTopics)) {
            normalized[domain] = subTopics.filter(Boolean);
        } else if (subTopics && typeof subTopics === "object") {
            normalized[domain] = Object.keys(subTopics);
        } else {
            normalized[domain] = [];
        }
    });
    return normalized;
}

function naturalJoin(labels) {
    if (labels.length === 0) return "";
    if (labels.length === 1) return labels[0];
    if (labels.length === 2) return `${labels[0]} and ${labels[1]}`;
    return `${labels.slice(0, -1).join(", ")}, and ${labels[labels.length - 1]}`;
}

function buildDomainDeniedTopic(domain, observedSubDomains) {
    const entry = TOPIC_CATALOG[domain];
    const matchedSubTopics = observedSubDomains
        .map(sd => entry?.subTopics?.[sd])
        .filter(Boolean)
        .slice(0, MAX_PHRASES);

    const labels = matchedSubTopics.map(st => st.label).filter(Boolean);
    const description = labels.length === 0
        ? `Requests/Messages regarding ${func.toSentenceCase(domain)}`
        : `Requests/Messages regarding ${func.toSentenceCase(domain)} about ${naturalJoin(labels)}`;

    const phrases = [];
    matchedSubTopics.forEach(st => {
        if (phrases.length < MAX_PHRASES && st.samplePhrases?.[0]) {
            phrases.push(st.samplePhrases[0]);
        }
    });
    (entry?.samplePhrases || []).forEach(p => {
        if (phrases.length < MAX_PHRASES && !phrases.includes(p)) {
            phrases.push(p);
        }
    });

    return { topic: domain, description, samplePhrases: phrases, origin: CONVERSATION_ORIGIN };
}

export function buildDeniedTopicsFromHierarchy(topicHierarchy) {
    const normalized = normalizeTopicHierarchy(topicHierarchy);
    return Object.entries(normalized).map(([domain, subDomains]) => buildDomainDeniedTopic(domain, subDomains));
}

export function buildSuggestedPolicyName(domains) {
    const labels = domains.map(d => func.toSentenceCase(d));
    for (let count = labels.length; count > 1; count--) {
        const remaining = labels.length - count;
        const name = `Topic - ${labels.slice(0, count).join(", ")}` + (remaining > 0 ? ` +${remaining}` : "");
        if (name.length <= MAX_NAME_LENGTH) return name;
    }
    // Even a single domain name doesn't fit — return it as-is rather than mangle it.
    return `Topic - ${labels[0] || ""}`;
}

export function buildTopicGuardrailPrefill(topicHierarchy) {
    const deniedTopics = buildDeniedTopicsFromHierarchy(topicHierarchy);
    const name = buildSuggestedPolicyName(deniedTopics.map(dt => dt.topic));
    return {
        name,
        deniedTopics,
        blockedMessage: DEFAULT_BLOCKED_MESSAGE,
        applyOnRequest: true,
        applyOnResponse: true,
    };
}
