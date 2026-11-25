export type BlueprintId = 'chat' | 'code' | 'mcp' | 'blank';
export type Severity = 'Low' | 'Medium' | 'High';
export type Sensitivity = 'Low' | 'Medium' | 'High';
export type HarmLevel = 'Off' | 'Low' | 'Medium' | 'High';
export type HarmCategory = 'Hate' | 'Violence' | 'Sexual' | 'Self-harm' | 'Misconduct' | 'Insults';
export type EnforcementMode = 'block' | 'redact' | 'alert';
export type WizardStep = 'blueprint' | 'protections' | 'scope';
export type NavItemState = 'done' | 'current' | 'set' | 'todo';
export type OwaspTileStatus = 'new' | 'covered' | 'partial' | 'gap';

export interface GuardrailPolicyDraft {
  blueprintId: BlueprintId;
  name: string;
  severity: Severity;
  description: string;
  blockedMessage: string;
  useSameMessageForResponses: boolean;
  protections: {
    promptInjection: { enabled: boolean; sensitivity: Sensitivity };
    contextPoisoning: { enabled: boolean; sensitivity: Sensitivity };
    deniedTopics: { enabled: boolean; topics: string[] };
    harmfulCategories: { enabled: boolean; thresholds: Record<HarmCategory, HarmLevel> };
  };
  scope: {
    assets: 'all' | 'selected';
    users: 'all' | 'selected';
    accounts: 'all' | 'personal';
  };
  mode: EnforcementMode;
  stagedRollout: boolean;
  inspect: { requests: boolean; responses: boolean };
  activateOnCreate: boolean;
}

export interface BlueprintFixture {
  id: BlueprintId;
  initial: string;
  title: string;
  body: string;
  tags: string[];
  recommended: boolean;
  strength: number;
  color: string;
  includes: string[];
  defaults: Pick<GuardrailPolicyDraft, 'protections'>;
}

export interface PlaygroundTest {
  prompt: string;
  verdict: string;
  rule: string;
  detail: string;
  meta: string;
  kind: 'block' | 'warn' | 'allow';
}

export interface PreflightCheck {
  kind: 'ok' | 'warn';
  title: string;
  body: string;
  action?: string;
}

export interface TopicSuggestion {
  label: string;
  count: number;
}

export interface OwaspTile {
  code: string;
  name: string;
  status: OwaspTileStatus;
}

export interface ImpactPreview {
  totalHits: number;
  pct: string;
  fpRisk: 'Low' | 'Medium';
  fpHelp: string;
  breakdown: Array<{ label: string; value: string; widthPct: number }>;
}

export interface NavItem {
  id: string;
  label: string;
  sub?: string;
  state: NavItemState;
  href?: string;
  navigable: boolean;
}

export interface NavGroup {
  title: string;
  items: NavItem[];
}
