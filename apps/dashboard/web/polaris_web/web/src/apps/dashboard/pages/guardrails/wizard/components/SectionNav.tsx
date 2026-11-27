import { useNavigate } from 'react-router-dom';
import { NavGroup, NavItemState } from '../types';

interface SectionNavProps {
  groups: NavGroup[];
  progressLabel: string;
  progressPct: number;
  progressComplete?: boolean;
}

function markFor(state: NavItemState): string {
  if (state === 'done') return '✓';
  if (state === 'set') return '•';
  return '';
}

export default function SectionNav({
  groups,
  progressLabel,
  progressPct,
  progressComplete = false,
}: SectionNavProps) {
  const navigate = useNavigate();

  return (
    <nav aria-label="Policy sections">
      <div className="gw-progress-card">
        <div className="gw-progress-row">
          <strong>Setup progress</strong>
          <span>{progressLabel}</span>
        </div>
        <div className="gw-progress-track">
          <div
            className={`gw-progress-fill ${progressComplete ? 'is-complete' : ''}`}
            style={{ width: `${progressPct}%` }}
          />
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 18, marginTop: 18 }}>
        {groups.map((group) => (
          <div key={group.title} className="gw-nav-group">
            <div className="gw-nav-group-title">{group.title}</div>
            {group.items.map((item) => {
              const isCurrent = item.state === 'current';
              const className = [
                'guardrail-nav-item',
                isCurrent ? 'active' : '',
                !item.navigable ? 'is-disabled' : '',
              ]
                .filter(Boolean)
                .join(' ');

              const content = (
                <>
                  <span className={`step-indicator ${item.state}`}>{markFor(item.state)}</span>
                  <span style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                    <span className={`gw-nav-label ${isCurrent ? 'is-current' : ''}`}>{item.label}</span>
                    {item.sub ? <span className="gw-nav-sub">{item.sub}</span> : null}
                  </span>
                </>
              );

              if (!item.navigable || !item.href) {
                return (
                  <div
                    key={item.id}
                    className={className}
                    aria-disabled="true"
                  >
                    {content}
                  </div>
                );
              }

              return (
                <button
                  key={item.id}
                  type="button"
                  className={className}
                  onClick={() => navigate(item.href!)}
                >
                  {content}
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </nav>
  );
}
