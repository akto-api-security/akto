import func from "@/util/func";
import PersistStore from "@/apps/main/PersistStore";
import TOPIC_CATALOG from "./topicCatalog";
import guardrailApi from "./api";

export const CONVERSATION_ORIGIN = "CONVERSATION";

export const GUARDRAIL_POLICIES_PATH = "/dashboard/guardrails/policies";

const MAX_PHRASES = 3;

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

export function buildSuggestedPolicyName(domains) {
    const labels = domains.map(d => func.toSentenceCase(d));
    for (let count = labels.length; count > 1; count--) {
        const remaining = labels.length - count;
        const name = `Topic - ${labels.slice(0, count).join(", ")}` + (remaining > 0 ? ` +${remaining}` : "");
        if (name.length <= MAX_NAME_LENGTH) return name;
    }
    return `Topic - ${labels[0] || ""}`;
}

export function buildTopicGuardrailPrefillForTopic(topic, topicHierarchy) {
    const normalized = normalizeTopicHierarchy(topicHierarchy);
    const deniedTopic = buildDomainDeniedTopic(topic, normalized[topic] || []);
    return {
        name: buildSuggestedPolicyName([topic]),
        deniedTopics: [deniedTopic],
        blockedMessage: DEFAULT_BLOCKED_MESSAGE,
        applyOnRequest: true,
        applyOnResponse: true,
    };
}

const POLICY_FETCH_PAGE_SIZE = 50;
const MAX_POLICY_FETCH_PAGES = 40;
const POLICY_NAMES_CACHE_TTL_MS = 2 * 60 * 1000;

async function fetchGuardrailPolicyNames() {
    let skip = 0;
    let names = [];
    for (let page = 0; page < MAX_POLICY_FETCH_PAGES; page++) {
        const resp = await guardrailApi.fetchGuardrailPolicies({ skip, limit: POLICY_FETCH_PAGE_SIZE });
        const batch = resp?.guardrailPolicies || [];
        names = names.concat(batch.filter(p => p.active).map(p => p.name));
        if (batch.length < POLICY_FETCH_PAGE_SIZE) break;
        skip += POLICY_FETCH_PAGE_SIZE;
    }
    return names;
}

export async function fetchGuardrailPolicyNamesCached() {
    const { guardrailPolicyNames, setGuardrailPolicyNames } = PersistStore.getState();
    if (Date.now() - guardrailPolicyNames.ts < POLICY_NAMES_CACHE_TTL_MS) {
        return guardrailPolicyNames.data;
    }
    const names = await fetchGuardrailPolicyNames();
    setGuardrailPolicyNames(names);
    return names;
}

export function addCreatedGuardrailPolicyName(name) {
    const { guardrailPolicyNames, setGuardrailPolicyNames } = PersistStore.getState();
    if (!guardrailPolicyNames.data.includes(name)) {
        setGuardrailPolicyNames([...guardrailPolicyNames.data, name]);
    }
}

export function clearGuardrailPolicyNamesCache() {
    PersistStore.getState().clearGuardrailPolicyNames();
}

export function findExistingPolicyPerTopic(policyNames, topics) {
    const nameSet = new Set(policyNames);
    const map = {};
    topics.forEach(topic => {
        const expectedName = buildSuggestedPolicyName([topic]);
        if (nameSet.has(expectedName)) map[topic] = expectedName;
    });
    return map;
}
