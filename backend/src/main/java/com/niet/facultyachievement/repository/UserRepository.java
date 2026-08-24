package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.User;
import com.niet.facultyachievement.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmployeeId(String employeeId);
    List<User> findByDepartmentId(Long departmentId);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);

    long countByDepartmentId(Long departmentId);
    long countByStatus(UserStatus status);

    /**
     * How many users hold a given role in a given state.
     *
     * <p>Exists for one reason: the last-administrator guard. Before an account
     * is deactivated or demoted out of the administrator role, the service counts
     * the remaining ACTIVE administrators. If that would reach zero the change is
     * refused with a 409, because nobody would be left who could undo it — the
     * portal would be permanently locked out of its own user management.
     */
    long countByRoleIdAndStatus(Long roleId, UserStatus status);

    /**
     * Excludes one specific user from the count above.
     *
     * <p>Needed because the guard has to ask "how many administrators would still
     * be active if this one were removed?". Counting first and subtracting one by
     * hand would be wrong whenever the user in question is already inactive.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.id = :roleId AND u.status = :status AND u.id <> :excludedUserId")
    long countByRoleAndStatusExcludingUser(@Param("roleId") Long roleId,
                                           @Param("status") UserStatus status,
                                           @Param("excludedUserId") Long excludedUserId);

    /**
     * Users per department as {@code [departmentId, count]} rows.
     *
     * <p>One GROUP BY query instead of a count per department, matching the
     * aggregation style already used in {@code DashboardServiceImpl}. With a
     * handful of departments the difference is small; the point is that the
     * management screen does not get slower as departments are added.
     */
    @Query("SELECT u.department.id, COUNT(u) FROM User u WHERE u.department IS NOT NULL GROUP BY u.department.id")
    List<Object[]> countUsersGroupedByDepartment();
}
