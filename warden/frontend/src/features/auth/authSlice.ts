import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import type { CurrentUser, SignOutReason } from '@/lib/types';

/**
 * Client-side session only: who is logged in and with which access token.
 * Nothing is persisted: the access token lives in memory (XSS cannot read it from storage) and a
 * reload restores the session through the HttpOnly refresh cookie (see lib/session.ts).
 * Server data (profiles, lists) lives in TanStack Query, not here.
 */
export type SessionStatus = 'restoring' | 'authenticated' | 'anonymous';

export interface AuthState {
  accessToken: string | null;
  user: CurrentUser | null;
  status: SessionStatus;
  /** Why the last session ended, shown once on the login page. */
  signOutReason: SignOutReason | null;
}

const initialState: AuthState = { accessToken: null, user: null, status: 'restoring', signOutReason: null };

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    /** Login, silent refresh and password change all end here. */
    sessionStarted(state, action: PayloadAction<{ accessToken: string; user: CurrentUser }>) {
      state.accessToken = action.payload.accessToken;
      state.user = action.payload.user;
      state.status = 'authenticated';
      state.signOutReason = null;
    },
    userUpdated(state, action: PayloadAction<CurrentUser>) {
      state.user = action.payload;
    },
    signedOut(state, action: PayloadAction<SignOutReason | undefined>) {
      state.accessToken = null;
      state.user = null;
      state.status = 'anonymous';
      state.signOutReason = action.payload ?? null;
    },
    signOutReasonShown(state) {
      state.signOutReason = null;
    },
  },
});

export const { sessionStarted, userUpdated, signedOut, signOutReasonShown } = authSlice.actions;
export default authSlice.reducer;
