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
    interface MemberBalanceProjection {
        Long getMemberId();

        Long getTotalCredited();

        Long getTotalWithdrawn();
    }

    interface MemberDistributionProjection {
        Long getMemberId();
        Long getTotalAmount();
        Long getPendingAmount();
        Long getClaimedAmount();
    }

    @Modifying
    @Query(value = "update vault_transactions set version = 0 where version is null", nativeQuery = true)
    int initializeNullVersions();

    List<VaultTransaction> findTop50ByOrderByCreatedAtDesc();

    List<VaultTransaction> findByTypeAndTargetMember_MemberIdOrderByCreatedAtDesc(VaultTransactionType type, Long memberId);

    @Query("""
            select v from VaultTransaction v
            where v.targetMember.memberId = :memberId
              and (v.type = com.clanmanager.clanmanager.entity.VaultTransactionType.WITHDRAW
                   or (v.type = com.clanmanager.clanmanager.entity.VaultTransactionType.DISTRIBUTION
                       and (v.claimed is null or v.claimed = false)))
            order by v.createdAt desc
            """)
    List<VaultTransaction> findMemberClaimHistory(Long memberId);

    List<VaultTransaction> findByTargetMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("""
            select
                v.targetMember.memberId as memberId,
                sum(case when v.type in (com.clanmanager.clanmanager.entity.VaultTransactionType.DEPOSIT,
                                          com.clanmanager.clanmanager.entity.VaultTransactionType.DISTRIBUTION)
                         then v.amountDiamonds else 0 end) as totalCredited,
                sum(case when v.type = com.clanmanager.clanmanager.entity.VaultTransactionType.WITHDRAW
                         then v.amountDiamonds else 0 end) as totalWithdrawn
            from VaultTransaction v
            where v.targetMember is not null
            group by v.targetMember.memberId
            """)
    List<MemberBalanceProjection> aggregateByMember();

    @Query("""
            select v.targetMember.memberId as memberId,
                   sum(v.amountDiamonds) as totalAmount,
                   sum(case when v.claimed = false or v.claimed is null then v.amountDiamonds else 0 end) as pendingAmount,
                   sum(case when v.claimed = true then v.amountDiamonds else 0 end) as claimedAmount
            from VaultTransaction v
            where v.type = com.clanmanager.clanmanager.entity.VaultTransactionType.DISTRIBUTION
              and v.targetMember is not null
            group by v.targetMember.memberId
            """)
    List<MemberDistributionProjection> aggregateDistributionsByMember();

    long countByType(VaultTransactionType type);

    boolean existsByTypeAndMemo(VaultTransactionType type, String memo);

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
