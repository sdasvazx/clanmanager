package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "collection_statuses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "collection_item_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long collectionStatusId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_item_id", nullable = false)
    private CollectionItem item;

    @Column(nullable = false, length = 30)
    private String state;

    @Column(length = 255)
    private String memo;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean locked = false;

    private Long updatedByMemberId;

    @Column(length = 50)
    private String updatedByName;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.state = this.state == null ? "미완료" : this.state;
        this.locked = this.locked == null ? false : this.locked;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
