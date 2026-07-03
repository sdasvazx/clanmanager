package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "item_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_member_id")
    private Member requester;

    @Column(nullable = false, length = 50)
    private String requesterName;

    @Column(nullable = false, length = 120)
    private String itemName;

    @Column(length = 500)
    private String memo;

    @Column(nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_member_id")
    private Member processedBy;

    @Column(length = 50)
    private String processedByName;

    @Column(length = 500)
    private String processedMemo;

    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        this.status = this.status == null || this.status.isBlank() ? "접수" : this.status;
        this.createdAt = LocalDateTime.now();
    }
}
