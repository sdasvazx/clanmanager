package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.DistributionClaimRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;

public interface DistributionClaimRequestRepository extends JpaRepository<DistributionClaimRequest, Long> {

    @Query("""
            select request from DistributionClaimRequest request
            where request.status = :pendingStatus
               or request.processedAt is null
               or request.processedAt >= :cutoff
            order by request.createdAt desc
            """)
    List<DistributionClaimRequest> findVisibleForAdmin(
            @Param("pendingStatus") String pendingStatus,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("""
            select request from DistributionClaimRequest request
            where request.requester.memberId = :memberId
              and (request.status = :pendingStatus
                   or request.processedAt is null
                   or request.processedAt >= :cutoff)
            order by request.createdAt desc
            """)
    List<DistributionClaimRequest> findVisibleForMember(
            @Param("memberId") Long memberId,
            @Param("pendingStatus") String pendingStatus,
            @Param("cutoff") LocalDateTime cutoff
    );

    List<DistributionClaimRequest> findByStatusAndSourceTransactionIsNotNull(String status);

    boolean existsBySourceTransaction_TransactionIdAndStatus(Long transactionId, String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DistributionClaimRequest request where request.status <> :status and request.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("status") String status, @Param("cutoff") LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DistributionClaimRequest> findWithLockByRequestId(Long requestId);
}
