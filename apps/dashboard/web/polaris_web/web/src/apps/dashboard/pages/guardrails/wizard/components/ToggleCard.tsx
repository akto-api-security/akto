import { ReactNode } from 'react';

interface ToggleCardProps {
  enabled: boolean;
  onToggle: () => void;
  title: string;
  owasp?: string;
  recommended?: boolean;
  description: string;
  hits?: number | null;
  children?: ReactNode;
  callout?: ReactNode;
}

function Switch({
  on,
  onToggle,
  label,
}: {
  on: boolean;
  onToggle: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      className={`gw-switch ${on ? 'is-on' : ''}`}
      onClick={onToggle}
    >
      <span className="gw-switch-knob" />
    </button>
  );
}

export { Switch };

export default function ToggleCard({
  enabled,
  onToggle,
  title,
  owasp,
  recommended,
  description,
  hits,
  children,
  callout,
}: ToggleCardProps) {
  return (
    <section className={`gw-toggle-card ${enabled ? '' : 'is-off'}`}>
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
        <Switch on={enabled} onToggle={onToggle} label={title} />
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
            <h3 style={{ margin: 0, fontSize: 15.5, fontWeight: 600 }}>{title}</h3>
            {owasp && <span className="gw-tag-chip" style={{ fontWeight: 600 }}>{owasp}</span>}
            {recommended && (
              <span className="gw-rec-pill is-accent" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 8px' }}>
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8z" />
                </svg>
                Recommended
              </span>
            )}
          </div>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--gw-muted)' }}>{description}</p>
        </div>
        {enabled && hits != null && (
          <div className="gw-hit-count">
            <strong>{hits.toLocaleString('en-US')}</strong>
            <span>would trigger · 7d</span>
          </div>
        )}
      </div>
      {enabled && callout}
      {enabled && children}
    </section>
  );
}
