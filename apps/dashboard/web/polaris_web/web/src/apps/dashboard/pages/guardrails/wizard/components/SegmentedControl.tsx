import { useRadioGroupKeys } from './useRadioGroupKeys';

interface Option {
  value: string;
  label: string;
  showDot?: boolean;
}

interface SegmentedControlProps {
  options: Option[];
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
  compact?: boolean;
}

export default function SegmentedControl({
  options,
  value,
  onChange,
  ariaLabel,
  compact = false,
}: SegmentedControlProps) {
  const values = options.map((o) => o.value);
  const onKeyDown = useRadioGroupKeys(values, value, onChange);

  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      className={`gw-segmented ${compact ? 'is-compact' : ''}`}
      onKeyDown={onKeyDown}
    >
      {options.map((opt) => {
        const selected = opt.value === value;
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            className={`gw-segment ${selected ? 'is-selected' : ''}`}
            onClick={() => onChange(opt.value)}
          >
            {opt.showDot && (
              <span className={`gw-sev-dot ${opt.value.toLowerCase()}`} aria-hidden="true" />
            )}
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
