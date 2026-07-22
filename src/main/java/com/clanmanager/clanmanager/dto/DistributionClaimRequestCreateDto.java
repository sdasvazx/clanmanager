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

    @NotNull
    private Long transactionId;

    @Size(max = 200)
    private String memo;
}
