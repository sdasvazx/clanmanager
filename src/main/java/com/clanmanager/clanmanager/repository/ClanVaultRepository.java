package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ClanVault;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ClanVaultRepository extends JpaRepository<ClanVault, Long> {
    @Modifying
    @Query(value = "update clan_vault set version = 0 where version is null", nativeQuery = true)
    int initializeNullVersions();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClanVault> findWithLockByVaultId(Long vaultId);
}
