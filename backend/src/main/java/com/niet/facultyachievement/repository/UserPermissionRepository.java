package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    /**
     * Returns just the permission code strings for one user.
     *
     * <p>This runs on every authenticated request (from
     * {@code CustomUserDetailsService}), so it deliberately selects only the
     * code column instead of loading whole Permission entities. For a user
     * with no grants it returns an empty list, which means that user behaves
     * exactly as they did before permissions existed.
     */
    @Query("SELECT up.permission.permissionCode FROM UserPermission up WHERE up.user.id = :userId")
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * All grant rows for one user, used when replacing a user's permission set.
     */
    List<UserPermission> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
