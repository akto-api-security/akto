import { BLUEPRINTS, SCOPE_COUNTS } from './api/fixtures';
import { computeImpact } from './api/guardrailPolicyApi';
import {
  GuardrailPolicyDraft,
  NavGroup,
  WizardStep,
} from './types';

export const NAME_PATTERN = /^[A-Za-z0-9_-]{1,50}$/;

export const STEP_PATHS: Record<WizardStep, string> = {
  blueprint: '/dashboard/guardrails/policies/new/blueprint',
  protections: '/dashboard/guardrails/policies/new/protections',
  scope: '/dashboard/guardrails/policies/new/scope',
};

export function stepFromPath(pathname: string): WizardStep {
  if (pathname.includes('/protections')) return 'protections';
  if (pathname.includes('/scope')) return 'scope';
  return 'blueprint';
}

export function getBlueprint(draft: GuardrailPolicyDraft) {
  return BLUEPRINTS.find((b) => b.id === draft.blueprintId) ?? BLUEPRINTS[0];
}

export function getStrengthLabel(strength: number): 'Strong' | 'Good' | 'Weak' {
  if (strength >= 70) return 'Strong';
  if (strength >= 50) return 'Good';
  return 'Weak';
}

export function validateName(name: string): string | null {
  if (!name.trim()) return 'Name is required';
  if (!NAME_PATTERN.test(name)) return 'Letters, numbers, _ and - only (max 50)';
  return null;
}

export function validateBlockedMessage(message: string): string | null {
  if (!message.trim()) return 'Blocked message is required';
  return null;
}

export function validateInspect(draft: GuardrailPolicyDraft): string | null {
  if (!draft.inspect.requests && !draft.inspect.responses) {
    return 'Select at least Requests or Responses';
  }
  return null;
}

export function isDraftValid(draft: GuardrailPolicyDraft): boolean {
  return (
    !validateName(draft.name) &&
    !validateBlockedMessage(draft.blockedMessage) &&
    !validateInspect(draft)
  );
}

export function countEnabledProtections(draft: GuardrailPolicyDraft): number {
  const p = draft.protections;
  return [
    p.promptInjection.enabled,
    p.contextPoisoning.enabled,
    p.deniedTopics.enabled,
    p.harmfulCategories.enabled,
  ].filter(Boolean).length;
}

export function isStagedActive(draft: GuardrailPolicyDraft): boolean {
  return draft.stagedRollout && draft.mode !== 'alert';
}

export function getModeLabel(mode: GuardrailPolicyDraft['mode']): string {
  return { block: 'Block', redact: 'Redact & allow', alert: 'Alert only' }[mode];
}

export function getCtaLabel(draft: GuardrailPolicyDraft): string {
  return isStagedActive(draft) ? 'Create & start rollout' : 'Create & activate';
}

export function getScopeSummary(draft: GuardrailPolicyDraft): string {
  const assets = draft.scope.assets === 'all' ? 'All assets' : 'Selected assets';
  const users = draft.scope.users === 'all' ? 'all 149 users' : 'selected users';
  const accounts = draft.scope.accounts === 'all' ? 'all accounts' : 'personal accounts';
  return `${assets} · ${users} · ${accounts}`;
}

export function getActionSummary(draft: GuardrailPolicyDraft): string {
  const modeLabel = getModeLabel(draft.mode);
  return isStagedActive(draft) ? `Alert only for 7 days, then ${modeLabel}` : modeLabel;
}

export function getInspectSummary(draft: GuardrailPolicyDraft): string {
  const parts = [
    draft.inspect.requests ? 'Requests' : null,
    draft.inspect.responses ? 'Responses' : null,
  ].filter(Boolean);
  return parts.length ? parts.join(' and ') : 'Nothing selected';
}

export function getProtectionsSummary(draft: GuardrailPolicyDraft): string {
  const parts: string[] = [];
  const p = draft.protections;
  if (p.promptInjection.enabled) {
    parts.push(`Prompt injection (${p.promptInjection.sensitivity})`);
  }
  if (p.contextPoisoning.enabled) parts.push('context poisoning');
  if (p.deniedTopics.enabled) {
    parts.push(`${p.deniedTopics.topics.length} denied topics`);
  }
  if (p.harmfulCategories.enabled) {
    const active = Object.values(p.harmfulCategories.thresholds).filter((v) => v !== 'Off').length;
    parts.push(`${active} harmful categories`);
  }
  if (draft.blueprintId === 'chat') {
    parts.push('PII redaction', 'toxicity');
  }
  return parts.length ? parts.join(', ') : 'None configured';
}

export function getInScopeCounts(draft: GuardrailPolicyDraft) {
  const assets = SCOPE_COUNTS.assets[draft.scope.assets === 'all' ? 'all' : 'selected'];
  const users = SCOPE_COUNTS.users[draft.scope.users === 'all' ? 'all' : 'selected'];
  return { ...assets, users };
}

export function getRolloutDates(now = new Date()) {
  const digest = new Date(now);
  digest.setDate(digest.getDate() + 5);
  const enforce = new Date(now);
  enforce.setDate(enforce.getDate() + 6);
  const fmt = (d: Date) =>
    d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  return {
    today: 'Today',
    digest: fmt(digest),
    enforce: fmt(enforce),
  };
}

export function getHeaderTitle(draft: GuardrailPolicyDraft, step: WizardStep): string {
  if (step === 'blueprint') return 'Create guardrail policy';
  return draft.name.trim() || 'Create guardrail policy';
}

export function getHeaderSubtitle(draft: GuardrailPolicyDraft, step: WizardStep): string {
  if (step === 'blueprint') return 'Draft · autosaved just now';
  const bp = getBlueprint(draft);
  return `Draft · ${bp.title} blueprint · autosaved just now`;
}

export function getProgress(step: WizardStep, draft: GuardrailPolicyDraft) {
  if (step === 'scope' && isDraftValid(draft)) {
    return { label: 'Ready to create', pct: 100, complete: true };
  }
  if (step === 'scope') return { label: '6 of 14 sections', pct: 43, complete: false };
  if (step === 'protections') return { label: '5 of 14 sections', pct: 36, complete: false };
  return { label: '4 of 14 sections', pct: 29, complete: false };
}

function protectionSubtitle(draft: GuardrailPolicyDraft): string {
  const n = countEnabledProtections(draft);
  if (n === 0) return 'Prompt attacks, context, topics';
  return `${n} protection${n === 1 ? '' : 's'} on`;
}

function deploySubtitle(draft: GuardrailPolicyDraft): string {
  const assets = draft.scope.assets === 'all' ? 'All assets' : 'Selected assets';
  return `${assets} · ${getModeLabel(draft.mode)}`;
}

export function getNavGroups(draft: GuardrailPolicyDraft, step: WizardStep): NavGroup[] {
  const bp = getBlueprint(draft);
  const detailsSub = draft.name.trim()
    ? `${draft.name} · ${draft.severity}`
    : bp.title;
  const contentSub =
    step === 'scope'
      ? protectionSubtitle(draft)
      : 'Prompt attacks, context, topics';

  const blueprintState =
    step === 'blueprint' ? 'current' : step === 'protections' || step === 'scope' ? 'done' : 'todo';
  const contentState =
    step === 'protections' ? 'current' : step === 'scope' ? 'done' : step === 'blueprint' ? 'set' : 'todo';
  const deployState = step === 'scope' ? 'current' : 'set';

  const langState = step === 'scope' ? 'done' : 'set';
  const sensState = step === 'scope' ? 'done' : 'set';

  return [
    {
      title: 'Setup',
      items: [
        {
          id: 'blueprint',
          label: 'Blueprint & details',
          sub: detailsSub,
          state: blueprintState,
          href: STEP_PATHS.blueprint,
          navigable: true,
        },
      ],
    },
    {
      title: 'Protections',
      items: [
        {
          id: 'content',
          label: 'Content & policy',
          sub: contentSub,
          state: contentState,
          href: STEP_PATHS.protections,
          navigable: true,
        },
        {
          id: 'language',
          label: 'Language safety & abuse',
          sub: step === 'scope' ? 'Toxicity · from blueprint' : 'Toxicity · from blueprint',
          state: langState,
          navigable: false,
        },
        {
          id: 'sensitive',
          label: 'Sensitive information',
          sub: step === 'scope' ? 'PII redaction · 22 types' : 'PII redaction · from blueprint',
          state: sensState,
          navigable: false,
        },
        { id: 'code', label: 'Advanced code detection', state: 'todo', navigable: false },
        { id: 'custom', label: 'Custom guardrails', state: 'todo', navigable: false },
      ],
    },
    {
      title: 'Agent controls',
      items: [
        {
          id: 'tools',
          label: 'Tools guardrails',
          sub: 'Recommended · ASI02',
          state: 'todo',
          navigable: false,
        },
        { id: 'rogue', label: 'Rogue agents', state: 'todo', navigable: false },
        { id: 'usage', label: 'Usage-based', state: 'todo', navigable: false },
        { id: 'anomaly', label: 'Anomaly detection', state: 'todo', navigable: false },
      ],
    },
    {
      title: 'Compliance',
      items: [
        { id: 'license', label: 'Enterprise license compliance', state: 'todo', navigable: false },
        { id: 'access', label: 'Access restrictions', state: 'todo', navigable: false },
        { id: 'exceptions', label: 'Exceptions', sub: 'Beta', state: 'todo', navigable: false },
      ],
    },
    {
      title: 'Deploy',
      items: [
        {
          id: 'scope',
          label: 'Scope, rollout & review',
          sub: step === 'scope' ? undefined : deploySubtitle(draft),
          state: deployState,
          href: STEP_PATHS.scope,
          navigable: true,
        },
      ],
    },
  ];
}

export function renderBlockedPreview(message: string, name: string): string {
  return message
    .replace(/\{policy_name\}/g, name.trim() || 'policy')
    .replace(/\{event_id\}/g, 'evt_8F2K')
    .replace(/\{reason\}/g, 'prompt injection');
}

export function getImpact(draft: GuardrailPolicyDraft) {
  return computeImpact(draft);
}
