package com.niet.facultyachievement.repository;

import com.niet.facultyachievement.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionCode(String permissionCode);

    /**
     * Look up several permissions at once by code. Used when saving a
     * permission set so the whole request needs a single query instead of one
     * per checkbox.
     */
    List<Permission> findByPermissionCodeIn(Collection<String> permissionCodes);
}
