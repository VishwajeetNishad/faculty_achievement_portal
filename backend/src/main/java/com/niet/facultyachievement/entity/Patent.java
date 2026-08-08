package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "patents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false, unique = true)
    private Achievement achievement;

    @Column(name = "patent_number", nullable = false, length = 100)
    private String patentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "patent_status", nullable = false, length = 20)
    private PatentStatus patentStatus;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(name = "grant_date")
    private LocalDate grantDate;
}
