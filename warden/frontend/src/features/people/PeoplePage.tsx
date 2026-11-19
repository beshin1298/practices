import { useEffect, useMemo, useState } from 'react';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
  type PaginationState,
  type SortingState,
} from '@tanstack/react-table';
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Building2,
  ChevronLeft,
  ChevronRight,
  Lock,
  LockOpen,
  KeyRound,
  Pencil,
  Search,
  ShieldCheck,
  UserPlus,
} from 'lucide-react';
import { toast } from 'sonner';

import { useAppSelector } from '@/app/store';
import { PageHeader } from '@/components/layout/PageHeader';
import { Alert } from '@/components/ui/Alert';
import { Avatar } from '@/components/ui/Avatar';
import { DepartmentBadges, RoleBadges, StatusBadge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Input, Select } from '@/components/ui/Input';
import { useDepartments } from '@/features/account/api';
import { cn } from '@/lib/cn';
import { toastApiError } from '@/lib/forms';
import { formatDateTime, formatRelative } from '@/lib/format';
import { can, canAssignRoles, canChangeDepartments, canChangeStatus, canEditUser, canGrantPermissions } from '@/lib/permissions';
import type { UserResponse } from '@/lib/types';
import { useChangeStatus, useUsers, type SortField } from './api';
import { AssignDialog, type AssignKind } from './AssignDialog';
import { CreateUserDialog } from './CreateUserDialog';
import { EditUserDialog } from './EditUserDialog';

const column = createColumnHelper<UserResponse>();

function useDebounced<T>(value: T, delayMs: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}

export function PeoplePage() {
  const me = useAppSelector((state) => state.auth.user)!;
  const companyWide = can(me, 'USER_READ_ALL');
  const { data: allDepartments = [] } = useDepartments();
  const departmentOptions = companyWide ? allDepartments : me.departments;

  const [search, setSearch] = useState('');
  const query = useDebounced(search.trim(), 300);
  const [departmentId, setDepartmentId] = useState<number | null>(null);
  const [sorting, setSorting] = useState<SortingState>([{ id: 'createdAt', desc: true }]);
  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 10 });
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<UserResponse | null>(null);
  const [assigning, setAssigning] = useState<{ user: UserResponse; kind: AssignKind } | null>(null);
  const [toLock, setToLock] = useState<UserResponse | null>(null);

  // A new search or filter starts from the first page.
  useEffect(() => setPagination((p) => ({ ...p, pageIndex: 0 })), [query, departmentId]);

  const { data, isPending, isFetching } = useUsers({
    q: query,
    departmentId,
    page: pagination.pageIndex,
    size: pagination.pageSize,
    sort: (sorting[0]?.id ?? 'createdAt') as SortField,
    direction: sorting[0]?.desc === false ? 'asc' : 'desc',
  });
  const changeStatus = useChangeStatus();

  const columns = useMemo(
    () => [
      column.accessor('fullName', {
        header: 'Nhân sự',
        cell: ({ row }) => (
          <div className="flex items-center gap-3">
            <Avatar name={row.original.fullName} size="sm" />
            <div className="min-w-0">
              <p className="truncate font-medium text-ink">
                {row.original.fullName}
                {row.original.id === me.id && <span className="ml-2 text-xs font-normal text-muted">(bạn)</span>}
              </p>
              <p className="truncate text-xs text-muted">
                @{row.original.username}
                {row.original.jobTitle && ` · ${row.original.jobTitle}`}
              </p>
            </div>
          </div>
        ),
      }),
      column.accessor('departments', {
        header: 'Phòng ban',
        enableSorting: false,
        cell: (info) => <DepartmentBadges departments={info.getValue()} />,
      }),
      column.accessor('roles', {
        header: 'Vai trò',
        enableSorting: false,
        cell: (info) => (
          <div className="flex flex-col items-start gap-1">
            <RoleBadges roles={info.getValue()} />
            {info.row.original.directPermissions.length > 0 && (
              <span className="text-xs text-muted">+{info.row.original.directPermissions.length} quyền lẻ</span>
            )}
          </div>
        ),
      }),
      column.accessor('enabled', {
        header: 'Trạng thái',
        enableSorting: false,
        cell: (info) => <StatusBadge enabled={info.getValue()} />,
      }),
      column.accessor('lastLoginAt', {
        header: 'Đăng nhập gần nhất',
        cell: (info) => (
          <span className="text-ink-soft" title={formatDateTime(info.getValue())}>
            {formatRelative(info.getValue())}
          </span>
        ),
      }),
      column.accessor('createdAt', {
        header: 'Ngày tạo',
        cell: (info) => <span className="text-ink-soft">{formatDateTime(info.getValue())}</span>,
      }),
      column.display({
        id: 'actions',
        header: () => <span className="sr-only">Thao tác</span>,
        cell: ({ row }) => {
          const user = row.original;
          return (
            <div className="flex justify-end gap-1">
              {canEditUser(me, user) && (
                <IconAction label="Sửa hồ sơ" onClick={() => setEditing(user)} icon={<Pencil className="size-4" />} />
              )}
              {canChangeDepartments(me, user) && (
                <IconAction label="Phòng ban" onClick={() => setAssigning({ user, kind: 'departments' })} icon={<Building2 className="size-4" />} />
              )}
              {canAssignRoles(me, user) && (
                <IconAction label="Vai trò" onClick={() => setAssigning({ user, kind: 'roles' })} icon={<ShieldCheck className="size-4" />} />
              )}
              {canGrantPermissions(me, user) && (
                <IconAction label="Quyền lẻ" onClick={() => setAssigning({ user, kind: 'permissions' })} icon={<KeyRound className="size-4" />} />
              )}
              {canChangeStatus(me, user) &&
                (user.enabled ? (
                  <IconAction label="Khóa tài khoản" danger onClick={() => setToLock(user)} icon={<Lock className="size-4" />} />
                ) : (
                  <IconAction
                    label="Mở khóa"
                    onClick={() =>
                      changeStatus.mutate(
                        { id: user.id, enabled: true },
                        { onSuccess: (u) => toast.success(`Đã mở khóa ${u.username}`), onError: toastApiError },
                      )
                    }
                    icon={<LockOpen className="size-4" />}
                  />
                ))}
            </div>
          );
        },
      }),
    ],
    [me, changeStatus],
  );

  const table = useReactTable({
    data: data?.content ?? [],
    columns,
    state: { sorting, pagination },
    onSortingChange: setSorting,
    onPaginationChange: setPagination,
    manualSorting: true,
    manualPagination: true,
    enableSortingRemoval: false,
    pageCount: data?.totalPages ?? -1,
    getRowId: (row) => String(row.id),
    getCoreRowModel: getCoreRowModel(),
  });

  const total = data?.totalElements ?? 0;
  const from = total === 0 ? 0 : pagination.pageIndex * pagination.pageSize + 1;
  const to = Math.min(total, (pagination.pageIndex + 1) * pagination.pageSize);

  return (
    <>
      <PageHeader
        title="Nhân sự"
        description={companyWide ? 'Toàn bộ nhân sự công ty.' : `Nhân sự thuộc ${me.departments.map((d) => d.name).join(', ')}.`}
        action={
          can(me, 'USER_CREATE') && (
            <Button onClick={() => setCreating(true)}>
              <UserPlus className="size-4" aria-hidden /> Tạo tài khoản
            </Button>
          )
        }
      />

      {!companyWide && (
        <div className="mb-4">
          <Alert>Bạn là quản lý: chỉ thấy và sửa được nhân sự cùng phòng ban có cấp thấp hơn. Email, phòng ban và vai trò do Nhân sự quản lý.</Alert>
        </div>
      )}

      <Card>
        <div className="flex flex-col gap-3 border-b border-line p-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative w-full sm:w-72">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" aria-hidden />
              <Input
                type="search"
                placeholder="Tìm theo tên, username, email"
                aria-label="Tìm nhân sự"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              aria-label="Lọc theo phòng ban"
              value={departmentId ?? ''}
              onChange={(event) => setDepartmentId(event.target.value ? Number(event.target.value) : null)}
              className="sm:w-56"
            >
              <option value="">{companyWide ? 'Tất cả phòng ban' : 'Các phòng của tôi'}</option>
              {departmentOptions.map((department) => (
                <option key={department.id} value={department.id}>
                  {department.name}
                </option>
              ))}
            </Select>
          </div>
          <p className="text-sm text-muted">
            {total} nhân sự {isFetching && !isPending && <span className="ml-2 text-brand-700">Đang tải…</span>}
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[960px] text-left">
            <thead className="bg-canvas/70">
              {table.getHeaderGroups().map((group) => (
                <tr key={group.id}>
                  {group.headers.map((header) => {
                    const sorted = header.column.getIsSorted();
                    return (
                      <th key={header.id} scope="col" className="px-4 py-3 text-xs font-medium whitespace-nowrap text-muted">
                        {header.column.getCanSort() ? (
                          <button
                            type="button"
                            onClick={header.column.getToggleSortingHandler()}
                            className={cn('inline-flex items-center gap-1 hover:text-ink', sorted && 'text-ink')}
                          >
                            {flexRender(header.column.columnDef.header, header.getContext())}
                            {sorted === 'asc' ? (
                              <ArrowUp className="size-3.5" />
                            ) : sorted === 'desc' ? (
                              <ArrowDown className="size-3.5" />
                            ) : (
                              <ArrowUpDown className="size-3.5 opacity-50" />
                            )}
                          </button>
                        ) : (
                          flexRender(header.column.columnDef.header, header.getContext())
                        )}
                      </th>
                    );
                  })}
                </tr>
              ))}
            </thead>
            <tbody className="divide-y divide-line">
              {isPending &&
                Array.from({ length: 5 }, (_, i) => (
                  <tr key={i}>
                    <td colSpan={columns.length} className="px-4 py-4">
                      <div className="h-6 animate-pulse rounded-control bg-line/70" />
                    </td>
                  </tr>
                ))}
              {!isPending && table.getRowModel().rows.length === 0 && (
                <tr>
                  <td colSpan={columns.length} className="px-4 py-16 text-center text-muted">
                    Không tìm thấy nhân sự nào{query && <> khớp với “{query}”</>}.
                  </td>
                </tr>
              )}
              {table.getRowModel().rows.map((row) => (
                <tr key={row.id} className={cn('hover:bg-canvas/60', !row.original.enabled && 'bg-canvas/40')}>
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-4 py-3 align-middle whitespace-nowrap">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex flex-col gap-3 border-t border-line px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-muted">
            Hiển thị {from}–{to} trên {total}
          </p>
          <div className="flex items-center gap-2">
            <Select
              aria-label="Số dòng mỗi trang"
              value={pagination.pageSize}
              onChange={(event) => setPagination({ pageIndex: 0, pageSize: Number(event.target.value) })}
              className="h-8 w-28 text-[13px]"
            >
              {[10, 20, 50].map((size) => (
                <option key={size} value={size}>
                  {size} / trang
                </option>
              ))}
            </Select>
            <Button size="sm" variant="secondary" onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()} aria-label="Trang trước">
              <ChevronLeft className="size-4" />
            </Button>
            <span className="min-w-20 text-center text-sm text-ink-soft">
              Trang {pagination.pageIndex + 1}/{Math.max(table.getPageCount(), 1)}
            </span>
            <Button size="sm" variant="secondary" onClick={() => table.nextPage()} disabled={!table.getCanNextPage()} aria-label="Trang sau">
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      </Card>

      <CreateUserDialog open={creating} onClose={() => setCreating(false)} />
      <EditUserDialog user={editing} onClose={() => setEditing(null)} />
      <AssignDialog user={assigning?.user ?? null} kind={assigning?.kind ?? 'roles'} onClose={() => setAssigning(null)} />

      <Dialog
        open={toLock !== null}
        onClose={() => setToLock(null)}
        title={`Khóa tài khoản ${toLock?.username ?? ''}?`}
        description="Người dùng bị đăng xuất ngay trên mọi thiết bị (access và refresh token đều bị thu hồi) và không thể đăng nhập cho tới khi được mở khóa."
      >
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setToLock(null)}>
            Hủy
          </Button>
          <Button
            variant="danger"
            loading={changeStatus.isPending}
            onClick={() =>
              toLock &&
              changeStatus.mutate(
                { id: toLock.id, enabled: false },
                {
                  onSuccess: (u) => {
                    toast.success(`Đã khóa ${u.username}`);
                    setToLock(null);
                  },
                  onError: toastApiError,
                },
              )
            }
          >
            Khóa tài khoản
          </Button>
        </div>
      </Dialog>
    </>
  );
}

function IconAction({ label, icon, onClick, danger }: { label: string; icon: React.ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className={cn(
        'rounded-control p-2 text-muted transition-colors hover:bg-canvas',
        danger ? 'hover:text-danger-600' : 'hover:text-brand-700',
      )}
    >
      {icon}
    </button>
  );
}
