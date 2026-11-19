import { cn } from '@/lib/cn';

export function Logo({ inverted = false }: { inverted?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <svg viewBox="0 0 32 32" className="size-8" aria-hidden>
        <rect width="32" height="32" rx="8" className={inverted ? 'fill-white/15' : 'fill-brand-700'} />
        <path
          d="M16 6.5 8.5 9.5v6c0 4.6 3.1 8.6 7.5 10 4.4-1.4 7.5-5.4 7.5-10v-6L16 6.5Z"
          fill="none"
          stroke="#fff"
          strokeWidth="2"
          strokeLinejoin="round"
        />
        <path d="m12.5 16 2.5 2.5 4.5-5" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className={cn('text-lg font-bold tracking-tight', inverted ? 'text-white' : 'text-ink')}>Warden</span>
    </span>
  );
}
