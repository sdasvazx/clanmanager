package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.DistributionClaimRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface DistributionClaimRequestRepository extends JpaRepository<DistributionClaimRequest, Long> {

    List<DistributionClaimRequest> findTop100ByOrderByCreatedAtDesc();

    List<DistributionClaimRequest> findByRequester_MemberIdOrderByCreatedAtDesc(Long memberId);

    boolean existsBySourceTransaction_TransactionIdAndStatus(Long transactionId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DistributionClaimRequest> findWithLockByRequestId(Long requestId);
}
