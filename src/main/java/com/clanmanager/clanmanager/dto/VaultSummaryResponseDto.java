package com.clanmanager.clanmanager.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class VaultSummaryResponseDto {

    private Long balanceDiamonds;
    private Long reservedDiamonds;
    private Long availableDiamonds;
    private long depositCount;
    private long distributionCount;
    private List<VaultTransactionResponseDto> recentTransactions;
}
