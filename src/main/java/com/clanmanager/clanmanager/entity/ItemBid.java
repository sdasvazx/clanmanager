package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "item_bids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemBid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemBidId;

    @Column(nullable = false, length = 80)
    private String itemName;

    @Column(nullable = false, length = 50)
    private String bidder;

    @Column(nullable = false)
    private Long bidDiamonds;

    @Column(length = 255)
    private String memo;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.bidDiamonds = this.bidDiamonds == null ? 0L : this.bidDiamonds;
        this.createdAt = LocalDateTime.now();
    }
}
