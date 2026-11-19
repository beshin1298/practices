import type { AxiosError } from 'axios';

import type { ApiProblem } from './types';

/** Error with the server's ProblemDetail attached, so UI code can switch on `code`. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(problem: ApiProblem) {
    super(problem.detail ?? problem.title ?? 'Đã có lỗi xảy ra');
    this.name = 'ApiError';
    this.status = problem.status;
    this.code = problem.code;
    this.fieldErrors = problem.errors ?? {};
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function toApiError(error: AxiosError<ApiProblem>): ApiError {
  const response = error.response;
  if (response?.data?.code) return new ApiError(response.data);
  if (response) {
    // A reply without our ProblemDetail: nginx answering 502/504 while the API is down, or a proxy error.
    const serverSide = response.status >= 500;
    return new ApiError({
      status: response.status,
      code: serverSide ? 'SERVER_UNAVAILABLE' : `HTTP_${response.status}`,
      detail: serverSide ? 'Máy chủ đang gặp sự cố, vui lòng thử lại sau' : 'Yêu cầu không hợp lệ',
    });
  }
  return new ApiError({
    status: error.response?.status ?? 0,
    code: 'NETWORK_ERROR',
    detail: 'Không kết nối được tới máy chủ',
  });
}
