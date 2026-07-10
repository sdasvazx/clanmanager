package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VaultTransactionRepository extends JpaRepository<VaultTransaction, Long> {

    List<VaultTransaction> findTop50ByOrderByCreatedAtDesc();

    List<VaultTransaction> findByTypeAndTargetMember_MemberIdOrderByCreatedAtDesc(VaultTransactionType type, Long memberId);

    long countByType(VaultTransactionType type);

    @Query("""
            select coalesce(sum(v.amountDiamonds), 0)
            from VaultTransaction v
            where v.type = com.clanmanager.clanmanager.entity.VaultTransactionType.DISTRIBUTION
            and (v.claimed is null or v.claimed = false)
            """)
    long sumPendingDistributionAmount();
}
