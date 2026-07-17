package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.VaultSummaryResponseDto;
import com.clanmanager.clanmanager.dto.VaultTransactionPageResponseDto;
import com.clanmanager.clanmanager.dto.VaultTransactionRequestDto;
import com.clanmanager.clanmanager.dto.VaultTransactionResponseDto;
import com.clanmanager.clanmanager.entity.ClanVault;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import com.clanmanager.clanmanager.repository.ClanVaultRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.repository.VaultTransactionRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class ClanVaultController {

    private static final Long VAULT_ID = 1L;
    private static final int HISTORY_PAGE_SIZE = 50;
    private static final int MAX_HISTORY_PAGES = 10;

    private final ClanVaultRepository vaultRepository;
    private final VaultTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    public VaultSummaryResponseDto getSummary() {
        ClanVault vault = getOrCreateVault();
        long reservedDiamonds = getReservedDistributionDiamonds();

        return VaultSummaryResponseDto.builder()
                .balanceDiamonds(vault.getBalanceDiamonds())
                .reservedDiamonds(reservedDiamonds)
                .availableDiamonds(getAvailableDiamonds(vault, reservedDiamonds))
                .depositCount(transactionRepository.countByType(VaultTransactionType.DEPOSIT))
                .distributionCount(transactionRepository.countByType(VaultTransactionType.DISTRIBUTION))
                .recentTransactions(findRecentTransactions())
                .build();
    }

    @GetMapping("/transactions")
    public VaultTransactionPageResponseDto getTransactions(@RequestParam(defaultValue = "1") int page) {
        int safePage = normalizeHistoryPage(page);
        Page<VaultTransaction> result = transactionRepository.findAll(PageRequest.of(
                safePage - 1,
                HISTORY_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt")
        ));

        return new VaultTransactionPageResponseDto(
                result.getContent().stream().map(VaultTransactionResponseDto::from).toList(),
                safePage,
                HISTORY_PAGE_SIZE,
                Math.min(Math.max(result.getTotalPages(), 1), MAX_HISTORY_PAGES),
                Math.min(result.getTotalElements(), (long) HISTORY_PAGE_SIZE * MAX_HISTORY_PAGES)
        );
    }

    @GetMapping("/distributions/member/{memberId}")
    public List<VaultTransactionResponseDto> getMemberDistributions(@PathVariable Long memberId) {
        return transactionRepository.findByTypeAndTargetMember_MemberIdOrderByCreatedAtDesc(VaultTransactionType.DISTRIBUTION, memberId).stream()
                .map(VaultTransactionResponseDto::from)
                .toList();
    }

    @PostMapping("/deposit")
    @Transactional
    public VaultTransactionResponseDto deposit(@Valid @RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        long amount = requirePositiveAmount(request.getAmountDiamonds());
        ClanVault vault = getOrCreateVault();
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() + amount);
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.DEPOSIT, amount, vault, null, request);
    }

    @PostMapping("/distribute")
    @Transactional
    public VaultTransactionResponseDto distribute(@Valid @RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        long amount = requirePositiveAmount(request.getAmountDiamonds());
        Member targetMember = findRequiredMember(request.getTargetMemberId(), "분배받을 클랜원을 선택해 주세요.");
        ClanVault vault = getOrCreateVaultWithLock();
        long availableDiamonds = getAvailableDiamonds(vault);
        if (availableDiamonds < amount) {
            throw new IllegalArgumentException("클랜금고 가용 다이아가 부족합니다. 가용 다이아: " + availableDiamonds);
        }
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() - amount);
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.DISTRIBUTION, amount, vault, targetMember, request);
    }

    @PostMapping("/distributions/{transactionId}/claim")
    @Transactional
    public VaultTransactionResponseDto claimDistribution(@PathVariable Long transactionId, @RequestParam Long memberId) {
        Member requester = findRequiredMember(memberId, "수령 확인 회원 정보가 필요합니다.");
        VaultTransaction transaction = transactionRepository.findWithLockByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 분배 기록입니다."));

        if (transaction.getType() != VaultTransactionType.DISTRIBUTION || transaction.getTargetMember() == null) {
            throw new IllegalArgumentException("분배 기록만 수령 처리할 수 있습니다.");
        }
        boolean owner = transaction.getTargetMember().getMemberId().equals(requester.getMemberId());
        if (!owner && requester.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("본인 분배금만 수령 처리할 수 있습니다.");
        }
        if (Boolean.TRUE.equals(transaction.getClaimed())) {
            throw new IllegalArgumentException("이미 수령 완료된 분배금입니다.");
        }

        transaction.setClaimed(true);
        transaction.setClaimedAt(LocalDateTime.now());
        return VaultTransactionResponseDto.from(transactionRepository.save(transaction));
    }

    @PatchMapping("/distributions/{transactionId}/cancel-claim")
    @Transactional
    public VaultTransactionResponseDto cancelClaimDistribution(@PathVariable Long transactionId, @RequestParam Long adminMemberId) {
        requireAdmin(adminMemberId);
        VaultTransaction transaction = transactionRepository.findWithLockByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 분배 기록입니다."));

        if (transaction.getType() != VaultTransactionType.DISTRIBUTION || transaction.getTargetMember() == null) {
            throw new IllegalArgumentException("분배 기록만 수령 취소할 수 있습니다.");
        }

        transaction.setClaimed(false);
        transaction.setClaimedAt(null);
        return VaultTransactionResponseDto.from(transactionRepository.save(transaction));
    }

    @PostMapping("/withdraw")
    @Transactional
    public VaultTransactionResponseDto withdraw(@Valid @RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        long amount = requirePositiveAmount(request.getAmountDiamonds());
        ClanVault vault = getOrCreateVaultWithLock();
        long availableDiamonds = getAvailableDiamonds(vault);
        if (availableDiamonds < amount) {
            throw new IllegalArgumentException("클랜금고 가용 다이아가 부족합니다. 가용 다이아: " + availableDiamonds);
        }
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() - amount);
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.WITHDRAW, amount, vault, null, request);
    }

    @PatchMapping("/balance")
    @Transactional
    public VaultTransactionResponseDto updateBalance(@Valid @RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        if (request.getBalanceDiamonds() == null || request.getBalanceDiamonds() < 0) {
            throw new IllegalArgumentException("금고 금액은 0 이상으로 입력해 주세요.");
        }
        ClanVault vault = getOrCreateVaultWithLock();
        long reservedDiamonds = getReservedDistributionDiamonds();
        if (request.getBalanceDiamonds() < reservedDiamonds) {
            throw new IllegalArgumentException("새 금액은 미수령 분배금(" + reservedDiamonds + ")보다 작을 수 없습니다.");
        }
        long changedAmount = Math.abs(request.getBalanceDiamonds() - vault.getBalanceDiamonds());
        vault.setBalanceDiamonds(request.getBalanceDiamonds());
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.ADJUSTMENT, changedAmount, vault, null, request);
    }

    private List<VaultTransactionResponseDto> findRecentTransactions() {
        return transactionRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(VaultTransactionResponseDto::from)
                .toList();
    }

    private int normalizeHistoryPage(int page) {
        return Math.max(1, Math.min(page, MAX_HISTORY_PAGES));
    }

    private ClanVault getOrCreateVault() {
        return vaultRepository.findById(VAULT_ID)
                .orElseGet(() -> vaultRepository.save(ClanVault.builder()
                        .vaultId(VAULT_ID)
                        .balanceDiamonds(0L)
                        .build()));
    }

    private ClanVault getOrCreateVaultWithLock() {
        return vaultRepository.findWithLockByVaultId(VAULT_ID)
                .orElseGet(() -> vaultRepository.save(ClanVault.builder()
                        .vaultId(VAULT_ID)
                        .balanceDiamonds(0L)
                        .build()));
    }

    private long getReservedDistributionDiamonds() {
        return 0L;
    }

    private long getAvailableDiamonds(ClanVault vault) {
        return getAvailableDiamonds(vault, getReservedDistributionDiamonds());
    }

    private long getAvailableDiamonds(ClanVault vault, long reservedDiamonds) {
        return Math.max(0L, vault.getBalanceDiamonds() - reservedDiamonds);
    }

    private VaultTransactionResponseDto saveTransaction(
            VaultTransactionType type,
            long amount,
            ClanVault vault,
            Member targetMember,
            VaultTransactionRequestDto request
    ) {
        VaultTransaction transaction = transactionRepository.save(VaultTransaction.builder()
                .type(type)
                .amountDiamonds(amount)
                .balanceAfter(vault.getBalanceDiamonds())
                .targetMember(targetMember)
                .createdBy(findOptionalMember(request.getCreatedByMemberId()))
                .memo(request.getMemo())
                .claimed(type != VaultTransactionType.DISTRIBUTION)
                .claimedAt(type == VaultTransactionType.DISTRIBUTION ? null : LocalDateTime.now())
                .build());
        return VaultTransactionResponseDto.from(transaction);
    }

    private long requirePositiveAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("다이아 수량은 1 이상으로 입력해 주세요.");
        }
        return amount;
    }

    private Member findRequiredMember(Long memberId, String message) {
        if (memberId == null) {
            throw new IllegalArgumentException(message);
        }
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
    }

    private void requireAdmin(Long memberId) {
        Member member = findRequiredMember(memberId, "운영자 확인 정보가 필요합니다.");
        if (member.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 사용할 수 있는 기능입니다.");
        }
    }

    private Member findOptionalMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepository.findById(memberId).orElse(null);
    }
}
