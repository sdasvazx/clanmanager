package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "collection_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long collectionRecordId;

    @Column(nullable = false, length = 50)
    private String characterName;

    @Column(nullable = false, length = 80)
    private String itemName;

    @Column(nullable = false, length = 30)
    private String state;

    @Column(length = 255)
    private String memo;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.state = this.state == null ? "완료" : this.state;
        this.createdAt = LocalDateTime.now();
    }
}
