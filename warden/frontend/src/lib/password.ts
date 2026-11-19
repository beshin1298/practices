import { z } from 'zod';

// Same policy as the backend's @ValidPassword (PasswordPolicyValidator.java).
const utf8 = new TextEncoder();
const hasLetter = (value: string) => /\p{L}/u.test(value);
const hasDigit = (value: string) => /\p{Nd}/u.test(value);
const fitsBcrypt = (value: string) => utf8.encode(value).length <= 72;

/** Shown as a live checklist under the "new password" field. */
export const passwordChecklist = [
  { id: 'length', label: 'Ít nhất 8 ký tự', test: (value: string) => value.length >= 8 },
  { id: 'letter', label: 'Có chữ cái', test: hasLetter },
  { id: 'digit', label: 'Có chữ số', test: hasDigit },
] as const;

export const passwordSchema = z
  .string()
  .min(8, 'Mật khẩu phải có ít nhất 8 ký tự')
  .refine(fitsBcrypt, 'Mật khẩu tối đa 72 byte')
  .refine((value) => hasLetter(value) && hasDigit(value), 'Mật khẩu phải có cả chữ và số');
