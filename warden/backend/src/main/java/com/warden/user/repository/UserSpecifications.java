package com.warden.user.repository;

import java.util.Collection;
import java.util.Locale;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.warden.department.entity.Department;
import com.warden.user.entity.User;

/**
 * Composable filters for the user list. A new filter is a new method here, not a new repository
 * query per combination (open/closed). A null filter means "no condition".
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Case-insensitive match on username, email or full name. */
    public static Specification<User> matches(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        // %, _ and \ typed by the user are literal characters, not wildcards.
        String literal = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + literal + "%";
        return (root, q, cb) -> cb.or(
                cb.like(root.get("username"), pattern, '\\'), // stored lower-case
                cb.like(root.get("email"), pattern, '\\'),    // stored lower-case
                cb.like(cb.lower(root.get("fullName")), pattern, '\\'));
    }

    /**
     * Users belonging to at least one of the departments. EXISTS instead of a join: a person in two
     * of the departments must still appear once, and paging stays correct without DISTINCT.
     */
    public static Specification<User> inAnyDepartment(Collection<Long> departmentIds) {
        if (departmentIds == null) {
            return null;
        }
        if (departmentIds.isEmpty()) {
            return (root, q, cb) -> cb.disjunction(); // matches nothing
        }
        return (root, query, cb) -> {
            Subquery<Long> membership = query.subquery(Long.class);
            Root<User> member = membership.from(User.class);
            Join<User, Department> department = member.join("departments");
            membership.select(member.get("id"))
                    .where(cb.equal(member.get("id"), root.get("id")), department.get("id").in(departmentIds));
            return cb.exists(membership);
        };
    }
}
