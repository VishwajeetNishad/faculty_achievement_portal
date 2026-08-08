package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "research_grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResearchGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false, unique = true)
    private Achievement achievement;

    @Column(name = "funding_agency", nullable = false, length = 200)
    private String fundingAgency;

    @Column(name = "project_title", nullable = false, length = 255)
    private String projectTitle;

    @Column(name = "grant_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grantAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 30)
    private ProjectType projectType;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_status", nullable = false, length = 20)
    private GrantStatus grantStatus;
}
