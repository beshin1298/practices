import { cn } from '@/lib/cn';
import { initials } from '@/lib/format';

const SIZES = { sm: 'size-8 text-xs', md: 'size-10 text-sm', lg: 'size-16 text-xl' } as const;

export function Avatar({ name, size = 'md' }: { name: string; size?: keyof typeof SIZES }) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-800 ring-2 ring-surface',
        SIZES[size],
      )}
    >
      {initials(name)}
    </span>
  );
}
