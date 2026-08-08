package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "publications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false, unique = true)
    private Achievement achievement;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_type", nullable = false, length = 30)
    private PublicationType publicationType;

    @Column(name = "journal_conference_name", nullable = false, length = 255)
    private String journalConferenceName;

    @Column(name = "publisher", length = 150)
    private String publisher;

    @Column(name = "doi", length = 100)
    private String doi;

    @Column(name = "isbn_issn", length = 50)
    private String isbnIssn;

    @Column(name = "volume", length = 20)
    private String volume;

    @Column(name = "issue", length = 20)
    private String issue;

    @Column(name = "pages", length = 30)
    private String pages;

    @Column(name = "impact_factor", precision = 5, scale = 3)
    private BigDecimal impactFactor;

    @Enumerated(EnumType.STRING)
    @Column(name = "indexing", nullable = false, length = 30)
    private PublicationIndexing indexing;
}
