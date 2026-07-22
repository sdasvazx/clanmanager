package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VaultTransactionRepository extends JpaRepository<VaultTransaction, Long> {
    @Modifying
    @Query(value = "update vault_transactions set version = 0 where version is null", nativeQuery = true)
    int initializeNullVersions();

    List<VaultTransaction> findTop50ByOrderByCreatedAtDesc();

    List<VaultTransaction> findByTypeAndTargetMember_MemberIdOrderByCreatedAtDesc(VaultTransactionType type, Long memberId);

    long countByType(VaultTransactionType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VaultTransaction> findWithLockByTransactionId(Long transactionId);

    @Query("""
            select coalesce(sum(v.amountDiamonds), 0)
            from VaultTransaction v
            where v.type = com.clanmanager.clanmanager.entity.VaultTransactionType.DISTRIBUTION
            and (v.claimed is null or v.claimed = false)
            and v.targetMember.active = true
            """)
    long sumPendingDistributionAmount();
}
