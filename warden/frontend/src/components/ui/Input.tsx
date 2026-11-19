import { forwardRef, useState, type ComponentPropsWithoutRef } from 'react';
import { Eye, EyeOff } from 'lucide-react';

import { cn } from '@/lib/cn';

interface InputProps extends ComponentPropsWithoutRef<'input'> {
  invalid?: boolean;
}

export const inputClass =
  'h-10 w-full rounded-control border bg-surface px-3 text-sm text-ink shadow-card placeholder:text-muted ' +
  'transition-colors focus:outline-none focus:ring-4 disabled:bg-canvas disabled:text-muted';

const stateClass = (invalid?: boolean) =>
  invalid
    ? 'border-danger-600 focus:border-danger-600 focus:ring-danger-50'
    : 'border-line-strong focus:border-brand-500 focus:ring-brand-100';

// React 18: function components only receive a ref through forwardRef. react-hook-form's
// register() relies on that ref to read, reset and focus the DOM input.
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ invalid, className, ...props }, ref) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(inputClass, stateClass(invalid), className)}
      {...props}
    />
  );
});

export const PasswordInput = forwardRef<HTMLInputElement, Omit<InputProps, 'type'>>(function PasswordInput(
  { invalid, className, ...props },
  ref,
) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input ref={ref} type={visible ? 'text' : 'password'} invalid={invalid} className={cn('pr-10', className)} {...props} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-muted hover:text-ink-soft"
        aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        tabIndex={-1}
      >
        {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </div>
  );
});

interface SelectProps extends ComponentPropsWithoutRef<'select'> {
  invalid?: boolean;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select({ invalid, className, ...props }, ref) {
  return (
    <select
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(inputClass, stateClass(invalid), 'appearance-none bg-no-repeat pr-9', className)}
      style={{
        backgroundImage:
          "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 20'%3E%3Cpath d='M5.5 7.5 10 12l4.5-4.5' stroke='%23667085' stroke-width='1.5' fill='none' stroke-linecap='round'/%3E%3C/svg%3E\")",
        backgroundPosition: 'right 0.6rem center',
        backgroundSize: '1.1rem',
      }}
      {...props}
    />
  );
});
