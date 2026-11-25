import {
  GuardrailPolicyDraft,
  ImpactPreview,
  PlaygroundTest,
} from '../types';
import {
  CONTEXT_POISON_PROMPT,
  HARM_HITS,
  LEVEL_HITS,
  PLAYGROUND_TESTS,
  TOTAL_EVENTS,
} from './fixtures';

const delay = <T,>(value: T, ms = 180): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(value), ms));

export function computeImpact(draft: GuardrailPolicyDraft): ImpactPreview {
  const { protections } = draft;
  const piHits = protections.promptInjection.enabled
    ? LEVEL_HITS.pi[protections.promptInjection.sensitivity]
    : 0;
  const ctxHits = protections.contextPoisoning.enabled
    ? LEVEL_HITS.ctx[protections.contextPoisoning.sensitivity]
    : 0;
  const topicHits = protections.deniedTopics.enabled
    ? protections.deniedTopics.topics.length * 31
    : 0;
  const harmHits = protections.harmfulCategories.enabled
    ? Object.values(protections.harmfulCategories.thresholds).reduce(
        (sum, level) => sum + HARM_HITS[level],
        0
      )
    : 0;

  const rows = [
    { label: 'Prompt injection filter', value: protections.promptInjection.enabled ? piHits : null },
    { label: 'Context poisoning detection', value: protections.contextPoisoning.enabled ? ctxHits : null },
    { label: 'Denied topics', value: protections.deniedTopics.enabled ? topicHits : null },
    { label: 'Harmful categories', value: protections.harmfulCategories.enabled ? harmHits : null },
  ];

  const totalHits = rows.reduce((sum, row) => sum + (row.value ?? 0), 0);
  const maxN = Math.max(1, ...rows.map((row) => row.value ?? 0));
  // Matches Create-Protections.dc.html: ctx High raises FP risk even when the card is off.
  const piHigh = protections.promptInjection.enabled && protections.promptInjection.sensitivity === 'High';
  const ctxHigh = protections.contextPoisoning.sensitivity === 'High';
  const fpRisk = piHigh || ctxHigh ? 'Medium' : 'Low';

  return {
    totalHits,
    pct: `${((totalHits / TOTAL_EVENTS) * 100).toFixed(2)}%`,
    fpRisk,
    fpHelp: fpRisk === 'Low' ? 'Est. under 1 in 2,000 prompts' : 'High sensitivity on: review weekly',
    breakdown: rows.map((row) => ({
      label: row.label,
      value: row.value == null ? 'Off' : row.value.toLocaleString('en-US'),
      widthPct: row.value == null ? 0 : Math.round((row.value / maxN) * 100),
    })),
  };
}

export async function createPolicy(draft: GuardrailPolicyDraft): Promise<{ ok: true; id: string }> {
  console.log('[guardrailPolicyApi.createPolicy]', draft);
  return delay({ ok: true as const, id: `pol_${Date.now().toString(36)}` });
}

export async function previewImpact(draft: GuardrailPolicyDraft): Promise<ImpactPreview> {
  return delay(computeImpact(draft));
}

export async function runPlaygroundTest(
  draft: GuardrailPolicyDraft,
  prompt: string
): Promise<PlaygroundTest> {
  const trimmed = (prompt || '').trim();
  const known = PLAYGROUND_TESTS.find((t) => t.prompt === trimmed);
  if (known) return delay(known);

  if (trimmed.includes('refund') || trimmed === CONTEXT_POISON_PROMPT) {
    const on = draft.protections.contextPoisoning.enabled;
    return delay({
      prompt: trimmed,
      verdict: on ? 'Blocked' : 'Allowed',
      rule: 'Context poisoning detection',
      detail: on
        ? 'A planted instruction in stored memory tried to change refund rules (confidence 0.94). The note was quarantined and the user got your blocked message.'
        : 'Context poisoning detection is off — this planted instruction would reach the model. Turn it on to block.',
      meta: '48 ms · OWASP ASI06',
      kind: on ? 'block' : 'allow',
    });
  }

  return delay({
    prompt: trimmed,
    verdict: 'Allowed',
    rule: 'No matching rule',
    detail: 'No protection matched this prompt against the current draft.',
    meta: '18 ms',
    kind: 'allow',
  });
}

const guardrailPolicyApi = {
  createPolicy,
  previewImpact,
  runPlaygroundTest,
};

export default guardrailPolicyApi;
