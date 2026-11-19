import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Briefcase, Building2, CalendarDays, Clock, KeyRound, Mail, Phone } from 'lucide-react';
import { toast } from 'sonner';
import { z } from 'zod';

import { PageHeader } from '@/components/layout/PageHeader';
import { Avatar } from '@/components/ui/Avatar';
import { DepartmentBadges, RoleBadges, StatusBadge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field } from '@/components/ui/Field';
import { Input } from '@/components/ui/Input';
import { PHONE_MESSAGE, PHONE_PATTERN } from '@/lib/forms';
import { applyServerErrors } from '@/lib/forms';
import { formatDateTime } from '@/lib/format';
import { PERMISSION_LABELS } from '@/lib/permissions';
import { useMe, useUpdateOwnProfile } from './api';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z.string().trim().regex(PHONE_PATTERN, PHONE_MESSAGE),
});
type FormValues = z.infer<typeof schema>;

export function AccountPage() {
  const { data: me, isPending, isError } = useMe();
  const updateProfile = useUpdateOwnProfile();

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { fullName: '', phone: '' } });

  useEffect(() => {
    if (me) reset({ fullName: me.fullName, phone: me.phone ?? '' });
  }, [me, reset]);

  if (isPending) return <AccountSkeleton />;
  if (isError || !me) return <p className="text-danger-600">Không tải được thông tin tài khoản.</p>;

  const onSubmit = handleSubmit((values) =>
    updateProfile.mutate(values, {
      onSuccess: () => toast.success('Đã lưu thông tin tài khoản'),
      onError: (error) => applyServerErrors(error, setError),
    }),
  );

  const facts = [
    { icon: Mail, label: 'Email', value: me.email },
    { icon: Phone, label: 'Số điện thoại', value: me.phone ?? 'Chưa có' },
    { icon: Briefcase, label: 'Chức danh', value: me.jobTitle ?? 'Chưa có' },
    { icon: CalendarDays, label: 'Ngày tạo', value: formatDateTime(me.createdAt) },
    { icon: Clock, label: 'Đăng nhập gần nhất', value: formatDateTime(me.lastLoginAt) },
    { icon: KeyRound, label: 'Đổi mật khẩu gần nhất', value: me.passwordChangedAt ? formatDateTime(me.passwordChangedAt) : 'Chưa đổi' },
  ];

  return (
    <>
      <PageHeader title="Hồ sơ của tôi" description="Thông tin tài khoản, vai trò và quyền hiện có." />

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,340px)_1fr]">
        <Card className="overflow-hidden">
          <div className="h-20 bg-gradient-to-r from-brand-700 to-brand-500" aria-hidden />
          <div className="-mt-8 px-6 pb-6">
            <Avatar name={me.fullName} size="lg" />
            <h2 className="mt-3 text-lg font-semibold">{me.fullName}</h2>
            <p className="text-muted">@{me.username}</p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <RoleBadges roles={me.roles} />
              <StatusBadge enabled={me.enabled} />
            </div>
            <div className="mt-3 flex items-start gap-2">
              <Building2 className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
              <DepartmentBadges departments={me.departments} />
            </div>
            <dl className="mt-6 space-y-4 border-t border-line pt-5">
              {facts.map(({ icon: Icon, label, value }) => (
                <div key={label} className="flex gap-3">
                  <Icon className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
                  <div className="min-w-0">
                    <dt className="text-xs text-muted">{label}</dt>
                    <dd className="truncate font-medium text-ink-soft">{value}</dd>
                  </div>
                </div>
              ))}
            </dl>
          </div>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Thông tin cá nhân" description="Email, chức danh, phòng ban và vai trò do phòng Nhân sự quản lý." />
            <form onSubmit={onSubmit} noValidate>
              <div className="grid gap-5 px-6 py-6 sm:grid-cols-2">
                <Field label="Họ và tên" htmlFor="fullName" error={errors.fullName?.message}>
                  <Input id="fullName" autoComplete="name" invalid={!!errors.fullName} {...register('fullName')} />
                </Field>
                <Field label="Số điện thoại" htmlFor="phone" error={errors.phone?.message}>
                  <Input id="phone" type="tel" autoComplete="tel" invalid={!!errors.phone} {...register('phone')} />
                </Field>
                <Field label="Email" htmlFor="email" hint="Do phòng Nhân sự quản lý">
                  <Input id="email" value={me.email} disabled readOnly />
                </Field>
                <Field label="Chức danh" htmlFor="jobTitle" hint="Do quản lý hoặc Nhân sự cập nhật">
                  <Input id="jobTitle" value={me.jobTitle ?? ''} disabled readOnly />
                </Field>
              </div>
              <div className="flex justify-end gap-3 border-t border-line px-6 py-4">
                <Button variant="secondary" disabled={!isDirty || updateProfile.isPending} onClick={() => reset()}>
                  Hủy thay đổi
                </Button>
                <Button type="submit" disabled={!isDirty} loading={updateProfile.isPending}>
                  Lưu thay đổi
                </Button>
              </div>
            </form>
          </Card>

          <Card>
            <CardHeader title="Quyền của tôi" description="Hợp các quyền của mọi vai trò bạn đang có." />
            <ul className="grid gap-2 px-6 py-5 sm:grid-cols-2">
              {me.permissions.map((permission) => (
                <li key={permission} className="rounded-control border border-line bg-canvas/60 px-3 py-2">
                  <p className="text-sm font-medium text-ink">{PERMISSION_LABELS[permission] ?? permission}</p>
                  <p className="font-mono text-[11px] text-muted">{permission}</p>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
    </>
  );
}

function AccountSkeleton() {
  return (
    <div className="animate-pulse space-y-6" aria-busy>
      <div className="h-8 w-48 rounded-control bg-line" />
      <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
        <div className="h-96 rounded-card bg-line/60" />
        <div className="h-72 rounded-card bg-line/60" />
      </div>
    </div>
  );
}
