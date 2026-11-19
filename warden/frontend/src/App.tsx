import { useEffect } from 'react';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';

import { router } from '@/app/router';
import { store, useAppSelector } from '@/app/store';
import { signedOut } from '@/features/auth/authSlice';
import { queryClient } from '@/lib/queryClient';
import { refreshSession } from '@/lib/session';
import { onLogoutElsewhere } from '@/lib/sessionChannel';

/** On page load, try to restore the session from the HttpOnly refresh cookie. */
function SessionBootstrap() {
  useEffect(() => {
    if (store.getState().auth.status === 'restoring') {
      refreshSession().catch(() => store.dispatch(signedOut()));
    }
    return onLogoutElsewhere(() => store.dispatch(signedOut()));
  }, []);
  return null;
}

/** When the session ends (logout, expired or revoked), drop every cached server response. */
function ClearCacheOnSignOut() {
  const status = useAppSelector((state) => state.auth.status);
  useEffect(() => {
    if (status === 'anonymous') queryClient.clear();
  }, [status]);
  return null;
}

export function App() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <SessionBootstrap />
        <ClearCacheOnSignOut />
        <RouterProvider router={router} />
        <Toaster position="top-right" richColors closeButton />
      </QueryClientProvider>
    </Provider>
  );
}
