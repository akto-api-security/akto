import { Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';
import { ArrowLeftMinor } from '@shopify/polaris-icons';
import RadioCard, { CheckMark, RadioMark } from '../components/RadioCard';
import { useRadioGroupKeys } from '../components/useRadioGroupKeys';
import { useGuardrailDraftStore } from '../draft.store';
import {
  getCtaLabel,
  getInScopeCounts,
  getModeLabel,
  getRolloutDates,
  isDraftValid,
  isStagedActive,
  validateInspect,
} from '../selectors';
import { EnforcementMode } from '../types';

interface ScopeStepProps {
  onPrevious: () => void;
  onCreate: (activate: boolean) => void;
  creating?: boolean;
}

const MODE_DEFS: Array<{
  id: EnforcementMode;
  letter: string;
  label: string;
  sub: string;
}> = [
  { id: 'block', letter: 'B', label: 'Block', sub: 'Stop the prompt or response and show your message' },
  { id: 'redact', letter: 'R', label: 'Redact & allow', sub: 'Mask what matched, let the rest through' },
  { id: 'alert', letter: 'A', label: 'Alert only', sub: 'Allow, log and notify — no user impact' },
];

export default function ScopeStep({ onPrevious, onCreate, creating = false }: ScopeStepProps) {
  const draft = useGuardrailDraftStore((s) => s.draft);
  const setScope = useGuardrailDraftStore((s) => s.setScope);
  const setMode = useGuardrailDraftStore((s) => s.setMode);
  const setStagedRollout = useGuardrailDraftStore((s) => s.setStagedRollout);
  const toggleInspect = useGuardrailDraftStore((s) => s.toggleInspect);

  const counts = getInScopeCounts(draft);
  const staged = isStagedActive(draft);
  const modeLabel = getModeLabel(draft.mode);
  const dates = getRolloutDates();
  const inspectError = validateInspect(draft);
  const canCreate = isDraftValid(draft);
  const alertMode = draft.mode === 'alert';

  const modeValues = MODE_DEFS.map((m) => m.id);
  const onModeKey = useRadioGroupKeys<EnforcementMode>(modeValues, draft.mode, setMode);

  const assetKey = useRadioGroupKeys(
    ['all', 'selected'],
    draft.scope.assets,
    (v) => setScope('assets', v)
  );
  const userKey = useRadioGroupKeys(
    ['all', 'selected'],
    draft.scope.users,
    (v) => setScope('users', v)
  );
  const accountKey = useRadioGroupKeys(
    ['all', 'personal'],
    draft.scope.accounts,
    (v) => setScope('accounts', v)
  );

  return (
    <VerticalStack gap="5">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div className="gw-step-kicker">Step 3 of 3 · Deploy</div>
        <h2 className="gw-step-title">Scope, rollout &amp; review</h2>
        <p className="gw-step-desc" style={{ margin: 0 }}>
          Choose where this policy runs and how it enforces. Atlas flags conflicts with your existing policies before anything goes live.
        </p>
      </div>

      <section className="gw-card is-compact">
        <VerticalStack gap="4">
          <Text as="h3" variant="headingSm">Where it applies</Text>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Text as="span" variant="bodyMd" fontWeight="semibold">Agentic assets</Text>
            <div
              role="radiogroup"
              aria-label="Agentic assets"
              onKeyDown={assetKey}
              style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10 }}
            >
              <RadioCard
                className="is-horizontal"
                selected={draft.scope.assets === 'all'}
                onSelect={() => setScope('assets', 'all')}
                tabIndex={draft.scope.assets === 'all' ? 0 : -1}
              >
                <RadioMark selected={draft.scope.assets === 'all'} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600 }}>
                    All assets
                    <span className="gw-rec-pill">Recommended</span>
                  </span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    10 agents, 230 MCP servers and 4 LLMs — new ones join automatically
                  </span>
                </span>
              </RadioCard>
              <RadioCard
                className="is-horizontal"
                selected={draft.scope.assets === 'selected'}
                onSelect={() => setScope('assets', 'selected')}
                tabIndex={draft.scope.assets === 'selected' ? 0 : -1}
              >
                <RadioMark selected={draft.scope.assets === 'selected'} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>Selected assets</span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    Choose specific agents, MCP servers and LLMs
                  </span>
                </span>
              </RadioCard>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Text as="span" variant="bodyMd" fontWeight="semibold">Device tags &amp; users</Text>
            <div
              role="radiogroup"
              aria-label="Device tags & users"
              onKeyDown={userKey}
              style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10 }}
            >
              <RadioCard
                className="is-horizontal"
                selected={draft.scope.users === 'all'}
                onSelect={() => setScope('users', 'all')}
                tabIndex={draft.scope.users === 'all' ? 0 : -1}
              >
                <RadioMark selected={draft.scope.users === 'all'} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600 }}>
                    Everyone
                    <span className="gw-rec-pill">Recommended</span>
                  </span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    All 149 users in your organization
                  </span>
                </span>
              </RadioCard>
              <RadioCard
                className="is-horizontal"
                selected={draft.scope.users === 'selected'}
                onSelect={() => setScope('users', 'selected')}
                tabIndex={draft.scope.users === 'selected' ? 0 : -1}
              >
                <RadioMark selected={draft.scope.users === 'selected'} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>Selected tags &amp; users</span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    e.g. tag “support-team” or individual users
                  </span>
                </span>
              </RadioCard>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Text as="span" variant="bodyMd" fontWeight="semibold">Work &amp; personal accounts</Text>
              <span className="gw-beta-pill">Beta</span>
            </div>
            <div
              role="radiogroup"
              aria-label="Work & personal accounts"
              onKeyDown={accountKey}
              style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10 }}
            >
              <RadioCard
                className="is-horizontal"
                selected={draft.scope.accounts === 'all'}
                onSelect={() => setScope('accounts', 'all')}
                tabIndex={draft.scope.accounts === 'all' ? 0 : -1}
              >
                <RadioMark selected={draft.scope.accounts === 'all'} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600 }}>
                    All accounts
                    <span className="gw-rec-pill">Recommended</span>
                  </span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    Work and personal sign-ins to AI tools
                  </span>
                </span>
              </RadioCard>
              <RadioCard
                className="is-horizontal"
                selected={draft.scope.accounts === 'personal'}
                onSelect={() => setScope('accounts', 'personal')}
                tabIndex={draft.scope.accounts === 'personal' ? 0 : -1}
              >
                <RadioMark selected={draft.scope.accounts === 'personal'} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>Personal accounts only</span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    Work accounts are excluded
                  </span>
                </span>
              </RadioCard>
            </div>
          </div>

          <div className="gw-scope-summary">
            <span style={{ fontSize: 12, color: 'var(--gw-muted)', width: '100%' }}>In scope right now</span>
            <span className="gw-scope-chip"><strong>{counts.agents}</strong> agents</span>
            <span className="gw-scope-chip"><strong>{counts.mcp}</strong> MCP servers</span>
            <span className="gw-scope-chip"><strong>{counts.llm}</strong> LLMs</span>
            <span className="gw-scope-chip"><strong>{counts.users}</strong> users</span>
          </div>
        </VerticalStack>
      </section>

      <section className="gw-card is-compact">
        <VerticalStack gap="4">
          <Text as="h3" variant="headingSm">When it triggers</Text>

          <div
            role="radiogroup"
            aria-label="Rule behaviour"
            onKeyDown={onModeKey}
            style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 10 }}
          >
            {MODE_DEFS.map((m) => {
              const selected = draft.mode === m.id;
              return (
                <RadioCard
                  key={m.id}
                  className="is-mode"
                  selected={selected}
                  onSelect={() => setMode(m.id)}
                  tabIndex={selected ? 0 : -1}
                >
                  <span className={`gw-mode-icon ${m.id}`}>{m.letter}</span>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>{m.label}</span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>{m.sub}</span>
                </RadioCard>
              );
            })}
          </div>

          <div className="gw-staged-box">
            <button
              type="button"
              role="switch"
              aria-checked={staged}
              aria-label="Staged rollout"
              aria-disabled={alertMode}
              className={`gw-switch ${staged ? 'is-on' : ''} ${alertMode ? 'is-disabled' : ''}`}
              onClick={() => {
                if (alertMode) return;
                setStagedRollout(!draft.stagedRollout);
              }}
              disabled={alertMode}
            >
              <span className="gw-switch-knob" />
            </button>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
                <span style={{ fontWeight: 600 }}>Staged rollout</span>
                <span className="gw-rec-pill is-accent" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 8px' }}>
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinejoin="round" aria-hidden="true">
                    <path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8z" />
                  </svg>
                  Recommended
                </span>
              </div>
              <div style={{ fontSize: 13, color: 'var(--gw-text-secondary)' }}>
                Run in <strong>Alert only</strong> for 7 days, then switch to <strong>{modeLabel}</strong> automatically if false positives stay under 0.5%. You get a digest before the switch.
              </div>
              {staged && (
                <div className="gw-timeline">
                  <div className="gw-timeline-seg">
                    <span className="gw-timeline-bar" />
                    <span style={{ fontWeight: 600 }}>{dates.today}</span>
                    <span style={{ color: 'var(--gw-muted)' }}>Alert only</span>
                  </div>
                  <div className="gw-timeline-seg">
                    <span className="gw-timeline-bar" />
                    <span style={{ fontWeight: 600 }}>{dates.digest}</span>
                    <span style={{ color: 'var(--gw-muted)' }}>Review digest</span>
                  </div>
                  <div className="gw-timeline-seg">
                    <span className="gw-timeline-bar" />
                    <span style={{ fontWeight: 600 }}>{dates.enforce}</span>
                    <span style={{ color: 'var(--gw-muted)' }}>{modeLabel} enforced</span>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--gw-text-secondary)' }}>Inspect</span>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 10 }}>
              <RadioCard
                className="is-horizontal"
                role="checkbox"
                selected={draft.inspect.requests}
                onSelect={() => toggleInspect('requests')}
              >
                <CheckMark selected={draft.inspect.requests} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>Requests</span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    Check user prompts before the model sees them
                  </span>
                </span>
              </RadioCard>
              <RadioCard
                className="is-horizontal"
                role="checkbox"
                selected={draft.inspect.responses}
                onSelect={() => toggleInspect('responses')}
              >
                <CheckMark selected={draft.inspect.responses} />
                <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>Responses</span>
                  <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>
                    Check model output before users see it
                  </span>
                </span>
              </RadioCard>
            </div>
            {inspectError && (
              <Text as="p" variant="bodySm" color="critical">{inspectError}</Text>
            )}
            <div className="gw-info-note">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 11v6" />
                <path d="M12 7.5v.5" />
              </svg>
              Response inspection isn’t available for browser LLMs; those 2 assets will be checked on requests only.
            </div>
          </div>
        </VerticalStack>
      </section>

      <HorizontalStack align="space-between" blockAlign="center" wrap>
        <Button icon={ArrowLeftMinor} onClick={onPrevious}>Previous</Button>
        <HorizontalStack gap="2">
          <Button
            onClick={() => onCreate(false)}
            loading={creating}
            disabled={!canCreate}
          >
            Save as inactive
          </Button>
          <Button
            primary
            onClick={() => onCreate(true)}
            loading={creating}
            disabled={!canCreate}
          >
            {getCtaLabel(draft)}
          </Button>
        </HorizontalStack>
      </HorizontalStack>
    </VerticalStack>
  );
}
