import { useEffect, useState } from 'react';
import { toast } from 'sonner';

import { useAppSelector } from '@/app/store';
import { Button } from '@/components/ui/Button';
import { CheckboxList } from '@/components/ui/CheckboxList';
import { Dialog } from '@/components/ui/Dialog';
import { useDepartments, useRoles } from '@/features/account/api';
import { toastApiError } from '@/lib/forms';
import { ALL_PERMISSIONS, PERMISSION_LABELS, rankOf } from '@/lib/permissions';
import type { PermissionCode, UserResponse } from '@/lib/types';
import { useAssignDepartments, useAssignPermissions, useAssignRoles } from './api';

export type AssignKind = 'roles' | 'departments' | 'permissions';

const TEXT: Record<AssignKind, { title: string; description: string; saved: string }> = {
  roles: {
    title: 'Vai trò',
    description: 'Quyền hiệu lực là hợp các quyền của mọi vai trò được chọn cộng quyền lẻ. Có hiệu lực ngay ở request kế tiếp.',
    saved: 'Đã cập nhật vai trò',
  },
  departments: {
    title: 'Phòng ban',
    description: 'Một người có thể thuộc nhiều phòng ban (kiêm nhiệm).',
    saved: 'Đã cập nhật phòng ban',
  },
  permissions: {
    title: 'Quyền lẻ',
    description: 'Cộng thêm vào quyền từ vai trò, không đổi cấp của người này. Bạn chỉ bật/tắt được quyền mình đang có.',
    saved: 'Đã cập nhật quyền lẻ',
  },
};

/** Replaces a user's roles, departments or direct permissions (all many-to-many) with the checked set. */
export function AssignDialog({ user, kind, onClose }: { user: UserResponse | null; kind: AssignKind; onClose: () => void }) {
  const me = useAppSelector((state) => state.auth.user);
  const { data: roles = [] } = useRoles(user !== null && kind !== 'departments');
  const { data: departments = [] } = useDepartments();
  const assignRoles = useAssignRoles();
  const assignDepartments = useAssignDepartments();
  const assignPermissions = useAssignPermissions();
  const [selected, setSelected] = useState<number[]>([]);
  const [codes, setCodes] = useState<PermissionCode[]>([]);

  useEffect(() => {
    if (!user) return;
    if (kind === 'permissions') setCodes(user.directPermissions);
    else setSelected((kind === 'roles' ? user.roles : user.departments).map((item) => item.id));
  }, [user, kind]);

  const myRank = me ? rankOf(me) : -1;
  const options =
    kind === 'roles'
      ? roles.map((role) => ({
          value: role.id,
          label: role.name,
          hint: role.rank > myRank ? 'Cao hơn cấp của bạn' : `${role.permissions.length} quyền`,
          disabled: role.rank > myRank,
        }))
      : departments.map((department) => ({ value: department.id, label: department.name, hint: department.code }));

  // What the target already gets from a role: a direct copy would add nothing, so say so.
  const fromRoles = new Set(roles.filter((role) => user?.roles.some((r) => r.id === role.id)).flatMap((role) => role.permissions));
  const permissionOptions = ALL_PERMISSIONS.map((code) => {
    const mine = me?.permissions.includes(code) ?? false;
    return {
      value: code,
      label: PERMISSION_LABELS[code],
      hint: !mine ? 'Bạn không có quyền này' : fromRoles.has(code) ? 'Đã có từ vai trò' : code,
      disabled: !mine,
    };
  });

  const pending = assignRoles.isPending || assignDepartments.isPending || assignPermissions.isPending;
  const empty = kind !== 'permissions' && selected.length === 0;
  const save = () => {
    if (!user) return;
    const done = {
      onSuccess: () => {
        toast.success(TEXT[kind].saved);
        onClose();
      },
      onError: toastApiError,
    };
    if (kind === 'roles') assignRoles.mutate({ id: user.id, roleIds: selected }, done);
    else if (kind === 'departments') assignDepartments.mutate({ id: user.id, departmentIds: selected }, done);
    else assignPermissions.mutate({ id: user.id, permissions: codes }, done);
  };

  return (
    <Dialog open={user !== null} onClose={onClose} title={`${TEXT[kind].title} của ${user?.fullName ?? ''}`} description={TEXT[kind].description}>
      {kind === 'permissions' ? (
        <CheckboxList label="Quyền lẻ" options={permissionOptions} value={codes} onChange={setCodes} />
      ) : (
        <CheckboxList label={TEXT[kind].title} options={options} value={selected} onChange={setSelected} />
      )}
      {empty && <p className="mt-2 text-[13px] text-danger-600">Chọn ít nhất một mục.</p>}
      <div className="mt-6 flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>
          Hủy
        </Button>
        <Button onClick={save} disabled={empty} loading={pending}>
          Lưu
        </Button>
      </div>
    </Dialog>
  );
}
