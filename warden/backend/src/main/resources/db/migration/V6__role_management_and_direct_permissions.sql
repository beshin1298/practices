-- Role management on the "Vai trò & quyền" screen, and per-user permissions ("quyền lẻ").
--   RBAC_READ    see the role x permission matrix (HR, ADMIN; HR also needs the role list to create accounts)
--   RBAC_UPDATE  change the permissions of a role ranked below yours (ADMIN only, so ADMIN itself stays locked)
--   user_permissions  extra permissions for one user on top of their roles (many-to-many); never changes the rank

INSERT INTO permissions (code, description) VALUES
    ('RBAC_READ',   'Xem ma trận vai trò × quyền'),
    ('RBAC_UPDATE', 'Sửa quyền của vai trò có cấp thấp hơn mình');

UPDATE permissions SET description = 'Gán vai trò và quyền lẻ cho tài khoản' WHERE code = 'USER_ASSIGN_ROLE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON (p.code = 'RBAC_READ' AND r.code IN ('HR', 'ADMIN'))
                   OR (p.code = 'RBAC_UPDATE' AND r.code = 'ADMIN');

CREATE TABLE user_permissions (
    user_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, permission_id)
);
CREATE INDEX ix_user_permissions_permission ON user_permissions (permission_id);
