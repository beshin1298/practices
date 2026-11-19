import { useMutation, useQuery } from '@tanstack/react-query';

import { useAppDispatch } from '@/app/store';
import { sessionStarted, userUpdated } from '@/features/auth/authSlice';
import { http } from '@/lib/http';
import { queryClient, queryKeys } from '@/lib/queryClient';
import type { AuthResponse, CurrentUser, Department, PermissionCode, RoleResponse } from '@/lib/types';

export interface UpdateOwnProfileBody {
  fullName: string;
  phone: string;
}

export interface ChangePasswordBody {
  currentPassword: string;
  newPassword: string;
}

/** The logged-in user. Refetched on window focus, so roles changed by an admin show up. */
export function useMe() {
  return useQuery({ queryKey: queryKeys.me, queryFn: async () => (await http.get<CurrentUser>('/users/me')).data });
}

export function useUpdateOwnProfile() {
  const dispatch = useAppDispatch();
  return useMutation({
    mutationFn: async (body: UpdateOwnProfileBody) => (await http.put<CurrentUser>('/users/me', body)).data,
    onSuccess: (user) => {
      queryClient.setQueryData(queryKeys.me, user);
      dispatch(userUpdated(user));
    },
  });
}

/** The server revokes every old session and returns a new one (new access token + new cookie). */
export function useChangePassword() {
  const dispatch = useAppDispatch();
  return useMutation({
    mutationFn: async (body: ChangePasswordBody) => (await http.put<AuthResponse>('/users/me/password', body)).data,
    onSuccess: ({ accessToken, user }) => {
      dispatch(sessionStarted({ accessToken, user }));
      queryClient.setQueryData(queryKeys.me, user);
    },
  });
}

/** Reference data: changes rarely, so it is cached for the whole session. */
export function useDepartments() {
  return useQuery({
    queryKey: queryKeys.departments,
    queryFn: async () => (await http.get<Department[]>('/departments')).data,
    staleTime: Infinity,
  });
}

/** RBAC_READ only (HR, Admin): pass enabled=false where the caller may not hold it. */
export function useRoles(enabled = true) {
  return useQuery({
    queryKey: queryKeys.roles,
    queryFn: async () => (await http.get<RoleResponse[]>('/roles')).data,
    enabled,
  });
}

/** Replaces one role's permissions; holders get the change on their next request. */
export function useUpdateRolePermissions() {
  return useMutation({
    mutationFn: async ({ id, permissions }: { id: number; permissions: PermissionCode[] }) =>
      (await http.put<RoleResponse>(`/roles/${id}/permissions`, { permissions })).data,
    onSuccess: (updated) => {
      queryClient.setQueryData<RoleResponse[]>(queryKeys.roles, (roles) =>
        roles?.map((role) => (role.id === updated.id ? updated : role)),
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.me });
    },
  });
}
