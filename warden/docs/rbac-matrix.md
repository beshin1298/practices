# RBAC matrix

Machine-readable copy of the section "Phân quyền" in the "Thiết kế kỹ thuật" tab of the Warden docs on Claude Docs.
Keep the table columns as they are; three tools read this file:

- `.claude/skills/secure-feature/scripts/rbac_audit.py` (static): the access annotation on every controller method and the URL
  rules in `SecurityConfig` must both equal the **API** table; every permission name must exist in `Permissions.java`.
- `backend/src/test/java/com/warden/RbacMatrixIntegrationTest.java` (runtime): the **Roles × Permissions** table must equal the
  `role_permissions` rows in the database, and every API row is called with every role and anonymously.
- `UserAccessScopeIntegrationTest` covers the row scope (own / department / all, rank) that a table cannot express.

Access column: `PUBLIC` (anyone), `AUTHENTICATED` (any logged-in user), or permission codes separated by commas (holding any one is enough).

## Roles × Permissions

| Permission | EMPLOYEE | MANAGER | HR | ADMIN | Description |
| --- | --- | --- | --- | --- | --- |
| USER_READ_OWN | ✓ | ✓ | ✓ | ✓ | Xem hồ sơ của mình |
| USER_UPDATE_OWN | ✓ | ✓ | ✓ | ✓ | Sửa hồ sơ của mình (họ tên, số điện thoại), đổi mật khẩu |
| USER_READ_DEPARTMENT |  | ✓ |  | ✓ | Xem nhân sự có chung phòng ban |
| USER_UPDATE_DEPARTMENT |  | ✓ |  | ✓ | Sửa nhân sự có chung phòng ban và cấp thấp hơn |
| USER_READ_ALL |  |  | ✓ | ✓ | Xem toàn bộ nhân sự |
| USER_UPDATE_ALL |  |  | ✓ | ✓ | Sửa mọi thông tin nhân sự, kể cả email và phòng ban |
| USER_CREATE |  |  | ✓ | ✓ | Tạo tài khoản (app nội bộ, không có đăng ký) |
| USER_DISABLE |  |  | ✓ | ✓ | Khóa / mở khóa tài khoản (thay cho xóa) |
| USER_ASSIGN_ROLE |  |  |  | ✓ | Gán vai trò và quyền lẻ |
| RBAC_READ |  |  | ✓ | ✓ | Xem ma trận vai trò × quyền |
| RBAC_UPDATE |  |  |  | ✓ | Sửa quyền của vai trò có cấp thấp hơn mình |

Row scope rules (UserAccessPolicy):

- DEPARTMENT scope: reading reaches everyone who shares at least one department with the actor; changing also needs
  the target to be ranked below the actor. A user outside the reader's scope answers 404, exactly like a missing id.
- ALL scope reaches anyone not ranked above the actor (HR cannot edit an ADMIN).
- Changing an email needs USER_UPDATE_ALL. Nobody grants a role above their own rank. Nobody changes their own roles or status.
- Creating an account or changing roles may not hand out a permission the actor does not hold (through the roles' permissions);
  for this check a wider scope covers a narrower one (USER_*_ALL covers USER_*_DEPARTMENT covers USER_*_OWN).
- The last enabled top-ranked user can be neither disabled nor demoted (checked under a lock).
- Direct permissions ("quyền lẻ", table user_permissions) add to the role permissions and never change the rank. Granting them
  follows the role rules (not yourself, target not ranked above you), and only permissions the granter holds may be added or removed.
- Role permissions can be changed only for roles ranked below the actor, so ADMIN (the top role) is locked; only permissions
  the actor holds may be added or removed.
- Ranks: EMPLOYEE 0 < MANAGER 1 < HR 2 < ADMIN 3. A person's rank is the highest rank among their roles.

## API

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| POST | /api/auth/login | PUBLIC | Đăng nhập: access token trong body, refresh token trong cookie HttpOnly |
| POST | /api/auth/refresh | PUBLIC | Đổi refresh token (cookie) lấy access token mới, xoay vòng refresh token |
| POST | /api/auth/logout | PUBLIC | Thu hồi phiên hiện tại (refresh token trong cookie), xóa cookie |
| GET | /api/users/me | USER_READ_OWN | Hồ sơ của tôi, kèm vai trò, phòng ban, quyền hiệu lực |
| PUT | /api/users/me | USER_UPDATE_OWN | Sửa họ tên, số điện thoại của tôi |
| PUT | /api/users/me/password | USER_UPDATE_OWN | Đổi mật khẩu, thu hồi mọi phiên cũ, trả phiên mới |
| GET | /api/users | USER_READ_DEPARTMENT, USER_READ_ALL | Danh sách nhân sự trong phạm vi: phân trang, tìm kiếm, lọc phòng ban |
| GET | /api/users/{id} | USER_READ_DEPARTMENT, USER_READ_ALL | Chi tiết một nhân sự trong phạm vi |
| POST | /api/users | USER_CREATE | Tạo tài khoản, gán vai trò và phòng ban |
| PUT | /api/users/{id} | USER_UPDATE_DEPARTMENT, USER_UPDATE_ALL | Sửa hồ sơ nhân sự (email cần USER_UPDATE_ALL) |
| PUT | /api/users/{id}/departments | USER_UPDATE_ALL | Gán phòng ban |
| PUT | /api/users/{id}/roles | USER_ASSIGN_ROLE | Gán vai trò |
| PUT | /api/users/{id}/permissions | USER_ASSIGN_ROLE | Gán quyền lẻ (thay toàn bộ danh sách) |
| PATCH | /api/users/{id}/status | USER_DISABLE | Khóa / mở khóa |
| GET | /api/departments | AUTHENTICATED | Danh sách phòng ban |
| GET | /api/roles | RBAC_READ | Danh sách vai trò kèm quyền |
| PUT | /api/roles/{id}/permissions | RBAC_UPDATE | Thay quyền của một vai trò có cấp thấp hơn mình |

## Screens

| Route | Access | Note |
| --- | --- | --- |
| /login | PUBLIC | Đã đăng nhập thì chuyển về /account |
| /account | USER_READ_OWN | Xem hồ sơ; sửa họ tên, số điện thoại |
| /account/password | USER_UPDATE_OWN | Đổi mật khẩu |
| /people | USER_READ_DEPARTMENT, USER_READ_ALL | Quản lý thấy phòng mình; HR và Admin thấy tất cả; nút thao tác theo quyền |
| /roles | RBAC_READ | Ma trận vai trò × quyền; ai có RBAC_UPDATE bấm trực tiếp để sửa vai trò cấp thấp hơn |
