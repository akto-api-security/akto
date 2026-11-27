import { HarmLevel } from '../types';

interface ThresholdButtonProps {
  label: string;
  level: HarmLevel;
  onCycle: () => void;
}

export default function ThresholdButton({ label, level, onCycle }: ThresholdButtonProps) {
  const display = level === 'Off' ? 'Off' : `Block ≥ ${level}`;
  return (
    <button
      type="button"
      className={`gw-threshold-btn ${level.toLowerCase()}`}
      onClick={onCycle}
      aria-label={`${label} threshold: ${display}. Change`}
    >
      {display}
    </button>
  );
}
