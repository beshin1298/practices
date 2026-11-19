import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { KeyRound, LogOut, Menu, ShieldCheck, UserRound, UsersRound, X, type LucideIcon } from 'lucide-react';

import { useAppDispatch, useAppSelector } from '@/app/store';
import { Avatar } from '@/components/ui/Avatar';
import { Logo } from '@/components/ui/Logo';
import { useMe } from '@/features/account/api';
import { useLogout } from '@/features/auth/api';
import { userUpdated } from '@/features/auth/authSlice';
import { cn } from '@/lib/cn';
import { can } from '@/lib/permissions';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}


export function AppShell() {
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const { data: me } = useMe();
  const logout = useLogout();
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  // Keep the Redux copy fresh (e.g. an admin changed my roles): menus and guards read it.
  useEffect(() => {
    if (me) dispatch(userUpdated(me));
  }, [me, dispatch]);

  useEffect(() => setMobileOpen(false), [location.pathname]);

  if (!user) return null;

  const accountNav: NavItem[] = [
    ...(can(user, 'USER_READ_OWN') ? [{ to: '/account', label: 'Hồ sơ', icon: UserRound, end: true }] : []),
    ...(can(user, 'USER_UPDATE_OWN') ? [{ to: '/account/password', label: 'Đổi mật khẩu', icon: KeyRound }] : []),
  ];

  const organisationNav: NavItem[] = [
    ...(can(user, 'USER_READ_DEPARTMENT', 'USER_READ_ALL') ? [{ to: '/people', label: 'Nhân sự', icon: UsersRound }] : []),
    ...(can(user, 'RBAC_READ') ? [{ to: '/roles', label: 'Vai trò & quyền', icon: ShieldCheck }] : []),
  ];

  const sidebar = (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center border-b border-line px-5">
        <Logo />
      </div>
      <nav className="flex-1 space-y-6 overflow-y-auto p-3" aria-label="Điều hướng chính">
        <NavSection title="Tài khoản" items={accountNav} />
        <NavSection title="Tổ chức" items={organisationNav} />
      </nav>
      <div className="border-t border-line p-3">
        <div className="flex items-center gap-3 rounded-control p-2">
          <Avatar name={user.fullName} size="sm" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-ink">{user.fullName}</p>
            <p className="truncate text-xs text-muted">
              {user.roles[0]?.name ?? 'Chưa có vai trò'} · {user.departments.map((d) => d.code).join(', ')}
            </p>
          </div>
          <button
            type="button"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            className="rounded-control p-2 text-muted hover:bg-canvas hover:text-danger-600"
            aria-label="Đăng xuất"
            title="Đăng xuất"
          >
            <LogOut className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen lg:pl-64">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-surface lg:block">{sidebar}</aside>

      <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-line bg-surface/90 px-4 backdrop-blur lg:hidden">
        <Logo />
        <button
          type="button"
          onClick={() => setMobileOpen(true)}
          className="rounded-control p-2 text-ink-soft hover:bg-canvas"
          aria-label="Mở menu"
        >
          <Menu className="size-5" />
        </button>
      </header>

      {mobileOpen && (
        <div className="fixed inset-0 z-30 lg:hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-ink/40" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 bg-surface shadow-pop">
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              className="absolute top-4 right-3 rounded-control p-1.5 text-muted hover:bg-canvas"
              aria-label="Đóng menu"
            >
              <X className="size-5" />
            </button>
            {sidebar}
          </aside>
        </div>
      )}

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-8 lg:py-10">
        <Outlet />
      </main>
    </div>
  );
}

function NavSection({ title, items }: { title: string; items: NavItem[] }) {
  return (
    <div>
      <p className="px-3 pb-2 text-xs font-semibold tracking-wide text-muted uppercase">{title}</p>
      <ul className="space-y-0.5">
        {items.map(({ to, label, icon: Icon, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-control px-3 py-2 text-sm font-medium transition-colors',
                  isActive ? 'bg-brand-50 text-brand-700' : 'text-ink-soft hover:bg-canvas hover:text-ink',
                )
              }
            >
              <Icon className="size-4" aria-hidden />
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
    </div>
  );
}
