import type { ComponentProps } from 'react';
import { LoaderCircle } from 'lucide-react';

import { cn } from '@/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand-700 text-white shadow-card hover:bg-brand-800 disabled:bg-brand-200',
  secondary: 'bg-surface text-ink-soft border border-line-strong shadow-card hover:bg-canvas disabled:text-muted',
  ghost: 'text-ink-soft hover:bg-canvas disabled:text-muted',
  danger: 'bg-danger-600 text-white shadow-card hover:bg-danger-700 disabled:opacity-50',
};

const SIZES: Record<Size, string> = {
  sm: 'h-8 px-3 text-[13px] gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
};

interface ButtonProps extends ComponentProps<'button'> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cn(
        'inline-flex items-center justify-center rounded-control font-semibold whitespace-nowrap transition-colors disabled:cursor-not-allowed',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    >
      {loading && <LoaderCircle className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}
