# 2026-11-19 — Sửa theo bản review bảo mật và hiệu năng

## 1. Tóm tắt

Sửa các điểm đúng trong bản review: chống dò mật khẩu, leo thang quyền, cấu hình mặc định an toàn, header bảo mật,
giữ kết nối DB khi chạy BCrypt, admin cuối cùng, audit log, validate đầu vào và lỗi xử lý phiên ở frontend. Có 6 điểm
phản biện, không sửa (mục 4). Test case: DN (đăng nhập), DMK (đổi mật khẩu), NS (nhân sự).

## 2. File theo lớp

**Cấu hình và hạ tầng**

- `application.yml`: không còn giá trị mặc định cho secret (JWT, DB), seed tắt, Swagger tắt, cookie Secure bật,
  actuator chỉ mở `health`, `login-max-failures-per-ip: 30`.
- `application-dev.yml` (mới): giá trị công khai cho máy dev, test và stack demo.
- `pom.xml`: thêm `spring-boot-starter-actuator`; `spring-boot:run` chạy profile `dev`.
- `docker-compose.yml`: `SPRING_PROFILES_ACTIVE=dev` cho stack demo, healthcheck `/actuator/health`, build arg
  `VITE_SHOW_DEMO_ACCOUNTS=true`, cổng `3000:8080`.
- `frontend/Dockerfile`: `nginxinc/nginx-unprivileged` (không chạy root); cờ demo mặc định tắt.
- `frontend/nginx.conf`, `frontend/security-headers.conf` (mới):
  - `X-Forwarded-For $remote_addr` (ghi đè, không nối thêm);
  - CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`;
  - `server_tokens off`, `client_max_body_size`, timeout proxy.
- `.env.example`, `CLAUDE.md`.

**Backend**

- `security/service/AttemptLimiter` (thay `auth/service/LoginAttemptLimiter`): ba bucket `LOGIN_USER_IP`, `LOGIN_IP`,
  `PASSWORD_CHECK`; key là record, không ghép chuỗi.
- `AuthServiceImpl.login`: bỏ `@Transactional`, BCrypt chạy ngoài transaction. `lastLoginAt` cập nhật bằng một câu
  `UPDATE` riêng; có audit log.
- `AccountServiceImpl.changePassword`: BCrypt ngoài transaction, chỉ phần ghi chạy trong `TransactionTemplate`. Bỏ lần
  BCrypt thừa. Giới hạn số lần đoán mật khẩu hiện tại. Kiểm hash không đổi giữa lúc đọc và lúc ghi.
- `SessionService.start(userId)`: từ chối tài khoản đã bị khóa; bỏ overload nhận `User`.
- `RefreshTokenRepository` / `RefreshTokenService.isFamilyActive(familyId, userId)`: `sid` gắn với `user_id`, và token
  phải chưa hết hạn.
- `Permissions.withImplied` / `changed`, `Actor.holdsAll`: luật bao hàm phạm vi khi giao quyền; một helper tính
  chênh lệch quyền dùng chung.
- `UserAccessPolicy.checkCreate` / `checkRoleChange` nhận thêm tập quyền của các vai trò.
- `UserManagementServiceImpl`:
  - tạo tài khoản và gán vai trò không được giao quyền mình không có;
  - giữ người cấp cao nhất cuối cùng (`lockTopRoles` + `countEnabledWithRankExcept`, mã `LAST_TOP_USER`);
  - `GET /api/users/{id}` ngoài phạm vi trả 404;
  - audit log.
- `RoleServiceImpl`: dùng helper chung, vai trò không tồn tại trả 404, audit log.
- `JwtServiceImpl`: dựng `JwtParser` một lần. `Problems`: URI lạ không làm hỏng handler. `ProblemDetailsSecurityHandler`:
  `WWW-Authenticate: Bearer`.
- DTO: `toString()` che mật khẩu (`ChangePasswordRequest`, `CreateUserRequest`); `Set<@NotNull Long>` /
  `Set<@NotBlank String>`; `LoginRequest` có `@Size`; regex số điện thoại tối đa 20 ký tự, hai đầu là chữ số.
- `UserSpecifications`: escape `%`, `_`, `\`; `q` tối đa 100 ký tự. `DataSeeder`: mỗi tài khoản một hash.
  `SecurityConfig`: `permitAll` cho `/actuator/health`, CORS `allowCredentials`.

**Frontend**

- `lib/http.ts`: refresh lỗi không phải 401 thì trả về lỗi thật (`SERVER_UNAVAILABLE` / `NETWORK_ERROR`).
- `lib/session.ts`: timeout 15 s. `lib/queryClient.ts`: retry lỗi mạng và 5xx, không retry 4xx.
- `app/router.tsx`, `AppShell.tsx`: `/account`, `/account/password` có guard và menu theo quyền.
- `features/account/api.ts`: bỏ `staleTime: Infinity` của danh sách vai trò.
- `AccountPage`: nhãn quyền lạ vẫn hiện (fallback về mã quyền).
- `LoginPage`: khối tài khoản demo chỉ có khi chạy dev hoặc build có cờ.
- `Dialog`: `useId`.
- `lib/forms.ts`: `PHONE_PATTERN` dùng chung cho 3 form.
- `lib/sessionChannel.ts` (mới): đăng xuất ở một tab thì mọi tab đăng xuất theo.
- Font Inter tự host (`@fontsource-variable/inter`), bỏ Google Fonts.

**Test**

- `SecurityHardeningIntegrationTest` (mới, 8 test).
- `UserAccessScopeIntegrationTest`: đọc người ngoài phạm vi nay là 404 (đổi spec, đã chốt với người dùng).
- `UserAccessPolicyTest`: chữ ký mới, thêm case cho luật bao hàm.
- `IntegrationTestSupport`: `@ActiveProfiles("dev")`.

## 3. RBAC

Không đổi endpoint hay annotation. `docs/rbac-matrix.md` thêm 3 luật phạm vi: đọc trong phòng không xét cấp và trả 404
khi ngoài phạm vi; giao quyền qua vai trò; người cấp cao nhất cuối cùng.

```
RBAC audit: 17 endpoints, 17 OK, 0 finding(s). Code and docs/rbac-matrix.md agree on both layers.
```

## 4. Quyết định, trade-off và các điểm phản biện

**Đã chốt với người dùng**

- *_ALL bao hàm *_DEPARTMENT bao hàm *_OWN, nhưng chỉ khi xét "có được giao quyền này không". Không bao hàm thì HR mất
  khả năng tạo tài khoản Quản lý.
- Đọc trong phạm vi phòng ban không xét cấp; sửa mới xét cấp. Đã sửa spec cho khớp code.

**Phản biện, không sửa**

1. **2.1, tích Đề-các của entity graph.** Đúng về nguyên lý, nhưng số dòng ở miền này nhỏ. Thường là 1 vai trò ×
   4–11 quyền × 1 phòng × 0 quyền lẻ, tức 4–11 dòng. Trường hợp xấu thực tế là 2 vai trò × 11 × 2 phòng × 2 quyền lẻ =
   88 dòng, chỉ vài KB. Tách thành 3 truy vấn đổi số dòng nhân lên lấy thêm 2 round-trip ở **mọi** request. Nên làm
   lại khi user có nhiều phòng ban hoặc quyền lẻ, lúc đó dùng projection riêng cho `AuthUser`.
2. **BCrypt cost 12.** OWASP Password Storage Cheat Sheet yêu cầu bcrypt cost tối thiểu 10. Cost 12 làm mỗi lần đăng
   nhập chậm khoảng 4 lần, ngược hướng với điểm 1.5 (DoS bằng CPU và connection).
3. **`navigator.locks` trên HTTP LAN.** Ngoài localhost, app bắt buộc chạy HTTPS: cookie refresh có cờ `Secure` (nay là
   mặc định), nên qua `http://<ip-lan>` refresh không chạy ngay từ đầu. Trên HTTPS thì Web Locks luôn có. Riêng phần
   đồng bộ đăng xuất giữa các tab đã làm (`BroadcastChannel`).
4. **1.6, gom đổi mật khẩu và mở phiên mới vào một transaction.** Việc này thuộc hai module (`user` không gọi `auth`,
   theo DIP). Khe hở bị khóa tài khoản giữa hai bước đã đóng: `start` từ chối tài khoản bị khóa. Nếu bước 2 lỗi thì
   trạng thái vẫn an toàn: mật khẩu đã đổi, phiên cũ đã bị thu hồi, người dùng đăng nhập lại bằng mật khẩu mới.
5. **`@Version` (optimistic locking).** Không có `If-Match`/ETag từ client thì `@Version` chỉ bắt được hai transaction
   chồng nhau trong vài mili giây, không chặn được lost update kiểu "form cũ" mà review mô tả. Muốn làm đúng phải thêm
   ETag đầu-cuối; ghi lại làm việc sau. Riêng đổi mật khẩu đã có kiểm hash trước khi ghi.
6. **`equals`/`hashCode` cho entity.** Mọi thao tác với `Set<Role|Department|Permission>` đều diễn ra trong một
   persistence context, nơi Hibernate bảo đảm mỗi dòng chỉ có một instance. Thêm `equals` theo id lại sinh lỗi với
   entity chưa có id (transient). Không có chỗ nào dùng entity detached trong Set.

**Trade-off khác**

- Giới hạn theo IP là 30 lần sai trong 15 phút: cao hơn giới hạn theo user vì nhiều người có thể dùng chung một IP NAT.
- CSP vẫn để `style-src 'unsafe-inline'` vì thư viện toast chèn thẻ `<style>`. `script-src` giữ chặt, và đó mới là
  lớp bảo vệ access token nằm trong bộ nhớ.

## 5. Rủi ro, chỗ reviewer nên đọc kỹ

- `backend/src/main/resources/application.yml`: chạy không có profile `dev` thì app dừng ngay khi thiếu `JWT_SECRET` /
  `DB_*`. Đây là chủ đích; stack demo đặt `SPRING_PROFILES_ACTIVE=dev`.
- `frontend/nginx.conf`: chỉ đúng khi nginx là lớp ngoài cùng. Nếu có proxy phía trước thì dùng `set_real_ip_from` và
  giới hạn `server.tomcat.remoteip.internal-proxies`.
- `user/service/impl/UserManagementServiceImpl.java` (`requireAnotherTopRankedUser`): lock hàng của vai trò cấp cao
  nhất nên các thao tác khóa hoặc hạ cấp admin chạy tuần tự. Thao tác này hiếm nên chi phí nhỏ.
- `security/service/AttemptLimiter.java`: lưu trong bộ nhớ, không chia sẻ giữa nhiều instance.

## 6. Đã kiểm chứng

- `bash .claude/skills/secure-feature/scripts/verify.sh`: audit 17/17, kiểm tầng OK, test backend xanh (xem Nhật ký dòng
  24), frontend typecheck + build OK.
- Lần chạy đầu có lỗi biên dịch: `UserAccessPolicyTest` còn gọi chữ ký cũ. Đã sửa test và thêm case cho luật bao hàm.

## 7. Câu hỏi reviewer có thể hỏi

1. **Giả `X-Forwarded-For` có qua được giới hạn đăng nhập không?** Không. nginx ghi đè header bằng địa chỉ nó thấy,
   backend không mở cổng ra ngoài; thêm vào đó có giới hạn theo IP cho cả việc rải mật khẩu lên nhiều username.
2. **HR có tự nâng quyền qua việc tạo tài khoản được không?** Không. Tạo hoặc gán vai trò mà vai trò đó mang quyền HR
   không có (sau khi tính bao hàm phạm vi) thì trả 403 (test `creatingAccountsCannotEscalatePermissions`).
3. **Lộ JWT secret thì sao?** Không có profile `dev` thì app không còn secret mặc định. Dù vậy, `sid` nay gắn với
   `user_id`: lấy `sid` của mình để ký token cho admin sẽ bị từ chối (test `sessionIdIsBoundToItsUser`).
4. **Sao đăng nhập không có `@Transactional`?** BCrypt mất ~100 ms. Giữ connection trong lúc băm thì khoảng 10 request
   song song là cạn pool. Đọc và ghi vẫn chạy trong transaction ngắn riêng.
5. **Hai admin khóa lẫn nhau cùng lúc?** Cả hai phải lấy lock hàng vai trò cấp cao nhất. Người đến sau thấy không còn
   ai và nhận 409 `LAST_TOP_USER` (test `lastTopRankedUserIsKept`).
