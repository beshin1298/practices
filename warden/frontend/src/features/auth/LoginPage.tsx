import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound, ShieldCheck, UsersRound } from 'lucide-react';
import { z } from 'zod';

import { useAppDispatch, useAppSelector } from '@/app/store';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Field } from '@/components/ui/Field';
import { Input, PasswordInput } from '@/components/ui/Input';
import { Logo } from '@/components/ui/Logo';
import { isApiError } from '@/lib/http';
import type { SignOutReason } from '@/lib/types';
import { useLogin } from './api';
import { signOutReasonShown } from './authSlice';

const schema = z.object({
  username: z.string().trim().min(1, 'Vui lòng nhập tên đăng nhập'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});
type FormValues = z.infer<typeof schema>;

const REASONS: Record<SignOutReason, string> = {
  TOKEN_EXPIRED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  TOKEN_REVOKED: 'Phiên đăng nhập đã bị thu hồi (mật khẩu vừa đổi hoặc tài khoản bị khóa).',
  TOKEN_INVALID: 'Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.',
  REFRESH_TOKEN_INVALID: 'Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.',
  REFRESH_TOKEN_EXPIRED: 'Phiên đăng nhập đã quá 7 ngày. Vui lòng đăng nhập lại.',
  REFRESH_TOKEN_REUSED: 'Phát hiện phiên đăng nhập bị dùng lại ở nơi khác; mọi phiên liên quan đã bị thu hồi.',
};

/** Demo shortcuts ship only in dev, or in a build made with VITE_SHOW_DEMO_ACCOUNTS=true (the demo stack). */
const SHOW_DEMO_ACCOUNTS = import.meta.env.DEV || import.meta.env.VITE_SHOW_DEMO_ACCOUNTS === 'true';

const DEMO_ACCOUNTS = [
  { label: 'Admin', note: 'Toàn quyền, gán vai trò', username: 'admin', password: 'Admin@123' },
  { label: 'Nhân sự', note: 'Tạo, sửa, khóa mọi người', username: 'hr', password: 'User@123' },
  { label: 'Quản lý', note: 'Sửa người phòng Kinh doanh', username: 'manager', password: 'User@123' },
  { label: 'Nhân viên', note: 'Chỉ hồ sơ của mình', username: 'user', password: 'User@123' },
];

const HIGHLIGHTS = [
  { icon: ShieldCheck, title: 'Token thu hồi được ngay', text: 'Đổi mật khẩu hay bị khóa là mọi phiên cũ mất hiệu lực.' },
  { icon: UsersRound, title: 'Phân quyền theo CRUD và phạm vi', text: 'Của mình, phòng ban hay toàn công ty; nhiều vai trò, nhiều phòng ban.' },
  { icon: KeyRound, title: 'Mật khẩu an toàn', text: 'BCrypt, chính sách mật khẩu giống nhau ở client và server.' },
];

export function LoginPage() {
  const dispatch = useAppDispatch();
  const signOutReason = useAppSelector((state) => state.auth.signOutReason);
  const [reason] = useState(signOutReason); // keep the message after clearing it from the store
  const login = useLogin();

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } });

  useEffect(() => {
    if (signOutReason) dispatch(signOutReasonShown());
  }, [signOutReason, dispatch]);

  const serverError = login.error && isApiError(login.error) ? login.error.message : null;

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-brand-900 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-[0.12]"
          style={{
            backgroundImage:
              'linear-gradient(to right, white 1px, transparent 1px), linear-gradient(to bottom, white 1px, transparent 1px)',
            backgroundSize: '44px 44px',
            maskImage: 'radial-gradient(ellipse at 30% 40%, black 20%, transparent 70%)',
          }}
        />
        <div aria-hidden className="absolute -right-24 -bottom-24 size-96 rounded-full bg-brand-600/40 blur-3xl" />

        <div className="relative">
          <Logo inverted />
        </div>

        <div className="relative max-w-md">
          <h2 className="text-4xl leading-tight font-semibold tracking-tight">Quản lý truy cập rõ ràng và an toàn.</h2>
          <p className="mt-4 text-base text-brand-100/80">
            Đăng nhập, hồ sơ, đổi mật khẩu và phân quyền Admin/User trong một ứng dụng gọn nhẹ.
          </p>
          <ul className="mt-10 space-y-5">
            {HIGHLIGHTS.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-control bg-white/10 ring-1 ring-white/15">
                  <Icon className="size-5" aria-hidden />
                </span>
                <div>
                  <p className="font-semibold">{title}</p>
                  <p className="text-sm text-brand-100/75">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-brand-100/60">Spring Boot · PostgreSQL · React · Redux · TanStack</p>
      </aside>

      <main className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm">
          <div className="mb-10 lg:hidden">
            <Logo />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">Đăng nhập</h1>
          <p className="mt-2 text-muted">Chào mừng trở lại. Nhập thông tin tài khoản để tiếp tục.</p>

          <div className="mt-6 space-y-3">
            {reason && <Alert tone="warning">{REASONS[reason]}</Alert>}
            {serverError && <Alert tone="danger">{serverError}</Alert>}
          </div>

          <form className="mt-6 space-y-5" noValidate onSubmit={handleSubmit((values) => login.mutate(values))}>
            <Field label="Tên đăng nhập" htmlFor="username" error={errors.username?.message}>
              <Input id="username" autoComplete="username" autoFocus invalid={!!errors.username} {...register('username')} />
            </Field>
            <Field label="Mật khẩu" htmlFor="password" error={errors.password?.message}>
              <PasswordInput id="password" autoComplete="current-password" invalid={!!errors.password} {...register('password')} />
            </Field>
            <Button type="submit" className="w-full" loading={login.isPending}>
              Đăng nhập
            </Button>
          </form>

          {SHOW_DEMO_ACCOUNTS && (
          <div className="mt-8 rounded-card border border-dashed border-line-strong p-4">
            <p className="text-xs font-semibold tracking-wide text-muted uppercase">Tài khoản demo</p>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {DEMO_ACCOUNTS.map((account) => (
                <button
                  key={account.username}
                  type="button"
                  onClick={() => {
                    setValue('username', account.username, { shouldValidate: true });
                    setValue('password', account.password, { shouldValidate: true });
                  }}
                  className="rounded-control border border-line bg-surface px-3 py-2 text-left transition-colors hover:border-brand-200 hover:bg-brand-50"
                >
                  <span className="block text-sm font-semibold text-ink">{account.label}</span>
                  <span className="block text-xs text-muted">{account.note}</span>
                  <span className="block font-mono text-[11px] text-muted">
                    {account.username} / {account.password}
                  </span>
                </button>
              ))}
            </div>
          </div>
          )}
        </div>
      </main>
    </div>
  );
}
