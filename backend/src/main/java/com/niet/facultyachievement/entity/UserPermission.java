package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A single grant: "this user holds this permission" (table
 * {@code user_permissions}).
 *
 * <p>WHY THIS IS A SEPARATE ENTITY AND NOT A {@code @ManyToMany} ON
 * {@link User}: the User entity is loaded on every single authenticated
 * request by {@code CustomUserDetailsService}. Adding a collection mapping
 * there would change how that hot path behaves for every existing feature.
 * Keeping the join table as its own entity means {@link User} is completely
 * untouched, and permissions are fetched with one small, explicit query
 * (see {@code UserPermissionRepository#findPermissionCodesByUserId}).
 *
 * <p>The database enforces UNIQUE (user_id, permission_id), so the same
 * permission can never be granted to the same user twice.
 */
@Entity
@Table(name = "user_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;
}
