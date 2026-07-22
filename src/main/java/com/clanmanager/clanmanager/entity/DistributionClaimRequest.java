package com.clanmanager.clanmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "distribution_claim_requests",
        indexes = {
                @Index(name = "idx_distribution_claim_status", columnList = "status"),
                @Index(name = "idx_distribution_claim_requester", columnList = "requester_member_id"),
                @Index(name = "idx_distribution_claim_source", columnList = "source_transaction_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributionClaimRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transaction_id", nullable = false)
    private VaultTransaction sourceTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_member_id", nullable = false)
    private Member requester;

    @Column(nullable = false, length = 50)
    private String requesterName;

    @Column(nullable = false)
    private Long amountDiamonds;

    @Column(length = 200)
    private String memo;

    @Column(nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_member_id")
    private Member processedBy;

    @Column(length = 50)
    private String processedByName;

    @Column(length = 200)
    private String processedMemo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null || this.status.isBlank()) {
            this.status = "\uC811\uC218";
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if ((this.requesterName == null || this.requesterName.isBlank()) && this.requester != null) {
            this.requesterName = this.requester.getCharacterName();
        }
        if (this.amountDiamonds == null && this.sourceTransaction != null) {
            this.amountDiamonds = this.sourceTransaction.getAmountDiamonds();
        }
    }
}
