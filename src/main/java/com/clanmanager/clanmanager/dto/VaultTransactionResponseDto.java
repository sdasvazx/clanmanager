package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VaultTransactionResponseDto {

    private Long transactionId;
    private VaultTransactionType type;
    private Long amountDiamonds;
    private Long balanceAfter;
    private Long targetMemberId;
    private String targetMemberName;
    private Long createdByMemberId;
    private String createdByMemberName;
    private String memo;
    private Boolean claimed;
    private LocalDateTime claimedAt;
    private LocalDateTime createdAt;

    public static VaultTransactionResponseDto from(VaultTransaction transaction) {
        Member targetMember = transaction.getTargetMember();
        Member createdBy = transaction.getCreatedBy();

        return VaultTransactionResponseDto.builder()
                .transactionId(transaction.getTransactionId())
                .type(transaction.getType())
                .amountDiamonds(transaction.getAmountDiamonds())
                .balanceAfter(transaction.getBalanceAfter())
                .targetMemberId(targetMember == null ? null : targetMember.getMemberId())
                .targetMemberName(targetMember == null ? null : targetMember.getCharacterName())
                .createdByMemberId(createdBy == null ? null : createdBy.getMemberId())
                .createdByMemberName(createdBy == null ? null : createdBy.getCharacterName())
                .memo(transaction.getMemo())
                .claimed(transaction.getClaimed() == null || transaction.getClaimed())
                .claimedAt(transaction.getClaimedAt())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
