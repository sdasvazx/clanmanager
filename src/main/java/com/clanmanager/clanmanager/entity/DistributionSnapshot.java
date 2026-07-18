package com.clanmanager.clanmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "distribution_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    @Column(nullable = false, length = 20)
    private String mode;

    private Double participationCut;

    private Double powerScoreCut;

    private Long totalDiamonds;

    private Long allocatedDiamonds;

    private Long remainingDiamonds;

    @Column(length = 500)
    private String periodIds;

    @Lob
    @Column(nullable = false)
    private String requestJson;

    @Lob
    @Column(nullable = false)
    private String responseJson;

    private Long createdByMemberId;

    @Column(length = 50)
    private String createdByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
