import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { toast } from 'sonner';

import { isApiError } from './errors';

/**
 * Puts server-side errors next to the right input:
 * - VALIDATION_FAILED: each entry of `errors` goes to its field
 * - a known business code (e.g. EMAIL_TAKEN): goes to the field given in `codeToField`
 * Anything else becomes a toast.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  codeToField: Partial<Record<string, Path<T>>> = {},
) {
  if (!isApiError(error)) {
    toast.error('Đã có lỗi xảy ra');
    return;
  }
  const field = codeToField[error.code];
  if (field) {
    setError(field, { type: 'server', message: error.message });
    return;
  }
  const fieldErrors = Object.entries(error.fieldErrors);
  if (error.code === 'VALIDATION_FAILED' && fieldErrors.length > 0) {
    fieldErrors.forEach(([name, message]) => setError(name as Path<T>, { type: 'server', message }));
    return;
  }
  toast.error(error.message);
}

export const toastApiError = (error: unknown) => toast.error(isApiError(error) ? error.message : 'Đã có lỗi xảy ra');

/** Same rule as the API (@Pattern on the DTOs): 8-20 characters, digits and spaces, optional leading +, digits at both ends. */
export const PHONE_PATTERN = /^$|^\+?[0-9][0-9 ]{6,17}[0-9]$/;
export const PHONE_MESSAGE = 'Số điện thoại 8-20 ký tự: chữ số và khoảng trắng, có thể bắt đầu bằng +';
