package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DistributionRequestDto {

    private String mode;
    private Double participationCut;
    private Double powerScoreCut;
    private Long totalDiamonds;
    private Map<String, Long> clanDiamonds;
    private Long createdByMemberId;
    private String memo;
}
