package com.clanmanager.clanmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DistributionClaimRequestProcessDto {

    @NotNull
    private Long processorMemberId;

    @NotBlank
    private String status;

    private Long approvedAmount;

    @Size(max = 200)
    private String processedMemo;
}
