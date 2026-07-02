package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "participation_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipationPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long periodId;

    @Column(nullable = false, unique = true)
    private Integer periodIndex;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 100)
    private String periodName;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
