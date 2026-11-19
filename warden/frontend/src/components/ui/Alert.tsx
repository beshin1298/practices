import type { ReactNode } from 'react';
import { CircleAlert, Info } from 'lucide-react';

import { cn } from '@/lib/cn';

export function Alert({ tone = 'info', children }: { tone?: 'info' | 'danger' | 'warning'; children: ReactNode }) {
  const Icon = tone === 'info' ? Info : CircleAlert;
  return (
    <div
      role={tone === 'info' ? 'status' : 'alert'}
      className={cn(
        'flex gap-3 rounded-control border px-3.5 py-3 text-[13px] leading-5',
        tone === 'info' && 'border-brand-200 bg-brand-50 text-brand-900',
        tone === 'danger' && 'border-danger-600/30 bg-danger-50 text-danger-700',
        tone === 'warning' && 'border-warning-700/30 bg-warning-50 text-warning-700',
      )}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  );
}
