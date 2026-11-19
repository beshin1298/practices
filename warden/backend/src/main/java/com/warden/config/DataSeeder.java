package com.warden.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.warden.department.repository.DepartmentRepository;
import com.warden.role.repository.RoleRepository;
import com.warden.user.entity.User;
import com.warden.user.repository.UserRepository;

/**
 * Demo accounts, one per role, created when missing (idempotent, safe on every start). Passwords are hashed at
 * startup (no BCrypt hash hard-coded in a migration) and come from SEED_* env vars outside dev.
 * Roles, permissions and departments are reference data seeded by Flyway (V3, V4).
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seed;

    public DataSeeder(UserRepository users, RoleRepository roles, DepartmentRepository departments,
            PasswordEncoder passwordEncoder, SeedProperties seed) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.seed = seed;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        create("admin", "Quản trị viên", "Quản trị hệ thống", seed.adminPassword(), List.of("ADMIN"), "IT");
        create("hr", "Trần Thị Hạnh", "Chuyên viên nhân sự", seed.userPassword(), List.of("HR"), "HR");
        create("manager", "Lê Minh Quân", "Trưởng phòng kinh doanh", seed.userPassword(), List.of("MANAGER"), "SALES");
        create("user", "Nguyễn Văn An", "Nhân viên kinh doanh", seed.userPassword(), List.of("EMPLOYEE"), "SALES");
        create("lan", "Phạm Thu Lan", "Kế toán kiêm kinh doanh", seed.userPassword(), List.of("EMPLOYEE"), "ACC", "SALES");
        create("dev", "Đỗ Quang Huy", "Lập trình viên", seed.userPassword(), List.of("EMPLOYEE"), "IT");
        log.info("Demo accounts ready: admin, hr, manager, user, lan, dev");
    }

    /** Idempotent: an existing account (same username) is left untouched. */
    private void create(String username, String fullName, String jobTitle, String password, List<String> roleCodes,
            String... departmentCodes) {
        if (users.existsByUsername(username)) {
            return;
        }
        // Hashed per account (own salt): equal demo passwords still give different hashes.
        User user = new User(username, username + "@warden.local", fullName, passwordEncoder.encode(password));
        user.setJobTitle(jobTitle);
        user.replaceRoles(roleCodes.stream().map(code -> roles.findByCode(code).orElseThrow()).toList());
        user.replaceDepartments(Arrays.stream(departmentCodes).map(code -> departments.findByCode(code).orElseThrow()).toList());
        users.save(user);
    }

    @ConfigurationProperties("app.seed")
    public record SeedProperties(boolean enabled, String adminPassword, String userPassword) {
    }
}
