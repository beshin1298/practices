import { Check } from 'lucide-react';

import { cn } from '@/lib/cn';

export interface CheckboxOption<T extends number | string = number> {
  value: T;
  label: string;
  hint?: string;
  disabled?: boolean;
}

interface CheckboxListProps<T extends number | string> {
  options: CheckboxOption<T>[];
  value: T[];
  onChange: (value: T[]) => void;
  invalid?: boolean;
  label: string;
}

/** Multi-select for many-to-many fields (roles, departments, direct permissions). */
export function CheckboxList<T extends number | string>({ options, value, onChange, invalid, label }: CheckboxListProps<T>) {
  const toggle = (id: T) => onChange(value.includes(id) ? value.filter((v) => v !== id) : [...value, id]);

  return (
    <div
      role="group"
      aria-label={label}
      className={cn('grid gap-2 sm:grid-cols-2', invalid && 'rounded-control ring-2 ring-danger-600/40 ring-offset-2')}
    >
      {options.map((option) => {
        const checked = value.includes(option.value);
        return (
          <button
            key={option.value}
            type="button"
            role="checkbox"
            aria-checked={checked}
            disabled={option.disabled}
            onClick={() => toggle(option.value)}
            className={cn(
              'flex items-start gap-3 rounded-control border px-3 py-2.5 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50',
              checked ? 'border-brand-500 bg-brand-50' : 'border-line-strong bg-surface hover:bg-canvas',
            )}
          >
            <span
              className={cn(
                'mt-0.5 flex size-4 shrink-0 items-center justify-center rounded border',
                checked ? 'border-brand-700 bg-brand-700 text-white' : 'border-line-strong bg-surface',
              )}
              aria-hidden
            >
              {checked && <Check className="size-3" strokeWidth={3} />}
            </span>
            <span className="min-w-0">
              <span className="block text-sm font-medium text-ink">{option.label}</span>
              {option.hint && <span className="block text-xs text-muted">{option.hint}</span>}
            </span>
          </button>
        );
      })}
    </div>
  );
}
