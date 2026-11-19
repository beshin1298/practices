import axios, { type AxiosError } from 'axios';

import { store } from '@/app/store';
import { sessionStarted } from '@/features/auth/authSlice';
import { toApiError } from './errors';
import type { ApiProblem, AuthResponse } from './types';

let inFlight: Promise<string> | null = null;

/**
 * Exchanges the HttpOnly refresh cookie for a new access token and stores the session.
 *
 * Only one refresh may run at a time in the whole browser: the server rotates the refresh token on
 * every call, so two parallel calls with the same cookie look exactly like a stolen token being
 * replayed (reuse detection would end the session). Within a tab a shared promise deduplicates;
 * across tabs the Web Locks API serializes, and the second tab then sends the already-rotated cookie.
 *
 * Uses a bare axios call on purpose: the `http` interceptor calls this function on 401.
 */
export function refreshSession(): Promise<string> {
  inFlight ??= exclusive(async () => {
    try {
      const { data } = await axios.post<AuthResponse>('/api/auth/refresh', null, { timeout: 15_000 });
      store.dispatch(sessionStarted({ accessToken: data.accessToken, user: data.user }));
      return data.accessToken;
    } catch (error) {
      throw toApiError(error as AxiosError<ApiProblem>);
    }
  }).finally(() => {
    inFlight = null;
  });
  return inFlight;
}

function exclusive<T>(task: () => Promise<T>): Promise<T> {
  if ('locks' in navigator && navigator.locks) {
    return navigator.locks.request('warden-refresh', task) as Promise<T>;
  }
  return task();
}
