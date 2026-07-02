package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.VaultSummaryResponseDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class ClanVaultController {

    private static final Long VAULT_ID = 1L;

    private final ClanVaultRepository vaultRepository;
    private final VaultTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    public VaultSummaryResponseDto getSummary() {
        ClanVault vault = getOrCreateVault();

        return VaultSummaryResponseDto.builder()
                .balanceDiamonds(vault.getBalanceDiamonds())
                .depositCount(transactionRepository.countByType(VaultTransactionType.DEPOSIT))
                .distributionCount(transactionRepository.countByType(VaultTransactionType.DISTRIBUTION))
                .recentTransactions(findRecentTransactions())
                .build();
    }

    @GetMapping("/transactions")
    public List<VaultTransactionResponseDto> getTransactions() {
        return findRecentTransactions();
    }

    @PostMapping("/deposit")
    @Transactional
    public VaultTransactionResponseDto deposit(@RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        long amount = requirePositiveAmount(request.getAmountDiamonds());
        ClanVault vault = getOrCreateVault();
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() + amount);
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.DEPOSIT, amount, vault, null, request);
    }

    @PostMapping("/distribute")
    @Transactional
    public VaultTransactionResponseDto distribute(@RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        long amount = requirePositiveAmount(request.getAmountDiamonds());
        Member targetMember = findRequiredMember(request.getTargetMemberId(), "분배 받을 클랜원을 선택해 주세요.");
        ClanVault vault = getOrCreateVault();
        if (vault.getBalanceDiamonds() < amount) {
            throw new IllegalArgumentException("클랜 금고 잔액이 부족합니다.");
        }
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() - amount);
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.DISTRIBUTION, amount, vault, targetMember, request);
    }

    @PostMapping("/withdraw")
    @Transactional
    public VaultTransactionResponseDto withdraw(@RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        long amount = requirePositiveAmount(request.getAmountDiamonds());
        ClanVault vault = getOrCreateVault();
        if (vault.getBalanceDiamonds() < amount) {
            throw new IllegalArgumentException("클랜 금고 잔액이 부족합니다.");
        }
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() - amount);
        vaultRepository.save(vault);
        return saveTransaction(VaultTransactionType.WITHDRAW, amount, vault, null, request);
    }

    @PatchMapping("/balance")
    @Transactional
    public VaultTransactionResponseDto updateBalance(@RequestBody VaultTransactionRequestDto request) {
        requireAdmin(request.getCreatedByMemberId());
        if (request.getBalanceDiamonds() == null || request.getBalanceDiamonds() < 0) {
            throw new IllegalArgumentException("금고 잔액은 0 이상으로 입력해 주세요.");
        }
        ClanVault vault = getOrCreateVault();
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

    private ClanVault getOrCreateVault() {
        return vaultRepository.findById(VAULT_ID)
                .orElseGet(() -> vaultRepository.save(ClanVault.builder()
                        .vaultId(VAULT_ID)
                        .balanceDiamonds(0L)
                        .build()));
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
