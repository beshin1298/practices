import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { z } from 'zod';

import { useAppSelector } from '@/app/store';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Field } from '@/components/ui/Field';
import { Input } from '@/components/ui/Input';
import { PHONE_MESSAGE, PHONE_PATTERN } from '@/lib/forms';
import { applyServerErrors } from '@/lib/forms';
import { can } from '@/lib/permissions';
import type { UserResponse } from '@/lib/types';
import { useUpdateUser } from './api';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z.string().trim().regex(PHONE_PATTERN, PHONE_MESSAGE),
  jobTitle: z.string().trim().max(100, 'Chức danh tối đa 100 ký tự'),
  email: z.email('Email không đúng định dạng').max(255, 'Email tối đa 255 ký tự'),
});
type FormValues = z.infer<typeof schema>;

/** Managers (department scope) and HR (company scope); only USER_UPDATE_ALL may change the email. */
export function EditUserDialog({ user, onClose }: { user: UserResponse | null; onClose: () => void }) {
  const me = useAppSelector((state) => state.auth.user);
  const canChangeEmail = can(me, 'USER_UPDATE_ALL');
  const updateUser = useUpdateUser();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (user) reset({ fullName: user.fullName, phone: user.phone ?? '', jobTitle: user.jobTitle ?? '', email: user.email });
  }, [user, reset]);

  const onSubmit = handleSubmit((values) =>
    user &&
    updateUser.mutate(
      { id: user.id, ...values },
      {
        onSuccess: (saved) => {
          toast.success(`Đã cập nhật ${saved.fullName}`);
          onClose();
        },
        onError: (error) => applyServerErrors(error, setError, { EMAIL_TAKEN: 'email' }),
      },
    ),
  );

  return (
    <Dialog open={user !== null} onClose={onClose} title={`Sửa hồ sơ ${user?.fullName ?? ''}`} description={`@${user?.username ?? ''}`}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Họ và tên" htmlFor="edit-fullName" error={errors.fullName?.message}>
            <Input id="edit-fullName" invalid={!!errors.fullName} {...register('fullName')} />
          </Field>
          <Field label="Số điện thoại" htmlFor="edit-phone" error={errors.phone?.message}>
            <Input id="edit-phone" type="tel" invalid={!!errors.phone} {...register('phone')} />
          </Field>
          <Field label="Chức danh" htmlFor="edit-jobTitle" error={errors.jobTitle?.message}>
            <Input id="edit-jobTitle" invalid={!!errors.jobTitle} {...register('jobTitle')} />
          </Field>
          <Field
            label="Email"
            htmlFor="edit-email"
            error={errors.email?.message}
            hint={canChangeEmail ? undefined : 'Chỉ Nhân sự được đổi email'}
          >
            <Input id="edit-email" type="email" readOnly={!canChangeEmail} disabled={!canChangeEmail} invalid={!!errors.email} {...register('email')} />
          </Field>
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" loading={updateUser.isPending}>
            Lưu thay đổi
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
