import type { CurrentUser, PermissionCode, RoleResponse, RoleSummary, UserResponse } from './types';

export const PERMISSION_LABELS: Record<PermissionCode, string> = {
  USER_READ_OWN: 'Xem hồ sơ của mình',
  USER_UPDATE_OWN: 'Sửa hồ sơ của mình, đổi mật khẩu',
  USER_READ_DEPARTMENT: 'Xem nhân sự cùng phòng ban',
  USER_UPDATE_DEPARTMENT: 'Sửa nhân sự cùng phòng, cấp thấp hơn',
  USER_READ_ALL: 'Xem toàn bộ nhân sự',
  USER_UPDATE_ALL: 'Sửa mọi thông tin, kể cả email, phòng ban',
  USER_CREATE: 'Tạo tài khoản',
  USER_DISABLE: 'Khóa / mở khóa tài khoản',
  USER_ASSIGN_ROLE: 'Gán vai trò và quyền lẻ',
  RBAC_READ: 'Xem vai trò & quyền',
  RBAC_UPDATE: 'Sửa quyền của vai trò cấp thấp hơn',
};

export const ALL_PERMISSIONS = Object.keys(PERMISSION_LABELS) as PermissionCode[];

/** True when the user holds at least one of the permissions. */
export function can(user: CurrentUser | null | undefined, ...anyOf: PermissionCode[]): boolean {
  return !!user && anyOf.some((permission) => user.permissions.includes(permission));
}

export function rankOf(user: { roles: RoleSummary[] }): number {
  return user.roles.reduce((max, role) => Math.max(max, role.rank), -1);
}

function shareDepartment(a: UserResponse, b: UserResponse): boolean {
  return a.departments.some((d) => b.departments.some((other) => other.id === d.id));
}

/*
 * Client-side mirror of UserAccessPolicy.java, used only to hide buttons the API would refuse.
 * It is never the enforcement: the server re-checks every rule on every request.
 */
export function canEditUser(me: CurrentUser, target: UserResponse): boolean {
  if (can(me, 'USER_UPDATE_ALL')) return rankOf(target) <= rankOf(me);
  return can(me, 'USER_UPDATE_DEPARTMENT') && shareDepartment(me, target) && rankOf(me) > rankOf(target);
}

export function canChangeDepartments(me: CurrentUser, target: UserResponse): boolean {
  return can(me, 'USER_UPDATE_ALL') && rankOf(target) <= rankOf(me);
}

export function canChangeStatus(me: CurrentUser, target: UserResponse): boolean {
  return me.id !== target.id && can(me, 'USER_DISABLE') && rankOf(target) <= rankOf(me);
}

export function canAssignRoles(me: CurrentUser, target: UserResponse): boolean {
  return me.id !== target.id && can(me, 'USER_ASSIGN_ROLE') && rankOf(target) <= rankOf(me);
}

/** Mirrors RoleServiceImpl: only roles ranked below me (ADMIN is locked), only permissions I hold. */
export function canEditRole(me: CurrentUser, role: RoleResponse): boolean {
  return can(me, 'RBAC_UPDATE') && role.rank < rankOf(me);
}

/** Direct permissions follow the role rules (UserAccessPolicy.checkPermissionGrant). */
export const canGrantPermissions = canAssignRoles;
