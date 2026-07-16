package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "all_item_stocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllItemStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long allItemStockId;

    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default ''")
    private String clanName;

    @Column(nullable = false, length = 20)
    private String tierName;

    @Column(nullable = false, length = 30)
    private String categoryName;

    @Column(nullable = false, length = 50)
    private String itemName;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    private Integer paidQuantity;

    @Column(nullable = false)
    private Integer displayOrder;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.clanName = this.clanName == null ? "" : this.clanName;
        this.stockQuantity = this.stockQuantity == null ? 0 : this.stockQuantity;
        this.paidQuantity = this.paidQuantity == null ? 0 : this.paidQuantity;
        this.displayOrder = this.displayOrder == null ? 0 : this.displayOrder;
        this.updatedAt = LocalDateTime.now();
    }
}
