import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { z } from 'zod';

import { useAppSelector } from '@/app/store';
import { Button } from '@/components/ui/Button';
import { CheckboxList } from '@/components/ui/CheckboxList';
import { Dialog } from '@/components/ui/Dialog';
import { Field } from '@/components/ui/Field';
import { Input, PasswordInput } from '@/components/ui/Input';
import { useDepartments, useRoles } from '@/features/account/api';
import { PHONE_MESSAGE, PHONE_PATTERN } from '@/lib/forms';
import { applyServerErrors } from '@/lib/forms';
import { passwordSchema } from '@/lib/password';
import { rankOf } from '@/lib/permissions';
import { useCreateUser } from './api';

const schema = z.object({
  username: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9._-]{3,50}$/, 'Tên đăng nhập 3-50 ký tự, chỉ gồm chữ không dấu, số, dấu . _ -'),
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(100, 'Họ tên tối đa 100 ký tự'),
  email: z.email('Email không đúng định dạng').max(255, 'Email tối đa 255 ký tự'),
  phone: z.string().trim().regex(PHONE_PATTERN, PHONE_MESSAGE),
  jobTitle: z.string().trim().max(100, 'Chức danh tối đa 100 ký tự'),
  password: passwordSchema,
  roleIds: z.array(z.number()).min(1, 'Chọn ít nhất một vai trò'),
  departmentIds: z.array(z.number()).min(1, 'Chọn ít nhất một phòng ban'),
});
type FormValues = z.infer<typeof schema>;

const EMPTY: FormValues = { username: '', fullName: '', email: '', phone: '', jobTitle: '', password: '', roleIds: [], departmentIds: [] };

/** Internal app: accounts are created by HR / admins, never by self-registration. */
export function CreateUserDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const me = useAppSelector((state) => state.auth.user);
  const { data: roles = [] } = useRoles(open);
  const { data: departments = [] } = useDepartments();
  const createUser = useCreateUser();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: EMPTY });

  const myRank = me ? rankOf(me) : -1;
  const close = () => {
    reset(EMPTY);
    createUser.reset();
    onClose();
  };

  const onSubmit = handleSubmit((values) =>
    createUser.mutate(values, {
      onSuccess: (user) => {
        toast.success(`Đã tạo tài khoản ${user.username}`);
        close();
      },
      onError: (error) => applyServerErrors(error, setError, { USERNAME_TAKEN: 'username', EMAIL_TAKEN: 'email' }),
    }),
  );

  return (
    <Dialog open={open} onClose={close} title="Tạo tài khoản" description="App nội bộ: tài khoản do Nhân sự tạo và gán vai trò, phòng ban.">
      <form onSubmit={onSubmit} noValidate className="max-h-[70vh] space-y-4 overflow-y-auto pr-1">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Tên đăng nhập" htmlFor="new-username" error={errors.username?.message}>
            <Input id="new-username" autoComplete="off" invalid={!!errors.username} {...register('username')} />
          </Field>
          <Field label="Họ và tên" htmlFor="new-fullName" error={errors.fullName?.message}>
            <Input id="new-fullName" invalid={!!errors.fullName} {...register('fullName')} />
          </Field>
          <Field label="Email" htmlFor="new-email" error={errors.email?.message}>
            <Input id="new-email" type="email" invalid={!!errors.email} {...register('email')} />
          </Field>
          <Field label="Số điện thoại" htmlFor="new-phone" error={errors.phone?.message}>
            <Input id="new-phone" type="tel" invalid={!!errors.phone} {...register('phone')} />
          </Field>
          <Field label="Chức danh" htmlFor="new-jobTitle" error={errors.jobTitle?.message}>
            <Input id="new-jobTitle" invalid={!!errors.jobTitle} {...register('jobTitle')} />
          </Field>
          <Field label="Mật khẩu ban đầu" htmlFor="new-password" error={errors.password?.message} hint="Ít nhất 8 ký tự, có chữ và số">
            <PasswordInput id="new-password" autoComplete="new-password" invalid={!!errors.password} {...register('password')} />
          </Field>
        </div>

        <Field label="Vai trò" htmlFor="new-roles" error={errors.roleIds?.message}>
          <Controller
            control={control}
            name="roleIds"
            render={({ field }) => (
              <CheckboxList
                label="Vai trò"
                value={field.value}
                onChange={field.onChange}
                invalid={!!errors.roleIds}
                options={roles.map((role) => ({
                  value: role.id,
                  label: role.name,
                  hint: role.rank > myRank ? 'Cao hơn cấp của bạn' : `${role.permissions.length} quyền`,
                  disabled: role.rank > myRank,
                }))}
              />
            )}
          />
        </Field>

        <Field label="Phòng ban" htmlFor="new-departments" error={errors.departmentIds?.message}>
          <Controller
            control={control}
            name="departmentIds"
            render={({ field }) => (
              <CheckboxList
                label="Phòng ban"
                value={field.value}
                onChange={field.onChange}
                invalid={!!errors.departmentIds}
                options={departments.map((department) => ({ value: department.id, label: department.name, hint: department.code }))}
              />
            )}
          />
        </Field>

        <div className="flex justify-end gap-3 pt-2">
          <Button variant="secondary" onClick={close}>
            Hủy
          </Button>
          <Button type="submit" loading={createUser.isPending}>
            Tạo tài khoản
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
