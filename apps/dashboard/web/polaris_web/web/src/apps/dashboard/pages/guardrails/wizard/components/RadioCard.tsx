import { ReactNode } from 'react';

interface RadioCardProps {
  selected: boolean;
  onSelect: () => void;
  children: ReactNode;
  className?: string;
  role?: 'radio' | 'checkbox';
  tabIndex?: number;
  ariaLabel?: string;
}

export default function RadioCard({
  selected,
  onSelect,
  children,
  className = '',
  role = 'radio',
  tabIndex,
  ariaLabel,
}: RadioCardProps) {
  return (
    <button
      type="button"
      role={role}
      aria-checked={selected}
      aria-label={ariaLabel}
      tabIndex={tabIndex}
      onClick={onSelect}
      className={`gw-radio-card ${selected ? 'is-selected' : ''} ${className}`.trim()}
    >
      {children}
    </button>
  );
}

export function RadioMark({ selected }: { selected: boolean }) {
  return (
    <span className={`gw-radio-mark ${selected ? 'is-checked' : ''}`}>
      <span className="gw-radio-mark-dot" />
    </span>
  );
}

export function CheckMark({ selected }: { selected: boolean }) {
  return (
    <span className={`gw-check-mark ${selected ? 'is-checked' : ''}`} aria-hidden="true">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.4">
        <path d="m5 12 5 5 9-10" />
      </svg>
    </span>
  );
}
