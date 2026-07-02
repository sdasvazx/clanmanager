package com.clanmanager.clanmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "clan_vault")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClanVault {

    @Id
    private Long vaultId;

    @Column(nullable = false)
    private Long balanceDiamonds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.vaultId = this.vaultId == null ? 1L : this.vaultId;
        this.balanceDiamonds = this.balanceDiamonds == null ? 0L : this.balanceDiamonds;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
