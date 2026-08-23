package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One row in the catalogue of permissions the system understands
 * (table {@code permissions}, seeded by V3__permissions.sql).
 *
 * <p>{@code permissionCode} is the exact string that becomes a Spring
 * Security authority, e.g. {@code CREATE_FACULTY}. The matching Java
 * constants live in {@code security/Permissions.java}.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_code", nullable = false, unique = true, length = 50)
    private String permissionCode;

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
