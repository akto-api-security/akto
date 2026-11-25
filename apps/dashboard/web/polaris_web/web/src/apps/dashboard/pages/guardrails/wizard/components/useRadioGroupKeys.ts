import { KeyboardEvent, useCallback } from 'react';

export function useRadioGroupKeys<T extends string>(
  values: T[],
  value: T,
  onChange: (next: T) => void
) {
  return useCallback(
    (event: KeyboardEvent<HTMLElement>) => {
      const idx = values.indexOf(value);
      if (idx < 0) return;
      let next = idx;
      if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
        next = (idx + 1) % values.length;
      } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
        next = (idx - 1 + values.length) % values.length;
      } else if (event.key === 'Home') {
        next = 0;
      } else if (event.key === 'End') {
        next = values.length - 1;
      } else {
        return;
      }
      event.preventDefault();
      onChange(values[next]);
    },
    [values, value, onChange]
  );
}
