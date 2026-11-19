// Mirrors the backend DTOs (Schema tab of the Warden docs, "API spec").

export type PermissionCode =
  | 'USER_READ_OWN'
  | 'USER_UPDATE_OWN'
  | 'USER_READ_DEPARTMENT'
  | 'USER_UPDATE_DEPARTMENT'
  | 'USER_READ_ALL'
  | 'USER_UPDATE_ALL'
  | 'USER_CREATE'
  | 'USER_DISABLE'
  | 'USER_ASSIGN_ROLE'
  | 'RBAC_READ'
  | 'RBAC_UPDATE';

export interface RoleSummary {
  id: number;
  code: string;
  name: string;
  rank: number;
}

export interface RoleResponse extends RoleSummary {
  permissions: PermissionCode[];
}

export interface Department {
  id: number;
  code: string;
  name: string;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  phone: string | null;
  jobTitle: string | null;
  roles: RoleSummary[]; // many-to-many, highest rank first
  directPermissions: PermissionCode[]; // "quyền lẻ": on top of the roles, never change the rank
  departments: Department[]; // many-to-many
  enabled: boolean;
  lastLoginAt: string | null; // ISO-8601 UTC
  passwordChangedAt: string | null;
  createdAt: string;
}

/** The logged-in user: plus the effective permissions (union over roles and direct permissions). */
export interface CurrentUser extends UserResponse {
  permissions: PermissionCode[];
}

export interface AuthResponse {
  accessToken: string; // 15-minute JWT, kept in memory only
  tokenType: 'Bearer';
  expiresIn: number; // seconds
  user: CurrentUser;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 9457 ProblemDetail + our stable `code`. */
export interface ApiProblem {
  status: number;
  title?: string;
  detail?: string;
  code: string;
  errors?: Record<string, string>;
}

export type SignOutReason =
  | 'TOKEN_EXPIRED'
  | 'TOKEN_REVOKED'
  | 'TOKEN_INVALID'
  | 'REFRESH_TOKEN_INVALID'
  | 'REFRESH_TOKEN_EXPIRED'
  | 'REFRESH_TOKEN_REUSED';
