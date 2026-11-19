# 2026-11-19 — Vá lỗ xử lý exception; sửa ma trận vai trò × quyền; quyền lẻ

## 1. Tóm tắt

Hai thay đổi:

- **Exception:** lỗi trong filter chain giờ cũng thành ProblemDetail có `code`; frontend không còn báo nhầm lỗi máy chủ thành
  mất mạng, không đăng xuất khi máy chủ lỗi, không trắng trang khi component lỗi.
- **Phân quyền:** màn hình 5. Vai trò & quyền chỉ còn cho HR và Admin, Admin sửa ma trận ngay trên trang; màn hình 4. Nhân sự
  có thêm quyền lẻ cho từng người.

Test case: VT-01, VT-04 (đổi nội dung), VT-05, VT-06, NS-11, NS-12.

## 2. File theo lớp

**Exception**

- `common/web/FilterChainExceptionFilter.java` (mới): bắt exception thoát khỏi filter chain, chuyển cho
  `HandlerExceptionResolver`, tức là `GlobalExceptionHandler`. Đặt ngay trước chain của Spring Security.
- `frontend/src/lib/errors.ts`: phản hồi 5xx không có `code` thành `SERVER_UNAVAILABLE`; chỉ không có phản hồi mới là `NETWORK_ERROR`.
- `frontend/src/lib/http.ts`: chỉ refresh bị từ chối (401) mới đăng xuất.
- `frontend/src/app/router.tsx`, `components/layout/StatusPage.tsx`: `errorElement` / `RouteErrorPage` ở 3 tầng route.
- `backend/src/test/java/com/warden/ErrorHandlingIntegrationTest.java` (mới, 5 test).

**Phân quyền** (theo thứ tự lớp của skill)

1. `V6__role_management_and_direct_permissions.sql`: quyền `RBAC_READ` (HR, ADMIN), `RBAC_UPDATE` (ADMIN); đổi mô tả
   `USER_ASSIGN_ROLE`; bảng `user_permissions` (PK kép, FK cascade, index `permission_id`).
2. Entity: `Role.replacePermissions`; `User.directPermissions` + `replaceDirectPermissions`, `permissionCodes()` = vai trò ∪ quyền lẻ.
3. DTO: `role/dto/request/UpdateRolePermissionsRequest`, `user/dto/request/AssignPermissionsRequest` (`@NotNull`, có thể rỗng);
   `UserResponse.directPermissions`.
4. Mapper: `UserMapper` map `directPermissions`.
5. Service:
   - `RoleService.updatePermissions`;
   - `UserManagementService.assignPermissions`;
   - `UserAccessPolicy.checkPermissionGrant`;
   - `Actor.holdsAll`;
   - `role/repository/PermissionRepository` (mới), `RoleRepository.findWithPermissionsById`;
   - entity graph xác thực có thêm `directPermissions`;
   - `ErrorCode`: `PERMISSION_NOT_FOUND` (400), `ROLE_LOCKED` (409).
6. Controller:
   - `RoleController`: bỏ `@Authenticated` cấp class; GET cần `RBAC_READ`, thêm PUT cần `RBAC_UPDATE`.
   - `UserController`: thêm PUT `/{id}/permissions`.
7. URL rule: `SecurityConfig` có 3 matcher mới.
8. Test: `RolePermissionIntegrationTest` (mới, 4 test). `RbacMatrixIntegrationTest` tự sinh case cho 2 dòng ma trận mới.
9. FE types + API: `PermissionCode` thêm 2 mã, `UserResponse.directPermissions`, `useRoles(enabled)`,
   `useUpdateRolePermissions`, `useAssignPermissions`.
10. FE trang + route:
    - `/roles` bọc `RequirePermission ['RBAC_READ']`, menu ẩn;
    - `RolesPage`: ô bấm được với vai trò sửa được;
    - `AssignDialog` có thêm kiểu `permissions`;
    - `PeoplePage` có nút "Quyền lẻ" và dòng "+N quyền lẻ";
    - `CheckboxList` generic.

**Spec:** `docs/rbac-matrix.md`; tab 4, tab 5, Thiết kế kỹ thuật, Schema, Luồng kỹ thuật, Nhật ký dòng 22–23.

## 3. RBAC

```
| ok | GET | `/api/roles` | RBAC_READ | RBAC_READ | RBAC_READ | OK | backend/src/main/java/com/warden/role/controller/RoleController.java:35 |
| ok | PUT | `/api/roles/{id}/permissions` | RBAC_UPDATE | RBAC_UPDATE | RBAC_UPDATE | OK | backend/src/main/java/com/warden/role/controller/RoleController.java:42 |
| ok | PUT | `/api/users/{id}/permissions` | USER_ASSIGN_ROLE | USER_ASSIGN_ROLE | USER_ASSIGN_ROLE | OK | backend/src/main/java/com/warden/user/controller/UserController.java:101 |
RBAC audit: 17 endpoints, 17 OK, 0 finding(s). Code and docs/rbac-matrix.md agree on both layers.
```

## 4. Quyết định và trade-off

- **Filter giao lỗi cho `HandlerExceptionResolver`** thay vì tự ghi JSON: chỉ một nơi quyết định exception nào thành mã nào.
  Cách khác là tùy biến trang `/error` của Spring Boot, nhưng sẽ thành hai định dạng lỗi phải giữ đồng bộ.
- **Tên `RBAC_READ` / `RBAC_UPDATE`** thay cho `ROLE_READ` / `ROLE_UPDATE` như lúc chốt: V4 có CHECK cấm tiền tố `ROLE_` vì
  Spring Security coi `ROLE_X` là vai trò X. Migration chạy thử đã bị ràng buộc này chặn, và ràng buộc đã làm đúng việc của nó.
- **Khóa ADMIN bằng luật rank** (chỉ sửa vai trò có rank < mình) thay vì so sánh `code == 'ADMIN'`: không viết cứng tên vai
  trò, và nếu sau này có người được giao `RBAC_UPDATE` thì luật vẫn đúng.
- **"Chỉ bật/tắt quyền mình có" xét phần chênh lệch** (thêm hoặc bỏ), không xét cả danh sách. Nhờ đó người gán vẫn lưu được
  khi người kia đang có sẵn một quyền mà mình không có.
- **Dùng lại `USER_ASSIGN_ROLE` cho quyền lẻ** (người dùng đã chốt) thay vì thêm mã mới: gán vai trò và gán quyền lẻ có cùng
  mức rủi ro và cùng luật.
- **Không thêm `GET /api/permissions`**: frontend đã có danh sách mã cố định (`PERMISSION_LABELS`) và mã quyền chỉ thêm qua
  migration kèm code.
- **Repository có `default` method ném `AppException`** (`findAllByCodeOrThrow`): lệch nhẹ khỏi "repository chỉ truy vấn".
  Đổi lại hai service dùng chung một chỗ kiểm mã quyền.

## 5. Rủi ro, chỗ reviewer nên đọc kỹ

- `backend/src/main/java/com/warden/role/service/impl/RoleServiceImpl.java:43`: sửa vai trò ảnh hưởng mọi người giữ vai trò
  đó ngay lập tức. Test khôi phục seed trong `finally` vì DB test dùng chung.
- `backend/src/main/java/com/warden/user/service/UserAccessPolicy.java`, `checkPermissionGrant`: quyền lẻ không đổi rank, nên
  Nhân viên được `USER_UPDATE_ALL` sẽ sửa được Nhân viên khác (rank ≤ 0), kể cả email. Đây là hệ quả của quy tắc đã chốt.
- `backend/src/main/java/com/warden/common/web/FilterChainExceptionFilter.java`: nếu response đã gửi một phần thì ném lại
  exception (không ghi đè). Lỗi không ánh xạ được cũng ném lại.
- `frontend/src/lib/http.ts`: máy chủ lỗi lúc refresh thì giữ phiên. Nếu refresh token thật sự đã chết, lần gọi sau khi máy chủ
  hồi phục sẽ nhận 401 và đăng xuất bình thường.

## 6. Đã kiểm chứng

- `bash .claude/skills/secure-feature/scripts/verify.sh` xanh:
  - audit 17/17;
  - kiểm tầng OK (7 controller/filter, 9 service interface + Impl);
  - 59/59 test backend (0 fail, 0 skip), gồm `ErrorHandlingIntegrationTest` 5/5, `RolePermissionIntegrationTest` 4/4,
    `RbacMatrixIntegrationTest` 18/18;
  - frontend typecheck + build OK.
- Lần chạy đầu: migration V6 bị CHECK `ck_permissions_not_role_prefix` chặn; đã đổi tên quyền rồi chạy lại.

## 7. Câu hỏi reviewer có thể hỏi

1. **Không thấy try/catch, xử lý exception ở đâu?** Ở ba lớp:
   - service ném `AppException(ErrorCode)`;
   - `GlobalExceptionHandler` biến mọi lỗi trong MVC thành ProblemDetail;
   - `FilterChainExceptionFilter` đưa lỗi trong filter về cùng handler, còn `ProblemDetailsSecurityHandler` lo 401/403.

   Controller không cần try/catch.
2. **Sao Admin không tự khóa mình ra ngoài được?** Chỉ sửa được vai trò có rank thấp hơn mình. ADMIN là rank cao nhất nên
   không ai sửa được nó (409 `ROLE_LOCKED`); `RBAC_UPDATE` của ADMIN không thể bị gỡ.
3. **Giao quyền gán cho một nhân viên có nguy hiểm không?** Người đó chỉ chia được quyền mình đang có, cho người không cao hơn
   mình, và không tự gán cho mình (test `directPermissionLimits`), nên không leo thang được.
4. **Sửa ma trận xong người dùng có phải đăng nhập lại?** Không. Filter nạp quyền từ DB ở mỗi request (test
   `adminEditsLowerRoleEffectiveImmediately`).
5. **Sao Quản lý không xem được trang vai trò nữa mà vẫn dùng app bình thường?** Quản lý không cần danh sách vai trò:
   `useRoles(enabled)` chỉ gọi API trong hộp thoại tạo tài khoản hoặc gán vai trò, mà hai hộp thoại đó chỉ HR và Admin mở được.
