package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "boss_participation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BossParticipationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @Column(nullable = false)
    private LocalDate bossDate;

    @Column(nullable = false)
    private LocalTime cutTime;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false, length = 80)
    private String bossName;

    @Column(nullable = false)
    private Integer score;

    @Column(length = 255)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id")
    private Member createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.score = this.score == null ? 1 : this.score;
        this.submittedAt = this.submittedAt == null ? LocalDateTime.now() : this.submittedAt;
        this.createdAt = LocalDateTime.now();
    }
}
