import DarkSummaryCard from '../components/DarkSummaryCard';
import PlaygroundPanel from '../components/PlaygroundPanel';
import { PLAYGROUND_TESTS } from '../api/fixtures';
import { runPlaygroundTest } from '../api/guardrailPolicyApi';
import { useGuardrailDraftStore } from '../draft.store';
import { getBlueprint, getStrengthLabel } from '../selectors';
import func from '@/util/func';

export default function BlueprintAside() {
  const draft = useGuardrailDraftStore((s) => s.draft);
  const playgroundTestIndex = useGuardrailDraftStore((s) => s.playgroundTestIndex);
  const setPlaygroundTestIndex = useGuardrailDraftStore((s) => s.setPlaygroundTestIndex);

  const bp = getBlueprint(draft);
  const strength = bp.strength;
  const lit = Math.round(strength / 10);
  const test = PLAYGROUND_TESTS[playgroundTestIndex] ?? PLAYGROUND_TESTS[1];

  const handleRun = async (prompt: string) => {
    const result = await runPlaygroundTest(draft, prompt);
    const idx = PLAYGROUND_TESTS.findIndex((t) => t.prompt === result.prompt);
    if (idx >= 0) setPlaygroundTestIndex(idx);
    else func.setToast(true, false, `${result.verdict}: ${result.rule}`);
  };

  return (
    <>
      <DarkSummaryCard>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="gw-dark-label">Policy strength</span>
          <span className="gw-dark-badge">{getStrengthLabel(strength)}</span>
        </div>
        <div className="gw-strength-score">
          <strong>{strength}</strong>
          <span>/ 100</span>
        </div>
        <div className="gw-strength-bars">
          {Array.from({ length: 10 }, (_, i) => {
            const litClass = i < lit ? (i < 4 ? 'is-lit-1' : i < 7 ? 'is-lit-2' : 'is-lit-3') : '';
            return <span key={i} className={`gw-strength-bar ${litClass}`} />;
          })}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {bp.includes.map((inc) => (
            <div key={inc} className="gw-include-row">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#7CE0A8" strokeWidth="2.6" aria-hidden="true">
                <path d="m5 12 5 5 9-10" />
              </svg>
              {inc}
            </div>
          ))}
        </div>
        <div className="gw-dark-tip">
          Tip: add <strong style={{ color: '#FFFFFF' }}>Tools guardrails</strong> to cover ASI02 Tool Misuse (+12)
        </div>
      </DarkSummaryCard>

      <PlaygroundPanel
        promptContent={test.prompt}
        test={test}
        onRun={handleRun}
        quickTests={PLAYGROUND_TESTS.map((t, i) => ({
          text: t.prompt,
          selected: i === playgroundTestIndex,
          onSelect: () => setPlaygroundTestIndex(i),
        }))}
      />
    </>
  );
}
