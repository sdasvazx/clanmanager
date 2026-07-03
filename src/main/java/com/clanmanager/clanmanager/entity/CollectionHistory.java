package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "collection_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long collectionHistoryId;

    private Long memberId;

    @Column(nullable = false, length = 50)
    private String characterName;

    private Long collectionItemId;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 30)
    private String previousState;

    @Column(length = 30)
    private String nextState;

    @Column(length = 255)
    private String memo;

    private Long editedByMemberId;

    @Column(length = 50)
    private String editedByName;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
