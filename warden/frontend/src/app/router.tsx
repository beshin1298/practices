import { createBrowserRouter, Navigate } from 'react-router-dom';

import { AppShell } from '@/components/layout/AppShell';
import { NotFoundPage, RouteErrorPage } from '@/components/layout/StatusPage';
import { AccountPage } from '@/features/account/AccountPage';
import { ChangePasswordPage } from '@/features/account/ChangePasswordPage';
import { GuestOnly, RequireAuth, RequirePermission } from '@/features/auth/guards';
import { LoginPage } from '@/features/auth/LoginPage';
import { PeoplePage } from '@/features/people/PeoplePage';
import { RolesPage } from '@/features/roles/RolesPage';

// Access per route = "Screens" table in docs/rbac-matrix.md.
export const router = createBrowserRouter([
  {
    element: <GuestOnly />, // PUBLIC
    errorElement: <RouteErrorPage />,
    children: [{ path: '/login', element: <LoginPage /> }],
  },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            // A page that throws while rendering shows the error inside the shell: the menu stays usable.
            errorElement: <RouteErrorPage />,
            children: [
          { path: '/', element: <Navigate to="/account" replace /> },
          {
            element: <RequirePermission anyOf={['USER_READ_OWN']} />,
            children: [{ path: '/account', element: <AccountPage /> }],
          },
          {
            element: <RequirePermission anyOf={['USER_UPDATE_OWN']} />,
            children: [{ path: '/account/password', element: <ChangePasswordPage /> }],
          },
          {
            element: <RequirePermission anyOf={['USER_READ_DEPARTMENT', 'USER_READ_ALL']} />,
            children: [{ path: '/people', element: <PeoplePage /> }],
          },
          {
            element: <RequirePermission anyOf={['RBAC_READ']} />,
            children: [{ path: '/roles', element: <RolesPage /> }],
          },
          { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
]);
