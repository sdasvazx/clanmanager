package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DistributionRequestDto {

    private String mode;
    private Double participationCut;
    private Double powerScoreCut;
    private Long totalDiamonds;
    private Long totalParticipationDiamonds;
    private Long totalPowerDiamonds;
    private Map<String, Long> clanDiamonds;
    private Map<String, Long> participationDiamonds;
    private Map<String, Long> powerDiamonds;
    private List<Long> periodIds;
    private Long periodId;
    private Integer periodIndex;
    private Long createdByMemberId;
    private String memo;
    private List<Long> excludedMemberIds;
}
