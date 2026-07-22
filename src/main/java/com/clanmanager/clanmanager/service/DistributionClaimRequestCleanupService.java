package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.repository.DistributionClaimRequestRepository;
import com.clanmanager.clanmanager.repository.VaultTransactionRepository;
import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DistributionClaimRequestCleanupService {

    private static final String PENDING_STATUS = "\uC811\uC218";
    private static final long RETENTION_HOURS = 24L;

    private final DistributionClaimRequestRepository repository;
    private final VaultTransactionRepository transactionRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcilePreviouslyApprovedRequests() {
        repository.findByStatusAndSourceTransactionIsNotNull("\uC9C0\uAE09\uC644\uB8CC").forEach(request -> {
            if (request.getSourceTransaction().getType() != VaultTransactionType.DISTRIBUTION) {
                return;
            }
            String memo = "\uC218\uB839\uC644\uB8CC \uC815\uC0B0 #" + request.getRequestId();
            if (transactionRepository.existsByTypeAndMemo(VaultTransactionType.WITHDRAW, memo)) {
                return;
            }
            long amount = request.getApprovedAmount() == null ? request.getAmountDiamonds() : request.getApprovedAmount();
            transactionRepository.save(VaultTransaction.builder()
                    .type(VaultTransactionType.WITHDRAW)
                    .amountDiamonds(amount)
                    .balanceAfter(request.getSourceTransaction().getBalanceAfter())
                    .targetMember(request.getRequester())
                    .createdBy(request.getProcessedBy())
                    .memo(memo)
                    .claimed(true)
                    .claimedAt(request.getProcessedAt())
                    .build());
        });
    }

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
