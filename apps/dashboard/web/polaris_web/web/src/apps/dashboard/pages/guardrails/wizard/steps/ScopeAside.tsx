import { Text, VerticalStack } from '@shopify/polaris';
import DarkSummaryCard from '../components/DarkSummaryCard';
import OwaspTileGrid from '../components/OwaspTileGrid';
import SeverityPill from '../../../../components/shared/SeverityPill';
import { OWASP_TILES, PREFLIGHT_CHECKS } from '../api/fixtures';
import { useGuardrailDraftStore } from '../draft.store';
import {
  getActionSummary,
  getInspectSummary,
  getProtectionsSummary,
  getScopeSummary,
} from '../selectors';
import func from '@/util/func';

export default function ScopeAside() {
  const draft = useGuardrailDraftStore((s) => s.draft);

  return (
    <>
      <DarkSummaryCard>
        <span className="gw-dark-label">Your threat coverage after this policy</span>
        <div className="gw-coverage-delta">
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: 12, color: 'var(--gw-dark-muted)' }}>Now</span>
            <span className="gw-coverage-now">40</span>
          </div>
          <svg width="28" height="16" viewBox="0 0 28 16" fill="none" stroke="#7CE0A8" strokeWidth="2" aria-hidden="true">
            <path d="M1 8h24" />
            <path d="m19 2 6 6-6 6" />
          </svg>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: 12, color: 'var(--gw-success-dark)' }}>After</span>
            <span className="gw-coverage-after">60</span>
          </div>
          <span className="gw-coverage-badge">+20</span>
        </div>
        <OwaspTileGrid tiles={OWASP_TILES} />
        <div style={{ fontSize: 12.5, color: 'var(--gw-dark-tip)' }}>
          Closes <strong style={{ color: '#FFFFFF' }}>ASI01 Goal Hijack</strong> and{' '}
          <strong style={{ color: '#FFFFFF' }}>ASI06 Context Poisoning</strong> for 10 agents.
        </div>
      </DarkSummaryCard>

      <VerticalStack gap="3">
        <Text as="h2" variant="headingSm">Pre-flight checks</Text>
        {PREFLIGHT_CHECKS.map((ck) => (
          <div key={ck.title} className="gw-check-row">
            <span className={`gw-check-icon ${ck.kind}`}>{ck.kind === 'ok' ? '✓' : '!'}</span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
              <span style={{ fontSize: 13, fontWeight: 600 }}>{ck.title}</span>
              <span style={{ fontSize: 12.5, color: 'var(--gw-muted)' }}>{ck.body}</span>
              {ck.action && (
                <button
                  type="button"
                  className="gw-link-btn"
                  style={{ fontWeight: 600, fontSize: 12.5, marginTop: 2, textAlign: 'left' }}
                  onClick={() => func.setToast(true, false, ck.action!)}
                >
                  {ck.action}
                </button>
              )}
            </div>
          </div>
        ))}
      </VerticalStack>

      <VerticalStack gap="2">
        <Text as="h2" variant="headingSm">Summary</Text>
        <dl className="gw-summary-dl">
          <dt>Severity</dt>
          <dd><SeverityPill severity={draft.severity} /></dd>
          <dt>Protections</dt>
          <dd>{getProtectionsSummary(draft)}</dd>
          <dt>Scope</dt>
          <dd>{getScopeSummary(draft)}</dd>
          <dt>Action</dt>
          <dd>{getActionSummary(draft)}</dd>
          <dt>Inspects</dt>
          <dd>{getInspectSummary(draft)}</dd>
        </dl>
      </VerticalStack>
    </>
  );
}
