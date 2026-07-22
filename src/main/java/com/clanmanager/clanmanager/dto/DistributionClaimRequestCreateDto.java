package com.clanmanager.clanmanager.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DistributionClaimRequestCreateDto {

    @NotNull
    private Long requesterMemberId;

    private Long transactionId;

    private Long requestedAmount;

    @Size(max = 200)
    private String memo;
}
