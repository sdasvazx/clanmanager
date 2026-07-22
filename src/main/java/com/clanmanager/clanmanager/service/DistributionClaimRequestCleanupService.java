package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.repository.DistributionClaimRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DistributionClaimRequestCleanupService {

    private static final String PENDING_STATUS = "\uC811\uC218";
    private static final long RETENTION_HOURS = 24L;

    private final DistributionClaimRequestRepository repository;

    @Scheduled(
            fixedDelayString = "${clanmanager.claim-request-cleanup-interval-ms:3600000}",
            initialDelayString = "${clanmanager.claim-request-cleanup-initial-delay-ms:60000}"
    )
    @Transactional
    public long deleteExpiredProcessedRequests() {
        return repository.deleteByStatusNotAndProcessedAtBefore(
                PENDING_STATUS,
                LocalDateTime.now().minusHours(RETENTION_HOURS)
        );
    }
}
