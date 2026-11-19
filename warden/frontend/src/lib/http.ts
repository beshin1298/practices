import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

import { store } from '@/app/store';
import { signedOut } from '@/features/auth/authSlice';
import { isApiError, toApiError } from './errors';
import { refreshSession } from './session';
import type { ApiProblem, SignOutReason } from './types';

export { ApiError, isApiError } from './errors';

/** All API calls go through this instance: it attaches the token and recovers expired sessions. */
export const http = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

http.interceptors.request.use((config) => {
  const token = store.getState().auth.accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/** Access-token problems a refresh can fix (expired, or revoked because this tab changed the password). */
const RECOVERABLE = new Set(['TOKEN_EXPIRED', 'TOKEN_REVOKED', 'TOKEN_INVALID']);

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiProblem>) => {
    const original = error.config as RetriableConfig | undefined;
    const code = error.response?.data?.code;
    const hadToken = Boolean(original?.headers?.Authorization);

    // 401 on a call made WITH a token: refresh once, then replay the original request.
    // (A 401 on /auth/login is just wrong credentials and goes back to the form.)
    if (error.response?.status === 401 && hadToken && original && !original._retried && code && RECOVERABLE.has(code)) {
      original._retried = true;
      try {
        const token = await refreshSession();
        original.headers.Authorization = `Bearer ${token}`;
        return http(original);
      } catch (refreshError) {
        // Only a refused refresh (401) ends the session. A server or network outage keeps it: the
        // original call fails with that error and the user can retry once the API is back.
        if (isApiError(refreshError) && refreshError.status === 401) {
          store.dispatch(signedOut(refreshError.code as SignOutReason));
        }
        // Report what actually failed (SERVER_UNAVAILABLE, NETWORK_ERROR...), not the stale 401.
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(toApiError(error));
  },
);
