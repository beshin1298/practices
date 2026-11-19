import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Check, Circle } from 'lucide-react';
import { toast } from 'sonner';
import { z } from 'zod';

import { PageHeader } from '@/components/layout/PageHeader';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/Input';
import { cn } from '@/lib/cn';
import { applyServerErrors } from '@/lib/forms';
import { passwordChecklist, passwordSchema } from '@/lib/password';
import { useChangePassword } from './api';

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Vui lòng nhập lại mật khẩu mới'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    message: 'Mật khẩu mới phải khác mật khẩu hiện tại',
    path: ['newPassword'],
  });
type FormValues = z.infer<typeof schema>;

const EMPTY: FormValues = { currentPassword: '', newPassword: '', confirmPassword: '' };

export function ChangePasswordPage() {
  const changePassword = useChangePassword();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: EMPTY });
  const newPassword = useWatch({ control, name: 'newPassword' });

  const onSubmit = handleSubmit(({ currentPassword, newPassword }) =>
    changePassword.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          reset(EMPTY);
          toast.success('Đã đổi mật khẩu', { description: 'Các thiết bị khác đã bị đăng xuất.' });
        },
        onError: (error) =>
          applyServerErrors(error, setError, {
            WRONG_CURRENT_PASSWORD: 'currentPassword',
            PASSWORD_REUSED: 'newPassword',
          }),
      },
    ),
  );

  return (
    <>
      <PageHeader title="Đổi mật khẩu" description="Dùng mật khẩu bạn chưa dùng ở nơi khác." />

      <Card className="max-w-2xl">
        <CardHeader title="Mật khẩu" description="Phiên hiện tại vẫn tiếp tục; mọi phiên khác sẽ phải đăng nhập lại." />
        <form onSubmit={onSubmit} noValidate>
          <div className="space-y-5 px-6 py-6">
            <Field label="Mật khẩu hiện tại" htmlFor="currentPassword" error={errors.currentPassword?.message}>
              <PasswordInput
                id="currentPassword"
                autoComplete="current-password"
                invalid={!!errors.currentPassword}
                {...register('currentPassword')}
              />
            </Field>

            <Field label="Mật khẩu mới" htmlFor="newPassword" error={errors.newPassword?.message}>
              <PasswordInput id="newPassword" autoComplete="new-password" invalid={!!errors.newPassword} {...register('newPassword')} />
            </Field>
            <ul className="grid gap-2 sm:grid-cols-3" aria-label="Yêu cầu mật khẩu">
              {passwordChecklist.map((rule) => {
                const ok = rule.test(newPassword ?? '');
                return (
                  <li key={rule.id} className={cn('flex items-center gap-2 text-[13px]', ok ? 'text-success-700' : 'text-muted')}>
                    {ok ? <Check className="size-4" aria-hidden /> : <Circle className="size-3.5" aria-hidden />}
                    {rule.label}
                  </li>
                );
              })}
            </ul>

            <Field label="Nhập lại mật khẩu mới" htmlFor="confirmPassword" error={errors.confirmPassword?.message}>
              <PasswordInput
                id="confirmPassword"
                autoComplete="new-password"
                invalid={!!errors.confirmPassword}
                {...register('confirmPassword')}
              />
            </Field>

            <Alert>Sau khi đổi, mọi phiên đăng nhập khác (access token và refresh token) bị thu hồi ngay; trình duyệt này nhận phiên mới và không bị đăng xuất.</Alert>
          </div>
          <div className="flex justify-end border-t border-line px-6 py-4">
            <Button type="submit" loading={changePassword.isPending}>
              Đổi mật khẩu
            </Button>
          </div>
        </form>
      </Card>
    </>
  );
}
