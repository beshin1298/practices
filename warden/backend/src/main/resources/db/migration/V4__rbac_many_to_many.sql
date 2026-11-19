-- Role-based access control, fully many-to-many:
--   users  <-> roles        (user_roles)        a person may hold several roles
--   roles  <-> permissions  (role_permissions)  a role is a bundle of CRUD permissions with a scope
-- New roles or a changed permission set are data changes, not code changes.
-- docs/rbac-matrix.md ("Roles x Permissions") is the spec; RbacMatrixIntegrationTest compares it with these rows.

CREATE TABLE roles (
    id   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    -- Orders roles for "nobody acts on someone ranked above them".
    rank INTEGER      NOT NULL,

    CONSTRAINT uk_roles_code       UNIQUE (code),
    CONSTRAINT ck_roles_code_upper CHECK (code = upper(code)),
    CONSTRAINT ck_roles_rank       CHECK (rank >= 0)
);

CREATE TABLE permissions (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NOT NULL,

    CONSTRAINT uk_permissions_code             UNIQUE (code),
    CONSTRAINT ck_permissions_code_upper       CHECK (code = upper(code)),
    -- "ROLE_" is Spring Security's role prefix: a permission named like that would pass hasRole().
    CONSTRAINT ck_permissions_not_role_prefix  CHECK (code NOT LIKE 'ROLE\_%')
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

INSERT INTO roles (code, name, rank) VALUES
    ('EMPLOYEE', 'Nhân viên',         0),
    ('MANAGER',  'Quản lý',           1),
    ('HR',       'Nhân sự',           2),
    ('ADMIN',    'Quản trị hệ thống', 3);

-- CRUD x scope. Delete is a soft delete (USER_DISABLE): accounts are locked, never removed.
INSERT INTO permissions (code, description) VALUES
    ('USER_READ_OWN',          'Xem hồ sơ của mình'),
    ('USER_UPDATE_OWN',        'Sửa hồ sơ của mình (họ tên, số điện thoại), đổi mật khẩu'),
    ('USER_READ_DEPARTMENT',   'Xem nhân sự có chung phòng ban'),
    ('USER_UPDATE_DEPARTMENT', 'Sửa nhân sự có chung phòng ban và cấp thấp hơn'),
    ('USER_READ_ALL',          'Xem toàn bộ nhân sự'),
    ('USER_UPDATE_ALL',        'Sửa mọi thông tin nhân sự, kể cả email và phòng ban'),
    ('USER_CREATE',            'Tạo tài khoản (app nội bộ, không có đăng ký)'),
    ('USER_DISABLE',           'Khóa / mở khóa tài khoản'),
    ('USER_ASSIGN_ROLE',       'Gán vai trò cho tài khoản');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON
       (r.code = 'EMPLOYEE' AND p.code IN ('USER_READ_OWN', 'USER_UPDATE_OWN'))
    OR (r.code = 'MANAGER'  AND p.code IN ('USER_READ_OWN', 'USER_UPDATE_OWN',
                                           'USER_READ_DEPARTMENT', 'USER_UPDATE_DEPARTMENT'))
    OR (r.code = 'HR'       AND p.code IN ('USER_READ_OWN', 'USER_UPDATE_OWN', 'USER_READ_ALL',
                                           'USER_UPDATE_ALL', 'USER_CREATE', 'USER_DISABLE'))
    OR (r.code = 'ADMIN');

-- Existing dev rows keep their access (ADMIN -> ADMIN, USER -> EMPLOYEE), then the old column goes.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = CASE WHEN u.role = 'ADMIN' THEN 'ADMIN' ELSE 'EMPLOYEE' END;

ALTER TABLE users DROP COLUMN role; -- also drops ck_users_role

-- Profile fields: phone is edited by the employee; job title by their manager or HR.
ALTER TABLE users ADD COLUMN phone     VARCHAR(20);
ALTER TABLE users ADD COLUMN job_title VARCHAR(100);
