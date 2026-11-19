import type { ReactNode } from 'react';

interface FieldProps {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
}

export function Field({ label, htmlFor, error, hint, children }: FieldProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-ink-soft">
        {label}
      </label>
      {children}
      {error ? (
        <p role="alert" className="text-[13px] text-danger-600">
          {error}
        </p>
      ) : (
        hint && <div className="text-[13px] text-muted">{hint}</div>
      )}
    </div>
  );
}
