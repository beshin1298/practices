import { Check, Lock } from 'lucide-react';
import { toast } from 'sonner';

import { useAppSelector } from '@/app/store';
import { PageHeader } from '@/components/layout/PageHeader';
import { Card } from '@/components/ui/Card';
import { useRoles, useUpdateRolePermissions } from '@/features/account/api';
import { cn } from '@/lib/cn';
import { toastApiError } from '@/lib/forms';
import { ALL_PERMISSIONS, PERMISSION_LABELS, can, canEditRole } from '@/lib/permissions';
import type { PermissionCode, RoleResponse } from '@/lib/types';

/**
 * The roles <-> permissions many-to-many table, straight from the database. Holders of RBAC_UPDATE
 * click a cell to toggle it, for roles ranked below them and permissions they hold; the server
 * enforces the same rules (RoleServiceImpl).
 */
export function RolesPage() {
  const me = useAppSelector((state) => state.auth.user);
  const { data: roles = [], isPending } = useRoles();
  const update = useUpdateRolePermissions();
  const canEdit = can(me, 'RBAC_UPDATE');

  const toggle = (role: RoleResponse, code: PermissionCode) => {
    const has = role.permissions.includes(code);
    const permissions = has ? role.permissions.filter((p) => p !== code) : [...role.permissions, code];
    update.mutate(
      { id: role.id, permissions },
      {
        onSuccess: () => toast.success(`${has ? 'Đã bỏ' : 'Đã thêm'} “${PERMISSION_LABELS[code]}” ${has ? 'khỏi' : 'cho'} ${role.name}`),
        onError: toastApiError,
      },
    );
  };

  return (
    <>
      <PageHeader
        title="Vai trò & quyền"
        description={
          canEdit
            ? 'Bấm vào ô để bật/tắt quyền của vai trò cấp thấp hơn bạn. Người giữ vai trò nhận thay đổi ngay ở request kế tiếp.'
            : 'Mỗi vai trò là một tập quyền CRUD theo phạm vi; một người có thể giữ nhiều vai trò và nhận hợp các quyền.'
        }
      />
      <Card className="overflow-x-auto">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-canvas/70">
            <tr>
              <th scope="col" className="px-4 py-3 text-xs font-medium text-muted">
                Quyền
              </th>
              {roles.map((role) => (
                <th key={role.id} scope="col" className="px-4 py-3 text-center text-xs font-medium text-muted">
                  <span className="block text-sm font-semibold text-ink">{role.name}</span>
                  <span className="inline-flex items-center gap-1 font-mono text-[11px]">
                    {canEdit && me && !canEditRole(me, role) && <Lock className="size-3" aria-label="Không sửa được" />}
                    {role.code}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {isPending && (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-muted">
                  Đang tải…
                </td>
              </tr>
            )}
            {!isPending &&
              ALL_PERMISSIONS.map((permission) => (
                <tr key={permission} className="hover:bg-canvas/60">
                  <th scope="row" className="px-4 py-3 font-normal">
                    <span className="block font-medium text-ink">{PERMISSION_LABELS[permission]}</span>
                    <span className="font-mono text-[11px] text-muted">{permission}</span>
                  </th>
                  {roles.map((role) => {
                    const has = role.permissions.includes(permission);
                    if (me && canEditRole(me, role)) {
                      const mine = me.permissions.includes(permission);
                      return (
                        <td key={role.id} className="px-4 py-3 text-center">
                          <button
                            type="button"
                            role="checkbox"
                            aria-checked={has}
                            aria-label={`${PERMISSION_LABELS[permission]} cho ${role.name}`}
                            title={mine ? undefined : 'Bạn không có quyền này nên không bật/tắt được'}
                            disabled={!mine || update.isPending}
                            onClick={() => toggle(role, permission)}
                            className={cn(
                              'mx-auto flex size-5 items-center justify-center rounded border transition-colors disabled:cursor-not-allowed disabled:opacity-50',
                              has ? 'border-brand-700 bg-brand-700 text-white' : 'border-line-strong bg-surface hover:border-brand-500',
                            )}
                          >
                            {has && <Check className="size-3.5" strokeWidth={3} />}
                          </button>
                        </td>
                      );
                    }
                    return (
                      <td key={role.id} className="px-4 py-3 text-center">
                        {has ? (
                          <Check className="mx-auto size-4 text-success-700" aria-label="Có" />
                        ) : (
                          <span className="text-line-strong" aria-label="Không">
                            —
                          </span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
          </tbody>
        </table>
      </Card>
      <p className="mt-4 text-[13px] text-muted">
        Ngoài quyền, mọi thao tác còn qua luật phạm vi: quản lý chỉ tác động người cùng phòng có cấp thấp hơn; không ai tác động người có cấp
        cao hơn mình hoặc gán vai trò cao hơn vai trò của mình. Một người còn có thể được gán quyền lẻ ở trang Nhân sự; quyền lẻ cộng thêm vào
        quyền từ vai trò và không đổi cấp. Vai trò Quản trị hệ thống luôn bị khóa.
      </p>
    </>
  );
}
