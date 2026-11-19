# 2026-11-19 — Đăng xuất chặn access token ngay

## 1. Tóm tắt

Đăng xuất (và mọi lần thu hồi family refresh token) giờ làm access token của phiên đó bị từ chối ngay ở request kế tiếp,
thay vì còn dùng được tối đa 15 phút. Phục vụ màn hình 1. Đăng nhập: quy tắc đăng xuất mới, test case DN-12, DN-13, DN-14.

## 2. File theo lớp

**Migration**

- `backend/src/main/resources/db/migration/V5__index_live_refresh_token_family.sql`: partial index
  `ix_refresh_tokens_live_family (family_id) WHERE revoked_at IS NULL`. Filter tra cột này mỗi request; index chỉ chứa
  token còn sống (khoảng một dòng mỗi phiên), không quét các token đã xoay.

**Repository / service**

- `auth/repository/RefreshTokenRepository`: `existsByFamilyIdAndRevokedAtIsNull`.
- `auth/service/RefreshTokenService` + `impl/RefreshTokenServiceImpl`: `isFamilyActive(familyId)` (`readOnly`).
- `auth/service/RotationResult`: `Rotated` mang thêm `familyId` để access token mới có cùng `sid`.
- `auth/service/SessionService` + `impl/SessionServiceImpl`: `start` sinh một `sessionId` dùng cho cả family refresh token
  lẫn claim `sid`; `refresh` lấy `sid` từ `rotated.familyId()`. Javadoc `end` mô tả hành vi mới.
- `security/jwt/JwtService` + `impl/JwtServiceImpl`: `issue(user, sessionId)` ghi claim `sid`; `parse` bắt buộc có `sid`
  hợp lệ (UUID), thiếu hay sai thì `MalformedJwtException` → `TOKEN_INVALID`.
- `security/service/impl/TokenAuthenticationServiceImpl`: kiểm `isFamilyActive(sid)` trước khi nạp user; family chết thì
  `TOKEN_REVOKED`.

**Test**

- `RefreshTokenIntegrationTest`: `logoutRevokesAccessTokenOfThatSessionOnly` (DN-12),
  `reuseRevokesAccessTokensOfFamily` (DN-13), `accessTokenWithoutSessionIdRejected` (DN-14).

**Tài liệu**

- Tab 1. Đăng nhập (quy tắc + 3 test case), Thiết kế kỹ thuật (claim, thu hồi, các bước filter, hiệu năng), Schema
  (index, migration V5, mô tả `TOKEN_REVOKED`), Skill secure-feature + `SKILL.md` (luật token, số truy vấn của filter),
  Nhật ký dòng 21.

## 3. RBAC

Không thêm hay đổi endpoint, annotation hay URL rule. Audit sau thay đổi:

```
| ok | POST | `/api/auth/logout` | PUBLIC | PUBLIC | PUBLIC | OK | backend/src/main/java/com/warden/auth/controller/AuthController.java:72 |
RBAC audit: 15 endpoints, 15 OK, 0 finding(s). Code and docs/rbac-matrix.md agree on both layers.
```

## 4. Quyết định và trade-off

- **Không làm bảng blacklist JWT.** Bảng `refresh_tokens` đã có `family_id` + `revoked_at`, tức là đã biết phiên nào
  đã chết. Gắn `sid = family_id` vào JWT là tra được ngay, không phải ghi thêm gì khi đăng xuất và không cần job dọn
  blacklist.
- **Không tăng `token_version` khi đăng xuất.** Cách đó đăng xuất user trên mọi thiết bị; đăng xuất chỉ nên kết thúc
  phiên của thiết bị đang dùng.
- **Không rút TTL access token xuống 1–5 phút.** Chỉ thu hẹp cửa sổ chứ không đóng nó, mà số lần refresh tăng lên.
- **Kiểm phiên trước khi nạp user:** truy vấn `exists` trên partial index rẻ hơn entity graph; phiên đã chết thì bỏ qua
  truy vấn user.
- **Phụ thuộc `security` → `auth`:** `TokenAuthenticationServiceImpl` gọi interface `RefreshTokenService`, không gọi
  repository của module khác. `security` vốn đã phụ thuộc `user` (`UserRepository`) theo cùng cách.
- Lệch luật Bước 4: luật "filter tốn đúng 1 truy vấn" đổi thành 2 truy vấn, đã sửa luật ở tab Skill và `SKILL.md`.

## 5. Rủi ro, chỗ reviewer nên đọc kỹ

- `backend/src/main/java/com/warden/security/service/impl/TokenAuthenticationServiceImpl.java:50`: mỗi request thêm một
  truy vấn. Cả hai đều có index; nếu đo thấy chậm thì cache ngắn theo `sid`, xóa cache khi family bị thu hồi.
- `backend/src/main/java/com/warden/security/jwt/impl/JwtServiceImpl.java:72`: JWT phát trước bản này không có `sid`, nên
  bị 401 `TOKEN_INVALID` một lần. Frontend coi mã này là lỗi refresh sửa được (`frontend/src/lib/http.ts:25`), tự refresh
  rồi nhận JWT mới có `sid`.
- `backend/src/main/java/com/warden/auth/service/impl/RefreshTokenServiceImpl.java:73`: lúc xoay vòng, token mới được
  insert và token cũ được thu hồi trong cùng transaction. Vì vậy request song song luôn thấy đúng một token sống trong
  family, không có lúc phiên tạm "chết".
- `isFamilyActive` không xét `expires_at`: refresh token mới nhất của một family luôn hết hạn sau access token cấp cùng
  lúc (7 ngày so với 15 phút), nên không có access token sống nào gắn với family đã hết hạn.

## 6. Đã kiểm chứng

- `bash .claude/skills/secure-feature/scripts/verify.sh`: audit 15/15, kiểm tầng OK, 48/48 test backend pass (45 cũ +
  3 mới, 0 fail, 0 skip), frontend typecheck + build OK.
- Test mới chạy trên PostgreSQL 16 thật (Testcontainers) với đủ migration V1–V5.

## 7. Câu hỏi reviewer có thể hỏi

1. **Không có bảng blacklist thì đăng xuất thu hồi access token bằng gì?** Bằng claim `sid` (`family_id` của refresh
   token). Đăng xuất ghi `revoked_at` cho cả family (`RefreshTokenServiceImpl.revokeFamilyOf`). Filter hỏi "family này
   còn token chưa thu hồi không" (`TokenAuthenticationServiceImpl.java:50`); hết thì trả 401 `TOKEN_REVOKED`.
2. **Vậy JWT còn "stateless" không?** Không hoàn toàn, và app này vốn đã không stateless: mỗi request đã đọc DB để kiểm
   `token_version`, trạng thái khóa và nạp lại quyền. JWT vẫn có ích vì chữ ký chặn token giả trước khi chạm DB và
   mang sẵn `userId`.
3. **Đăng xuất ở laptop có đá điện thoại ra không?** Không. Mỗi lần đăng nhập là một family riêng; chỉ family của
   laptop bị thu hồi (test `logoutRevokesAccessTokenOfThatSessionOnly`).
4. **Kẻ trộm cầm access token và refresh token thì sao?** Khi một trong hai bên dùng lại refresh token đã xoay, cả family
   bị thu hồi. Từ request kế tiếp, mọi access token của phiên, kể cả của kẻ trộm, đều nhận 401 (test
   `reuseRevokesAccessTokensOfFamily`).
5. **Mỗi request thêm một truy vấn có chậm không?** Đó là `SELECT ... LIMIT 1` trên partial index chỉ chứa token còn
   sống, gần như O(1). Phiên đã chết thì truy vấn user nặng hơn bị bỏ qua.
