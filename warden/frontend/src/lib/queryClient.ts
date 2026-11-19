import { QueryClient } from '@tanstack/react-query';

import { isApiError } from './errors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Retrying a 4xx never helps (401/403/404 won't fix themselves); network errors (status 0) and 5xx may.
      retry: (failureCount, error) =>
        !(isApiError(error) && error.status >= 400 && error.status < 500) && failureCount < 2,
    },
  },
});

/** One place for cache keys so invalidation can't drift from the queries. */
export const queryKeys = {
  me: ['me'] as const,
  users: (params: object) => ['users', params] as const,
  usersAll: ['users'] as const,
  departments: ['departments'] as const,
  roles: ['roles'] as const,
};
