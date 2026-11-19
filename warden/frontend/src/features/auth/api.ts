import { useMutation } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';

import { useAppDispatch } from '@/app/store';
import { http } from '@/lib/http';
import { announceLogout } from '@/lib/sessionChannel';
import { queryClient, queryKeys } from '@/lib/queryClient';
import type { AuthResponse } from '@/lib/types';
import { sessionStarted, signedOut } from './authSlice';

export interface LoginBody {
  username: string;
  password: string;
}

/** Logs in (the refresh cookie is set by the server), stores the session, returns to where the user was. */
export function useLogin() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();

  return useMutation({
    mutationFn: async (body: LoginBody) => (await http.post<AuthResponse>('/auth/login', body)).data,
    onSuccess: ({ accessToken, user }) => {
      dispatch(sessionStarted({ accessToken, user }));
      queryClient.setQueryData(queryKeys.me, user);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/account', { replace: true });
    },
  });
}

/** Revokes the refresh token server-side, then forgets the session locally, even if the call fails. */
export function useLogout() {
  const dispatch = useAppDispatch();
  return useMutation({
    mutationFn: () => http.post('/auth/logout'),
    onSettled: () => {
      announceLogout();
      dispatch(signedOut());
      toast('Đã đăng xuất');
    },
  });
}
