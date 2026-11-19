-- Internal app organised by department. Departments are reference data, so they are seeded here
-- (same in every environment); user accounts are seeded by DataSeeder because they carry passwords.
CREATE TABLE departments (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_departments_code       UNIQUE (code),
    CONSTRAINT uk_departments_name       UNIQUE (name),
    CONSTRAINT ck_departments_code_upper CHECK (code = upper(code))
);

INSERT INTO departments (code, name) VALUES
    ('BOD',   'Ban điều hành'),
    ('IT',    'Công nghệ thông tin'),
    ('HR',    'Nhân sự'),
    ('ACC',   'Kế toán'),
    ('SALES', 'Kinh doanh');

-- Many-to-many: a person can work for several departments (kiêm nhiệm).
-- The composite PK serves "departments of a user"; the extra index serves "users of a department".
CREATE TABLE user_departments (
    user_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    department_id BIGINT NOT NULL REFERENCES departments (id),
    PRIMARY KEY (user_id, department_id)
);
CREATE INDEX ix_user_departments_department ON user_departments (department_id);

-- Existing dev rows: admins go to IT, everyone else to SALES.
INSERT INTO user_departments (user_id, department_id)
SELECT u.id, d.id
FROM users u
JOIN departments d ON d.code = CASE WHEN u.role = 'ADMIN' THEN 'IT' ELSE 'SALES' END;
