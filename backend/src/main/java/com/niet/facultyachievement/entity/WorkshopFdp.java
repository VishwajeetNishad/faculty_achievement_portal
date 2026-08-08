package com.niet.facultyachievement.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "workshops_fdps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopFdp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false, unique = true)
    private Achievement achievement;

    @Column(name = "event_name", nullable = false, length = 255)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private EventRole role;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "organizing_body", nullable = false, length = 200)
    private String organizingBody;
}
