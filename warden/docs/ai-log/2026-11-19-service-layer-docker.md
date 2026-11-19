# 2026-11-19 — Tầng service, cấu trúc thư mục, Docker

## 1. Tóm tắt

Backend chuyển sang chia theo tính năng rồi theo tầng, và có đủ tầng service (interface + Impl) để controller và filter không
còn gọi thẳng repository. Thêm Docker để chạy cả app một lệnh. Không đổi hành vi nghiệp vụ; mọi test case của các
màn hình 1–5 vẫn chạy như trước.

## 2. File theo lớp

**Cấu trúc** (di chuyển file, đổi package, không đổi logic)

- `com.warden.access` → `com.warden.role`: tên module trùng với khái niệm (vai trò và quyền).
- Mỗi module `auth/ user/ role/ department/` có `controller/ service/ service/impl/ repository/ entity/ dto/request|response/ mapper/`;
  `security/` có `jwt/ filter/ handler/ annotation/ service/`.
- `UserSpecifications`, constructor và các method `RefreshToken` chuyển sang `public` vì giờ khác package với service dùng chúng.

**Service** (mới hoặc tách)

- `role/service/RoleService` + `impl/RoleServiceImpl`, `department/service/DepartmentService` + `impl/DepartmentServiceImpl`:
  trước đó `RoleController`, `DepartmentController` gọi thẳng repository và mapper.
- `security/service/TokenAuthenticationService` + `impl/TokenAuthenticationServiceImpl`, `security/service/TokenCheck`:
  việc kiểm JWT và tra user chuyển ra khỏi `JwtAuthenticationFilter`; filter chỉ còn đọc header và gắn principal.
- `AuthService`, `SessionService`, `RefreshTokenService`, `AccountService`, `UserManagementService`, `JwtService`: tách
  thành interface (giữ Javadoc hợp đồng) + `XxxServiceImpl` (`@Service`, `@Transactional`).
- `auth/service/TokenHasher`: SHA-256 của refresh token, dùng chung cho service và test thay vì mỗi nơi tự băm.

**Web**

- `security/filter/JwtAuthenticationFilter`, `config/SecurityConfig`: inject `TokenAuthenticationService` thay cho
  `JwtService` + `UserRepository`.

**Hạ tầng**

- `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`, hai file `.dockerignore`.

**Kiểm chứng và tài liệu**

- `.claude/skills/secure-feature/scripts/verify.sh`: thêm bước 2/4 kiểm tầng.
- `CLAUDE.md`: mục Run (Docker) và Backend layout. Tab Thiết kế kỹ thuật, Skill secure-feature, Nhật ký (dòng 18–20).

## 3. RBAC

Không thêm hay đổi endpoint; chỉ đổi đường dẫn file nguồn. Audit sau thay đổi:

```
| ok | GET | `/api/departments` | AUTHENTICATED | ... | OK | .../department/controller/DepartmentController.java:29 |
| ok | GET | `/api/roles`       | AUTHENTICATED | ... | OK | .../role/controller/RoleController.java:29 |
| ok | GET | `/api/users/me`    | USER_READ_OWN | ... | OK | .../user/controller/AccountController.java:48 |
RBAC audit: 15 endpoints, 15 OK, 0 finding(s). Code and docs/rbac-matrix.md agree on both layers.
```

## 4. Quyết định và trade-off

- **Tính năng → tầng** thay vì tầng → tính năng (`controller/user`, `controller/auth`...): mọi thứ của một tính năng nằm
  cùng một chỗ, xóa hay tách module không phải lục khắp cây thư mục.
- **Interface + Impl** dù mỗi interface chỉ có một implementation: controller phụ thuộc hợp đồng (chữ D trong SOLID), test
  mock được interface, Javadoc hợp đồng nằm tách khỏi chi tiết. Giá phải trả: số file gấp đôi.
- **`TokenCheck` là sealed interface** (`Accepted` / `Rejected`) thay vì ném exception: filter dùng `switch` phủ hết các
  trường hợp, thêm loại kết quả mới thì compiler báo những chỗ chưa xử lý.
- **`TokenHasher` là hàm static**, không phải bean: hàm thuần, không có trạng thái, không cần inject.
- **Kiểm tầng bằng grep trong `verify.sh`** thay vì thêm ArchUnit: không thêm thư viện, chạy nhanh. Đổi lại đây là kiểm
  theo tên, không chặt bằng phân tích bytecode.
- **nginx proxy cùng origin** thay vì gọi API khác cổng: cookie `SameSite=Strict` vẫn gửi được, không cần preflight CORS.
- **Dockerfile không dùng cú pháp chỉ BuildKit có** (cache mount): engine Docker trong WSL này không có buildx. Layer
  `dependency:go-offline` vẫn được cache khi `pom.xml` không đổi.
- Không lệch luật nào ở Bước 4.

## 5. Rủi ro, chỗ reviewer nên đọc kỹ

- `backend/src/main/resources/application.yml:28`: không có `.env` thì Docker dùng JWT secret mặc định nằm trong repo; ai có
  repo cũng ký được token. Ngoài máy local phải đặt `JWT_SECRET`.
- `backend/src/main/resources/application.yml:35`: `cookie-secure` mặc định `false` để chạy được qua http://localhost; chạy
  sau HTTPS thì đặt `COOKIE_SECURE=true`.
- `docker-compose.yml:35`: `SERVER_FORWARD_HEADERS_STRATEGY=native` tin header `X-Forwarded-*`. An toàn vì backend chỉ
  `expose` trong mạng compose, không mở cổng ra host; mở cổng 8080 ra ngoài thì phải bỏ dòng này.
- `backend/src/main/java/com/warden/config/SecurityConfig.java:55` + `frontend/nginx.conf:7`: Swagger mở công khai qua :3000.
  Hợp với app nội bộ đang demo; môi trường thật nên tắt `springdoc`.
- `backend/src/main/java/com/warden/security/service/impl/TokenAuthenticationServiceImpl.java:48`: mỗi request tốn một truy
  vấn để kiểm `tokenVersion`, trạng thái khóa và nạp lại vai trò. Đây là chủ đích: khóa user hay đổi vai trò có hiệu lực ngay.
- `.claude/skills/secure-feature/scripts/verify.sh:21`: regex theo tên class, nên Javadoc nhắc tới `UserRepository` trong một
  controller cũng bị báo lỗi (dương tính giả, an toàn).

## 6. Đã kiểm chứng

- `bash .claude/skills/secure-feature/scripts/verify.sh`: audit 15/15, kiểm tầng OK (6 controller/filter, 9 service
  interface + Impl), 45/45 test backend pass, frontend typecheck + build OK.
- Thử âm tính: thêm repository vào một controller thì bước 2/4 báo lỗi; gỡ ra thì xanh lại.
- `docker compose build`: 2 image (backend 131 MB, frontend 21 MB, dung lượng nén).
- `docker compose up -d`, gọi qua http://localhost:3000:
  - `/` 200, route SPA `/users` 200, Swagger 200.
  - Đăng nhập 200 kèm `Set-Cookie: warden_refresh=...; Path=/api/auth; HttpOnly; SameSite=Strict`; refresh 200.
  - HR `GET /api/users` 200; nhân viên `GET /api/users` 403, `GET /api/users/me` 200.
  - Thiếu token 401; sai mật khẩu 401.
- `docker compose down` sau khi kiểm.

## 7. Câu hỏi reviewer có thể hỏi

1. **Interface chỉ có một implementation thì tách làm gì?** Controller phụ thuộc hợp đồng chứ không phụ thuộc chi tiết,
   test mock dễ, và `verify.sh` kiểm được luật "controller chỉ biết interface". Muốn bỏ Impl thì phải bỏ cả luật đó.
2. **Sao filter không gọi `UserRepository` cho nhanh?** Filter là lớp web; truy vấn DB cần transaction `readOnly` và luật
   kiểm `tokenVersion`, đó là nghiệp vụ. Để trong `TokenAuthenticationServiceImpl` thì test được riêng, filter chỉ còn đọc header và gắn principal.
3. **Khóa user thì access token đang sống bị chặn ngay không?** Có. Mỗi request đọc lại user từ DB
   (`TokenAuthenticationServiceImpl.java:48`): user bị khóa thì trả `TOKEN_REVOKED`; vai trò và quyền cũng lấy từ DB nên
   đổi vai trò có hiệu lực ngay, không chờ JWT hết hạn. Đổi mật khẩu tăng `tokenVersion` (`User.java:117`) nên mọi JWT cũ
   lệch `ver` và bị từ chối. Refresh token bị thu hồi qua `UserSecurityChangedEvent`.
4. **Sao trình duyệt không gọi thẳng :8080?** nginx chuyển `/api` sang backend nên trình duyệt chỉ thấy một origin;
   cookie `SameSite=Strict` đi kèm bình thường và không cần mở CORS rộng.
5. **Dữ liệu Docker nằm đâu, `down` có mất không?** Trong volume `warden-pgdata`; `down` giữ dữ liệu, `down -v` mới xóa.
