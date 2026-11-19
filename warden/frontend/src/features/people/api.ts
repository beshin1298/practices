import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';

import { http } from '@/lib/http';
import { queryClient, queryKeys } from '@/lib/queryClient';
import type { PageResponse, PermissionCode, UserResponse } from '@/lib/types';

export type SortField = 'fullName' | 'email' | 'createdAt' | 'lastLoginAt';

export interface UserListParams {
  q: string;
  departmentId: number | null;
  page: number;
  size: number;
  sort: SortField;
  direction: 'asc' | 'desc';
}

export interface CreateUserBody {
  username: string;
  email: string;
  fullName: string;
  phone: string;
  jobTitle: string;
  password: string;
  roleIds: number[];
  departmentIds: number[];
}

export interface UpdateUserBody {
  fullName: string;
  phone: string;
  jobTitle: string;
  email: string;
}

const peopleApi = {
  list: async ({ q, departmentId, ...rest }: UserListParams) =>
    (
      await http.get<PageResponse<UserResponse>>('/users', {
        params: { ...rest, q: q || undefined, departmentId: departmentId ?? undefined },
      })
    ).data,
  create: async (body: CreateUserBody) => (await http.post<UserResponse>('/users', body)).data,
  update: async ({ id, ...body }: UpdateUserBody & { id: number }) => (await http.put<UserResponse>(`/users/${id}`, body)).data,
  assignDepartments: async ({ id, departmentIds }: { id: number; departmentIds: number[] }) =>
    (await http.put<UserResponse>(`/users/${id}/departments`, { departmentIds })).data,
  assignRoles: async ({ id, roleIds }: { id: number; roleIds: number[] }) =>
    (await http.put<UserResponse>(`/users/${id}/roles`, { roleIds })).data,
  assignPermissions: async ({ id, permissions }: { id: number; permissions: PermissionCode[] }) =>
    (await http.put<UserResponse>(`/users/${id}/permissions`, { permissions })).data,
  changeStatus: async ({ id, enabled }: { id: number; enabled: boolean }) =>
    (await http.patch<UserResponse>(`/users/${id}/status`, { enabled })).data,
};

/** Server-side paging, sorting, search and department scope; keeps the previous page while loading. */
export function useUsers(params: UserListParams) {
  return useQuery({
    queryKey: queryKeys.users(params),
    queryFn: () => peopleApi.list(params),
    placeholderData: keepPreviousData,
  });
}

/** Any change can move a row between pages or filters: refetch the lists (and "me", it may be me). */
const refresh = () => {
  void queryClient.invalidateQueries({ queryKey: queryKeys.usersAll });
  void queryClient.invalidateQueries({ queryKey: queryKeys.me });
};

export const useCreateUser = () => useMutation({ mutationFn: peopleApi.create, onSuccess: refresh });
export const useUpdateUser = () => useMutation({ mutationFn: peopleApi.update, onSuccess: refresh });
export const useAssignDepartments = () => useMutation({ mutationFn: peopleApi.assignDepartments, onSuccess: refresh });
export const useAssignRoles = () => useMutation({ mutationFn: peopleApi.assignRoles, onSuccess: refresh });
export const useAssignPermissions = () => useMutation({ mutationFn: peopleApi.assignPermissions, onSuccess: refresh });
export const useChangeStatus = () => useMutation({ mutationFn: peopleApi.changeStatus, onSuccess: refresh });
