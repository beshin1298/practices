---
name: secure-feature
description: Use for ANY change to Warden features - a new or changed API endpoint, page or route, DB table or column, role or permission (thêm API, thêm màn hình, sửa phân quyền, thêm cột). Drives the change through every layer in a fixed order (Flyway, entity, DTO + MapStruct, service, controller with exactly one access annotation, URL rule, integration tests, React API hook, page + route guard), keeps docs/rbac-matrix.md as the single source of truth for who may call what, proves code and spec agree with scripts/rbac_audit.py and the spec-driven RbacMatrixIntegrationTest, then writes an AI change report the developer can defend. Also use to audit RBAC (kiểm tra phân quyền) or to explain existing auth code for a code review.
---

<!-- Generated from the 'Skill secure-feature' tab of the Warden doc on Claude Docs by scripts/sync_from_docs.py. Edit the tab, then re-sync; see CLAUDE.md. -->

# secure-feature

## Mục tiêu và khi nào dùng

Mỗi thay đổi do AI viết trong Warden phải: cùng khuôn với code đang có; không thể lọt một endpoint thiếu kiểm tra quyền; và được người nộp giải thích, chịu trách nhiệm từng dòng.

| Chế độ | Khi người dùng nói | Làm gì |
| --- | --- | --- |
| build (mặc định) | "thêm API xóa user", "thêm màn hình nhật ký", "thêm cột phone" | Bước 1 đến 6 |
| audit | "audit RBAC", "kiểm tra phân quyền" | Chỉ Bước 5; giải thích từng dòng FAIL và cách sửa |
| explain | "giải thích luồng đổi mật khẩu", "giải thích cơ chế refresh token" | Đọc code, trả lời theo mục "Câu hỏi reviewer" ở Bước 6, kèm `file:line` |

Không dùng cho việc không đụng tới tính năng: đổi README, nâng version thư viện, sửa style thuần CSS.

## Nguồn sự thật, đọc trước khi viết code

1. Bộ docs Warden trên Claude Docs ([link](https://claude.ai/artifact/TmkpfUh7J9EnWVjDCcAap2)): tab Requirements (tổng quan, vai trò, yêu cầu chung) và các tab con theo màn hình (nội dung, quy tắc, thông báo, test case); tab Thiết kế kỹ thuật (xác thực, phân quyền, bảng API, frontend); tab Schema (DDL, API, mã lỗi). Có tool Claude Docs thì đọc tab liên quan; không có thì dùng `CLAUDE.md` và các file local bên dưới.
2. `docs/rbac-matrix.md`: ai được gọi gì. Script audit và `RbacMatrixIntegrationTest` đều đọc file này, nên giữ nguyên cột của bảng **Roles × Permissions** và bảng **API** (`Method | Path | Access | Description`). Cột Access là `PUBLIC`, `AUTHENTICATED` hoặc danh sách mã quyền cách nhau bằng dấu phẩy (có một là đủ); mọi mã phải có trong `role/constant/Permissions.java`, và bảng Roles × Permissions phải khớp `role_permissions` trong DB.
3. File mẫu (exemplar) cho từng lớp, liệt kê ở Bước 3. Bắt chước hình dạng của chúng thay vì tự nghĩ ra cấu trúc mới.
4. Spec và code lệch nhau: dừng lại hỏi người dùng, không tự chọn bên nào đúng.

## Bước 1–2: Lập kế hoạch, rồi sửa spec trước

**Bước 1. Kế hoạch, viết trong chat trước khi sửa file**

- Mỗi endpoint mới hoặc bị đổi: một dòng `METHOD /path -> ACCESS — lý do`.
- Danh sách file sẽ chạm, theo thứ tự lớp ở Bước 3.
- Màn hình và test case được phục vụ (tab con của Requirements), mã quyền và luật phạm vi áp dụng, mã lỗi mới nếu có.
- Rủi ro bảo mật và hiệu năng của thay đổi, đối chiếu Bước 4.
- Quy tắc phân quyền chưa rõ thì hỏi người dùng. Không bao giờ tự đặt ra quyền.

**Bước 2. Sửa spec cùng lúc với code**

- Thêm hoặc sửa dòng trong `docs/rbac-matrix.md` (bảng API; bảng Screens nếu có route mới).
- Ghi cùng thay đổi đó vào mục Phân quyền ở tab Thiết kế kỹ thuật, và vào tab màn hình liên quan (quy tắc + test case); không có tool Claude Docs thì nhắc người dùng. Quyền mới cần cả hằng số trong `Permissions.java` lẫn migration seed `permissions` + `role_permissions`.
- Bảng/cột mới, endpoint mới, mã lỗi mới: cập nhật tab Schema; mã lỗi thêm vào `ErrorCode.java`.
- Ghi một dòng vào tab Nhật ký thực hiện.

## Bước 3: Triển khai đúng thứ tự các lớp

Làm từ dưới lên, xong lớp này mới sang lớp sau. Đường dẫn backend tính từ `backend/src/main/java/com/warden/`, frontend từ `frontend/src/`.

Mỗi module backend (`auth`, `user`, `role`, `department`) chia folder theo tầng: `controller/` → `service/` (interface) → `service/impl/` (`XxxServiceImpl`, có `@Service`) → `repository/` → `entity/`, cùng `dto/request/`, `dto/response/`, `mapper/`. Dùng chung nằm ở `common/` (error, validation, util, web), `config/` và `security/` (`jwt/`, `filter/`, `handler/`, `annotation/`, `service/`).

| # | Lớp | File mẫu | Luật |
| --- | --- | --- | --- |
| 1 | Migration Flyway | `backend/src/main/resources/db/migration/V2__create_refresh_tokens.sql` | File mới `V{n}__{mo_ta}.sql`; không sửa migration đã chạy. Ràng buộc và index đặt ở DB: NOT NULL, UNIQUE, CHECK, FK, index cho cột tra cứu. |
| 2 | Entity | `user/entity/User.java`, `auth/entity/RefreshToken.java` | Setter chỉ cho field được đổi tự do; bất biến đi qua method nghiệp vụ (`changePassword`, `RefreshToken.revoke`). `ddl-auto=validate` phải qua. |
| 3 | DTO | `user/dto/request/*.java` | Java record riêng cho từng use case; Bean Validation với message tiếng Việt. Không bao giờ lộ `passwordHash`, `tokenVersion`, `tokenHash`. |
| 4 | Mapper | `user/mapper/UserMapper.java` | MapStruct entity sang DTO. Build đang bật `unmappedTargetPolicy=ERROR`, giữ nguyên. |
| 5 | Service | `user/service/impl/UserManagementServiceImpl.java`, `user/service/UserAccessPolicy.java`, `auth/service/impl/RefreshTokenServiceImpl.java` | `@Transactional` (truy vấn thì `readOnly`); thao tác trên người khác phải qua `UserAccessPolicy` (phòng ban, rank, chính mình); lỗi nghiệp vụ là `throw new AppException(ErrorCode.X)`; username và email qua `Normalizer.key`. Thay đổi làm phiên cũ mất hiệu lực thì publish `UserSecurityChangedEvent`. Interface ở `service/`, code ở `service/impl/XxxServiceImpl` (`@Service` và `@Transactional` đặt ở đây). Helper không phải service (`UserAccessPolicy`, `AttemptLimiter`) là `@Component` nằm trong `service/`. |
| 6 | Controller | `user/controller/UserController.java`, `auth/controller/AuthController.java` | Mỗi method có đúng một trong `@PublicEndpoint`, `@Authenticated`, `@PreAuthorize("hasAuthority('P')")` (hoặc `hasAnyAuthority(...)` khi ma trận liệt kê nhiều mã), viết literal để audit đọc được. Người gọi lấy từ `@AuthenticationPrincipal AuthUser`. Cookie qua `RefreshTokenCookies`, không tự dựng header. Chỉ inject interface service; không inject repository, mapper, `EntityManager` hay `*ServiceImpl` (`verify.sh` bước 2 chặn). |
| 7 | URL rule | `config/SecurityConfig.java` | PUBLIC cần `permitAll()`; dòng có mã quyền cần `hasAuthority` / `hasAnyAuthority` với đúng các mã đó; matcher cụ thể (`/api/users/*/roles`) đặt trước matcher rộng (`/api/users/*`). |
| 8 | Test | `backend/src/test/java/com/warden/RefreshTokenIntegrationTest.java` | Mỗi acceptance criterion một test: role đúng 2xx, sai role 403, ẩn danh 401, validate 400, mã lỗi nghiệp vụ. `RbacMatrixIntegrationTest` tự sinh case cho dòng ma trận mới. |
| 9 | FE types + API | `lib/types.ts`, `features/people/api.ts` | Một object API + hook TanStack Query; key chỉ lấy từ `queryKeys`; mutation xong thì invalidate hoặc `setQueryData`. |
| 10 | FE trang + route | `features/people/PeoplePage.tsx`, `app/router.tsx`, `lib/permissions.ts` | Route bọc guard đúng bảng Screens; menu ẩn với role không có quyền; lỗi server qua `applyServerErrors`; phiên ở Redux (chỉ trong bộ nhớ), dữ liệu server ở TanStack Query. |

## Bước 4: Luật code — SOLID, bảo mật, hiệu năng

Mọi dòng code mới phải qua ba nhóm luật này. Vi phạm mà có lý do thì ghi lý do vào báo cáo ở Bước 6.

**SOLID**

| Nguyên tắc | Áp dụng trong Warden |
| --- | --- |
| S — Single responsibility | Controller chỉ lo HTTP (parse, status, cookie); service lo nghiệp vụ; `JwtService` ký và kiểm access token; `RefreshTokenService` lo vòng đời refresh token; filter chỉ xác thực. Class có hai lý do để sửa thì tách. |
| O — Open/closed | Thêm quyền hay gán quyền cho vai trò bằng dữ liệu (migration seed) và một hằng số `Permissions`, không sửa filter hay policy; thêm lỗi bằng giá trị `ErrorCode`, không thêm nhánh `if` ở handler; phản ứng mới với thay đổi bảo mật thì thêm listener, không sửa nơi phát event. |
| L — Liskov | Implementation không làm yếu hợp đồng: `AuthenticationEntryPoint` và `AccessDeniedHandler` luôn trả ProblemDetail đúng status. |
| I — Interface segregation | DTO request riêng cho từng use case (`UpdateOwnProfileRequest` chỉ có họ tên và số điện thoại), không dùng chung một DTO có field thừa; nhờ vậy chặn luôn mass assignment. Policy nhận interface nhỏ `AccessSubject` / `Actor`, không nhận cả entity. |
| D — Dependency inversion | Phụ thuộc vào interface, inject qua constructor: controller giữ `UserManagementService` (interface), không biết `UserManagementServiceImpl`; service giữ `UserRepository`, `PasswordEncoder`, `ApplicationEventPublisher`. Module `user` không gọi thẳng module `auth`: nó phát `UserSecurityChangedEvent`, `auth` tự thu hồi refresh token. |

**Bảo mật**

- Mọi endpoint có đúng một annotation truy cập và URL rule tương ứng; ma trận là nguồn sự thật.
- Mật khẩu: BCrypt, chính sách chung client và server, giới hạn 72 byte; chỉ so sánh qua `PasswordEncoder`.
- Token: access JWT 15 phút, FE chỉ giữ trong bộ nhớ. Refresh token ngẫu nhiên 256 bit trong cookie `HttpOnly; SameSite=Strict; Path=/api/auth`; DB chỉ lưu SHA-256; xoay vòng mỗi lần dùng; dùng lại token đã xoay thì thu hồi cả chuỗi. Access JWT mang claim sid = family của phiên: đăng xuất hay thu hồi family thì access token của phiên bị từ chối ngay (không cần bảng blacklist).
- Không lộ thông tin: lỗi đăng nhập chung một thông báo và cùng thời gian phản hồi; 404 và 500 không trả chi tiết nội bộ; không log mật khẩu, token, hash.
- Chống brute force: giới hạn số lần đăng nhập sai theo username + IP và theo IP, giới hạn đoán mật khẩu hiện tại, vượt thì trả 429. BCrypt không chạy trong transaction. Không ai giao được quyền mình không có, kể cả qua vai trò.
- 401 chỉ cho lỗi "bạn là ai"; vi phạm luật nghiệp vụ trả 400 hoặc 409. Không ai tự đổi vai trò hay tự khóa mình, không ai cấp vai trò có rank cao hơn rank của mình.
- Không quyết định phân quyền theo role hay id trong JWT; không nhận id "của tôi" từ request; không dùng route guard FE làm lớp chặn duy nhất.

**Hiệu năng**

- Danh sách luôn phân trang ở DB, `size` tối đa 100, chỉ sort theo cột trong whitelist.
- Không N+1: không gọi repository trong vòng lặp; một bản ghi cần quan hệ thì `@EntityGraph`; trang danh sách dùng `default_batch_fetch_size`, không fetch join collection kèm phân trang; lọc theo bảng nối bằng subquery `EXISTS`; cập nhật hàng loạt bằng một câu `UPDATE`.
- `open-in-view=false`; transaction ngắn, `readOnly = true` cho truy vấn.
- Cột tra cứu thường xuyên có index: UNIQUE `username`, `email`, `token_hash`; index `user_id`, `family_id`, `expires_at`.
- Việc nặng làm một lần hoặc ngoài request: hash giả cho login tính lúc khởi động; dọn token hết hạn bằng job định kỳ.
- Filter JWT tốn đúng 2 truy vấn mỗi request: kiểm phiên sid trên partial index ix\_refresh\_tokens\_live\_family, rồi 1 truy vấn entity graph (user + vai trò + quyền + phòng ban); chỉ thêm cache khi đo thấy cần, và phải xóa cache khi có `UserSecurityChangedEvent` hoặc đổi vai trò.
- FE: TanStack Query cache với `staleTime`, `keepPreviousData` khi phân trang, debounce ô tìm kiếm 300 ms, chỉ một request refresh chạy tại một thời điểm.

**Không bao giờ**: sửa test hay script audit cho "xanh", thêm `@Disabled`, trả entity ra controller, gọi repository từ controller, thêm thư viện mà không nêu lý do trong báo cáo.

## Bước 5: Kiểm chứng, phải xanh mới được nói "xong"

Chạy từ gốc repo `warden/`:

```bash
# Tĩnh: annotation + URL rule so với docs/rbac-matrix.md (vài giây, không cần Docker)
python3 .claude/skills/secure-feature/scripts/rbac_audit.py

# Đầy đủ: audit + kiểm tầng + test backend (Testcontainers, cần Docker) + typecheck và build frontend
bash .claude/skills/secure-feature/scripts/verify.sh

# Nhanh khi không có Docker: audit + kiểm tầng + typecheck frontend
bash .claude/skills/secure-feature/scripts/verify.sh --fast
```

| Trạng thái audit | Nghĩa | Sửa thế nào |
| --- | --- | --- |
| `UNGUARDED` | Method và class đều không có annotation truy cập | Thêm `@PreAuthorize("hasAuthority('P')")`, `@Authenticated` hoặc `@PublicEndpoint` theo đúng ma trận |
| `MISMATCH` | Annotation khác ma trận | Sửa bên sai; không rõ bên nào sai thì hỏi người dùng |
| `URL_LAYER` | URL rule không đỡ cho ma trận | PUBLIC thì thêm `permitAll()`; dòng có mã quyền thì thêm `hasAuthority` / `hasAnyAuthority` đúng các mã, đặt trước matcher rộng hơn |
| `UNDOCUMENTED` | Code có endpoint, ma trận không có | Thêm dòng vào ma trận, cả local lẫn tab Thiết kế kỹ thuật |
| `NOT_IMPLEMENTED` | Ma trận có, code không có | Viết endpoint hoặc xóa dòng |
| `AMBIGUOUS`, `CUSTOM` | Nhiều annotation cùng cấp, hoặc biểu thức `@PreAuthorize` lạ | Dùng đúng một annotation, dạng literal hasAuthority / hasAnyAuthority mà audit đọc được |
| `UNKNOWN_PERMISSION` | Mã quyền trong ma trận hoặc annotation không có trong Permissions.java | Sửa chính tả, hoặc thêm hằng số kèm migration seed quyền |

Test thất bại thì đọc message, sửa code, chạy lại.

## Bước 6: Báo cáo thay đổi AI

Ghi file `docs/ai-log/YYYY-MM-DD-<slug>.md` theo mẫu các file đã có trong `docs/ai-log/`, đủ 7 mục:

1. **Tóm tắt**: một câu, thay đổi gì và phục vụ màn hình, test case nào.
2. **File theo lớp**: danh sách file, mỗi file một dòng lý do.
3. **RBAC**: dán các dòng audit của endpoint bị chạm.
4. **Quyết định và trade-off**: phương án đã cân nhắc và vì sao bỏ; điểm nào lệch luật Bước 4 và vì sao.
5. **Rủi ro, chỗ reviewer nên đọc kỹ**: kèm `file:line`.
6. **Đã kiểm chứng**: lệnh đã chạy và kết quả (số test pass, audit OK).
7. **Câu hỏi reviewer có thể hỏi**: 3–5 câu kèm trả lời ngắn, để người nộp tự tin bảo vệ code.

Cuối cùng báo người dùng trong chat: đường dẫn file báo cáo và kết quả Bước 5. Còn bước nào đỏ thì nói rõ, không tuyên bố "xong".
