import { useState } from 'react';
import { Button, HorizontalStack, VerticalStack } from '@shopify/polaris';
import { ArrowLeftMinor, ArrowRightMinor } from '@shopify/polaris-icons';
import ToggleCard from '../components/ToggleCard';
import SegmentedControl from '../components/SegmentedControl';
import ThresholdButton from '../components/ThresholdButton';
import { STEP_CONFIG } from '../../components/owaspConfig';
import { HARM_HITS, LEVEL_HELP, LEVEL_HITS, TOPIC_SUGGESTIONS } from '../api/fixtures';
import { useGuardrailDraftStore } from '../draft.store';
import { HarmCategory, Sensitivity } from '../types';

const CONTENT_OWASP =
  STEP_CONFIG.find((s) => s.stepNumber === 2)?.owaspThreats ?? [];

interface ProtectionsStepProps {
  onPrevious: () => void;
  onNext: () => void;
}

const HARM_ORDER: HarmCategory[] = [
  'Hate',
  'Violence',
  'Sexual',
  'Self-harm',
  'Misconduct',
  'Insults',
];

export default function ProtectionsStep({ onPrevious, onNext }: ProtectionsStepProps) {
  const draft = useGuardrailDraftStore((s) => s.draft);
  const toggleProtection = useGuardrailDraftStore((s) => s.toggleProtection);
  const setSensitivity = useGuardrailDraftStore((s) => s.setSensitivity);
  const addTopic = useGuardrailDraftStore((s) => s.addTopic);
  const removeTopic = useGuardrailDraftStore((s) => s.removeTopic);
  const cycleHarm = useGuardrailDraftStore((s) => s.cycleHarm);
  const [topicInput, setTopicInput] = useState('');

  const p = draft.protections;
  const suggestions = TOPIC_SUGGESTIONS.filter((s) => !p.deniedTopics.topics.includes(s.label));

  const harmTotal = Object.values(p.harmfulCategories.thresholds).reduce(
    (sum, level) => sum + HARM_HITS[level],
    0
  );

  const addTopicFromInput = () => {
    addTopic(topicInput);
    setTopicInput('');
  };

  return (
    <VerticalStack gap="5">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div className="gw-step-kicker">Step 2 of 3 · Protections</div>
        <h2 className="gw-step-title">Content &amp; policy guardrails</h2>
        <p className="gw-step-desc" style={{ margin: 0 }}>
          Stop prompts and responses that hijack goals, poison context or wander into topics this assistant shouldn’t handle. Impact estimates replay the last 7 days of traffic.
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {CONTENT_OWASP.map((t) => (
            <span key={t.id} className="gw-owasp-chip">
              OWASP {t.id} · {t.name}
            </span>
          ))}
        </div>
      </div>

      <ToggleCard
        enabled={p.promptInjection.enabled}
        onToggle={() => toggleProtection('promptInjection')}
        title="Prompt injection filter"
        owasp="ASI01"
        recommended
        description="Detects inputs that try to override system instructions, jailbreak role-play and encoded payloads."
        hits={LEVEL_HITS.pi[p.promptInjection.sensitivity]}
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 16, paddingTop: 4 }}>
          <span style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--gw-text-secondary)', width: 84 }}>Sensitivity</span>
          <SegmentedControl
            compact
            ariaLabel="Prompt injection filter sensitivity"
            value={p.promptInjection.sensitivity}
            onChange={(v) => setSensitivity('promptInjection', v as Sensitivity)}
            options={[
              { value: 'Low', label: 'Low' },
              { value: 'Medium', label: 'Medium' },
              { value: 'High', label: 'High' },
            ]}
          />
          <span style={{ fontSize: 12, color: 'var(--gw-muted)' }}>{LEVEL_HELP[p.promptInjection.sensitivity]}</span>
        </div>
      </ToggleCard>

      <ToggleCard
        enabled={p.contextPoisoning.enabled}
        onToggle={() => toggleProtection('contextPoisoning')}
        title="Context poisoning detection"
        owasp="ASI06"
        recommended
        description="Screens retrieved documents, tool outputs and agent memory for planted instructions before they reach the model."
        hits={LEVEL_HITS.ctx[p.contextPoisoning.sensitivity]}
        callout={
          <div className="gw-callout">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" style={{ flex: 'none', marginTop: 1 }}>
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8v5" />
              <path d="M12 16.5v.5" />
            </svg>
            <span>None of your 9 existing policies cover ASI06 today. Turning this on closes the gap for all 10 agents.</span>
          </div>
        }
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 16, paddingTop: 4 }}>
          <span style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--gw-text-secondary)', width: 84 }}>Sensitivity</span>
          <SegmentedControl
            compact
            ariaLabel="Context poisoning detection sensitivity"
            value={p.contextPoisoning.sensitivity}
            onChange={(v) => setSensitivity('contextPoisoning', v as Sensitivity)}
            options={[
              { value: 'Low', label: 'Low' },
              { value: 'Medium', label: 'Medium' },
              { value: 'High', label: 'High' },
            ]}
          />
          <span style={{ fontSize: 12, color: 'var(--gw-muted)' }}>{LEVEL_HELP[p.contextPoisoning.sensitivity]}</span>
        </div>
      </ToggleCard>

      <ToggleCard
        enabled={p.deniedTopics.enabled}
        onToggle={() => toggleProtection('deniedTopics')}
        title="Denied topics"
        owasp="ASI09"
        description="Refuse requests or responses about topics this assistant must not discuss. Up to 30 topics."
        hits={p.deniedTopics.topics.length * 31}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {p.deniedTopics.topics.map((topic) => (
              <span key={topic} className="gw-topic-chip">
                {topic}
                <button type="button" aria-label={`Remove ${topic}`} onClick={() => removeTopic(topic)}>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" aria-hidden="true">
                    <path d="M6 6l12 12" />
                    <path d="M18 6 6 18" />
                  </svg>
                </button>
              </span>
            ))}
            <label className="gw-topic-add">
              <span className="gw-sr-only">Add a denied topic</span>
              <input
                type="text"
                placeholder="+ Add a topic"
                value={topicInput}
                onChange={(e) => setTopicInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    addTopicFromInput();
                  }
                }}
              />
            </label>
          </div>
          {suggestions.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--gw-muted)' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontWeight: 600, color: 'var(--gw-accent-deep)' }}>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8z" />
                </svg>
                Seen in your traffic:
              </span>
              {suggestions.map((sg) => (
                <button
                  key={sg.label}
                  type="button"
                  className="gw-suggest-chip"
                  onClick={() => addTopic(sg.label)}
                >
                  + {sg.label} <span>· {sg.count} prompts</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </ToggleCard>

      <ToggleCard
        enabled={p.harmfulCategories.enabled}
        onToggle={() => toggleProtection('harmfulCategories')}
        title="Harmful categories"
        owasp="Content safety"
        description="Block hateful, violent, sexual and self-harm content in prompts and responses. Click a threshold to change it."
        hits={harmTotal}
      >
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 8 }}>
          {HARM_ORDER.map((cat) => (
            <div key={cat} className="gw-harm-row">
              <span style={{ fontSize: 13, fontWeight: 500 }}>{cat}</span>
              <ThresholdButton
                label={cat}
                level={p.harmfulCategories.thresholds[cat]}
                onCycle={() => cycleHarm(cat)}
              />
            </div>
          ))}
        </div>
      </ToggleCard>

      <HorizontalStack align="space-between" blockAlign="center" wrap>
        <Button icon={ArrowLeftMinor} onClick={onPrevious}>Previous</Button>
        <HorizontalStack gap="2">
          <Button onClick={() => {}}>Language safety next</Button>
          <Button primary icon={ArrowRightMinor} onClick={onNext}>
            Next: Scope &amp; review
          </Button>
        </HorizontalStack>
      </HorizontalStack>
    </VerticalStack>
  );
}
