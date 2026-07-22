package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.DistributionClaimRequest;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.VaultTransaction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DistributionClaimRequestResponseDto {

    private Long requestId;
    private Long transactionId;
    private Long requesterMemberId;
    private String requesterName;
    private Long amountDiamonds;
    private Long approvedAmount;
    private String memo;
    private String status;
    private Long processedByMemberId;
    private String processedByName;
    private String processedMemo;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public static DistributionClaimRequestResponseDto from(DistributionClaimRequest request) {
        VaultTransaction transaction = request.getSourceTransaction();
        Member requester = request.getRequester();
        Member processedBy = request.getProcessedBy();

        return DistributionClaimRequestResponseDto.builder()
                .requestId(request.getRequestId())
                .transactionId(transaction == null ? null : transaction.getTransactionId())
                .requesterMemberId(requester == null ? null : requester.getMemberId())
                .requesterName(request.getRequesterName())
                .amountDiamonds(request.getAmountDiamonds())
                .approvedAmount(request.getApprovedAmount())
                .memo(request.getMemo())
                .status(request.getStatus())
                .processedByMemberId(processedBy == null ? null : processedBy.getMemberId())
                .processedByName(request.getProcessedByName())
                .processedMemo(request.getProcessedMemo())
                .createdAt(request.getCreatedAt())
                .processedAt(request.getProcessedAt())
                .build();
    }
}
