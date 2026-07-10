package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long activityTypeId;

    @Column(nullable = false, length = 50)
    private String typeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityCategory category;

    @Column(nullable = false)
    private Integer score;

    @Column
    private Integer participationScore;

    @Column
    private Boolean penaltyEnabled;

    @Column
    private Integer absencePenaltyScore;

    @Column
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.score = this.score == null ? 1 : this.score;
        this.participationScore = this.participationScore == null ? this.score : this.participationScore;
        this.penaltyEnabled = this.penaltyEnabled == null ? false : this.penaltyEnabled;
        this.absencePenaltyScore = this.absencePenaltyScore == null ? 0 : this.absencePenaltyScore;
        this.displayOrder = this.displayOrder == null ? 999 : this.displayOrder;
        this.active = this.active == null ? true : this.active;
        this.createdAt = this.createdAt == null ? LocalDateTime.now() : this.createdAt;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.score = this.score == null ? 1 : this.score;
        this.participationScore = this.participationScore == null ? this.score : this.participationScore;
        this.penaltyEnabled = this.penaltyEnabled == null ? false : this.penaltyEnabled;
        this.absencePenaltyScore = this.absencePenaltyScore == null ? 0 : this.absencePenaltyScore;
        this.displayOrder = this.displayOrder == null ? 999 : this.displayOrder;
        this.active = this.active == null ? true : this.active;
        this.updatedAt = LocalDateTime.now();
    }
}
