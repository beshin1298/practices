import type { ReactNode } from 'react';

import { cn } from '@/lib/cn';
import type { Department, RoleSummary } from '@/lib/types';

type Tone = 'brand' | 'neutral' | 'success' | 'danger' | 'warning';

const TONES: Record<Tone, string> = {
  brand: 'bg-brand-50 text-brand-700 ring-brand-200',
  neutral: 'bg-canvas text-ink-soft ring-line-strong',
  success: 'bg-success-50 text-success-700 ring-success-600/20',
  danger: 'bg-danger-50 text-danger-700 ring-danger-600/20',
  warning: 'bg-warning-50 text-warning-700 ring-warning-700/20',
};

export function Badge({ tone = 'neutral', children, dot }: { tone?: Tone; children: ReactNode; dot?: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset',
        TONES[tone],
      )}
    >
      {dot && <span className="size-1.5 rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  );
}

const ROLE_TONES: Record<string, Tone> = { ADMIN: 'brand', HR: 'success', MANAGER: 'warning' };

export function RoleBadges({ roles }: { roles: RoleSummary[] }) {
  return (
    <span className="inline-flex flex-wrap gap-1">
      {roles.map((role) => (
        <Badge key={role.id} tone={ROLE_TONES[role.code] ?? 'neutral'}>
          {role.name}
        </Badge>
      ))}
    </span>
  );
}

export function DepartmentBadges({ departments }: { departments: Department[] }) {
  return (
    <span className="inline-flex flex-wrap gap-1">
      {departments.map((department) => (
        <span
          key={department.id}
          title={department.name}
          className="inline-flex items-center rounded-control bg-canvas px-1.5 py-0.5 text-xs font-medium whitespace-nowrap text-ink-soft ring-1 ring-line ring-inset"
        >
          {department.name}
        </span>
      ))}
    </span>
  );
}

export function StatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <Badge tone={enabled ? 'success' : 'danger'} dot>
      {enabled ? 'Hoạt động' : 'Đã khóa'}
    </Badge>
  );
}
