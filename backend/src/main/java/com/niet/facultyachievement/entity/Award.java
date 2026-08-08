package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "awards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Award {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false, unique = true)
    private Achievement achievement;

    @Column(name = "award_name", nullable = false, length = 255)
    private String awardName;

    @Column(name = "awarding_body", nullable = false, length = 200)
    private String awardingBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "award_level", nullable = false, length = 30)
    private AwardLevel awardLevel;
}
