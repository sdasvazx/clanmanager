package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ClanVault;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ClanVaultRepository extends JpaRepository<ClanVault, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClanVault> findWithLockByVaultId(Long vaultId);
}
