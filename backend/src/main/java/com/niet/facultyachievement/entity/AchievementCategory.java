package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "achievement_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
