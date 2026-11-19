import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { LoaderCircle } from 'lucide-react';

import { useAppSelector } from '@/app/store';
import { ForbiddenPage } from '@/components/layout/StatusPage';
import { can } from '@/lib/permissions';
import type { PermissionCode } from '@/lib/types';

/**
 * Route guards mirror the "Screens" table in docs/rbac-matrix.md. They are UX only: the API
 * enforces every permission and scope itself.
 */
export function RequireAuth() {
  const status = useAppSelector((state) => state.auth.status);
  const location = useLocation();
  if (status === 'restoring') return <SplashScreen />;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

export function RequirePermission({ anyOf }: { anyOf: PermissionCode[] }) {
  const user = useAppSelector((state) => state.auth.user);
  return can(user, ...anyOf) ? <Outlet /> : <ForbiddenPage />;
}

export function GuestOnly() {
  const status = useAppSelector((state) => state.auth.status);
  if (status === 'restoring') return <SplashScreen />;
  return status === 'authenticated' ? <Navigate to="/account" replace /> : <Outlet />;
}

/** Shown while the refresh cookie is exchanged for a session after a reload. */
function SplashScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center text-muted" aria-busy>
      <LoaderCircle className="size-6 animate-spin" aria-label="Đang khôi phục phiên đăng nhập" />
    </div>
  );
}
