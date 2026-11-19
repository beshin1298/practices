package com.warden.user.service;

import static com.warden.role.constant.Permissions.USER_CREATE;
import static com.warden.role.constant.Permissions.USER_DISABLE;
import static com.warden.role.constant.Permissions.USER_READ_ALL;
import static com.warden.role.constant.Permissions.USER_READ_DEPARTMENT;
import static com.warden.role.constant.Permissions.USER_READ_OWN;
import static com.warden.role.constant.Permissions.USER_UPDATE_ALL;
import static com.warden.role.constant.Permissions.USER_UPDATE_DEPARTMENT;
import static com.warden.role.constant.Permissions.USER_UPDATE_OWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.warden.common.error.AppException;
import com.warden.common.error.ErrorCode;
import com.warden.role.constant.Permissions;

/**
 * Pure unit test of the scope rules: no Spring, no database. Possible because the policy depends on
 * the small Actor / AccessSubject interfaces instead of entities (interface segregation).
 */
class UserAccessPolicyTest {

    private static final long IT = 1, SALES = 2, ACC = 3;
    private static final Set<String> EMPLOYEE = Set.of(USER_READ_OWN, USER_UPDATE_OWN);
    private static final Set<String> MANAGER = Set.of(USER_READ_OWN, USER_UPDATE_OWN, USER_READ_DEPARTMENT, USER_UPDATE_DEPARTMENT);
    private static final Set<String> HR = Set.of(USER_READ_OWN, USER_UPDATE_OWN, USER_READ_ALL, USER_UPDATE_ALL, USER_CREATE, USER_DISABLE);

    private final UserAccessPolicy policy = new UserAccessPolicy();

    private record Person(Long id, int rank, Set<Long> departmentIds, Set<String> permissions) implements Actor {
    }

    private static Person person(long id, int rank, Set<String> permissions, Long... departments) {
        return new Person(id, rank, Set.of(departments), permissions);
    }

    @Test
    @DisplayName("Manager: same department and lower rank only")
    void managerScope() {
        Person manager = person(1, 1, MANAGER, SALES);
        assertThat(policy.canUpdate(manager, person(2, 0, EMPLOYEE, SALES))).isTrue();
        assertThat(policy.canUpdate(manager, person(3, 0, EMPLOYEE, IT))).isFalse();      // other department
        assertThat(policy.canUpdate(manager, person(4, 2, HR, SALES))).isFalse();         // ranked above
        assertThat(policy.canUpdate(manager, person(5, 1, MANAGER, SALES))).isFalse();    // same rank
        assertThat(policy.canUpdate(manager, person(6, 0, EMPLOYEE, ACC, SALES))).isTrue(); // shares one of two
        assertThat(policy.canRead(manager, person(3, 0, EMPLOYEE, IT))).isFalse();
    }

    @Test
    @DisplayName("Manager may edit a profile but not change the email")
    void managerCannotChangeEmail() {
        Person manager = person(1, 1, MANAGER, SALES);
        Person employee = person(2, 0, EMPLOYEE, SALES);
        assertThatCode(() -> policy.checkUpdate(manager, employee, false)).doesNotThrowAnyException();
        assertDenied(() -> policy.checkUpdate(manager, employee, true));
    }

    @Test
    @DisplayName("HR: company-wide, but never above its own rank")
    void hrScope() {
        Person hr = person(1, 2, HR, IT);
        Person admin = person(9, 3, Permissions.ALL, IT);
        assertThat(policy.canUpdate(hr, person(2, 0, EMPLOYEE, SALES))).isTrue();
        assertThat(policy.canUpdate(hr, person(3, 2, HR, ACC))).isTrue();
        assertThat(policy.canUpdate(hr, admin)).isFalse();
        assertDenied(() -> policy.checkStatusChange(hr, admin));
        assertDenied(() -> policy.checkCreate(hr, 3, Set.of()));                  // cannot create an ADMIN
        assertThatCode(() -> policy.checkCreate(hr, 2, Set.of())).doesNotThrowAnyException();
        // A wider scope covers a narrower one: HR (USER_*_ALL) may hand out a manager's USER_*_DEPARTMENT ...
        assertThatCode(() -> policy.checkCreate(hr, 1, Set.of(Permissions.USER_READ_DEPARTMENT,
                Permissions.USER_UPDATE_DEPARTMENT, Permissions.USER_READ_OWN))).doesNotThrowAnyException();
        // ... but not a permission it does not hold at all, even through a lower role.
        assertDenied(() -> policy.checkCreate(hr, 1, Set.of(Permissions.USER_ASSIGN_ROLE)));
    }

    @Test
    @DisplayName("Employee: only themselves")
    void employeeScope() {
        Person employee = person(1, 0, EMPLOYEE, IT);
        assertThat(policy.canRead(employee, employee)).isTrue();
        assertThat(policy.canRead(employee, person(2, 0, EMPLOYEE, IT))).isFalse();
        assertThat(policy.canUpdate(employee, person(2, 0, EMPLOYEE, IT))).isFalse();
    }

    @Test
    @DisplayName("Nobody changes their own roles or status; nobody grants a role above their own")
    void selfAndEscalation() {
        Person admin = person(1, 3, Permissions.ALL, IT);
        assertThatThrownBy(() -> policy.checkRoleChange(admin, admin, 0, Set.of()))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SELF_MODIFICATION));
        assertThatThrownBy(() -> policy.checkStatusChange(admin, admin))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SELF_MODIFICATION));
        assertDenied(() -> policy.checkRoleChange(person(2, 2, HR, IT), person(3, 0, EMPLOYEE, IT), 1, Set.of())); // HR lacks the permission
    }

    @Test
    @DisplayName("List scope: managers are pinned to their departments, HR may filter freely")
    void visibleDepartments() {
        Person manager = person(1, 1, MANAGER, SALES, ACC);
        assertThat(policy.visibleDepartments(manager, null)).containsExactlyInAnyOrder(SALES, ACC);
        assertThat(policy.visibleDepartments(manager, SALES)).containsExactly(SALES);
        assertThat(policy.visibleDepartments(manager, IT)).isEmpty();

        Person hr = person(2, 2, HR, IT);
        assertThat(policy.visibleDepartments(hr, null)).isNull();
        assertThat(policy.visibleDepartments(hr, SALES)).containsExactly(SALES);
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
}
