package com.clanmanager.clanmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributionPenaltyDetailDto {
    private String activityName;
    private LocalDate missedDate;
    private Long amountDiamonds;
}
