package com.clanmanager.clanmanager.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributionResponseDto {

    private Long snapshotId;
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
    private List<PeriodSummaryDto> selectedPeriods;
    private Integer totalActivityCount;
    private Long allocatedDiamonds;
    private Long remainingDiamonds;
    private Boolean readOnly;
    private LocalDateTime createdAt;
    private String createdByName;
    private List<ClanSummaryDto> clanSummaries;
    private List<ResultItemDto> results;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClanSummaryDto {
        private String clanName;
        private Integer memberCount;
        private Long totalDiamonds;
        private Long participationPool;
        private Long powerPool;
        private Integer participationEligibleCount;
        private Integer powerEligibleCount;
        private Long allocatedDiamonds;
        private Long remainingDiamonds;
        private Double participationDiamondsPerPoint;
        private Double powerDiamondsPerPoint;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItemDto {
        private Long memberId;
        private String characterName;
        private String clanName;
        private String characterClass;
        private Integer level;
        private Integer combatPower;
        private Double currentPowerMan;
        private Double previousPowerMan;
        private Double growthScore;
        private Double currentPowerScore;
        private Double powerScore;
        private Long attendanceCount;
        private Integer totalActivityCount;
        private Double integratedParticipationRate;
        private Double participationRate;
        private Integer finalParticipationScore;
        private Boolean participationEligible;
        private Boolean powerEligible;
        private Long participationAmount;
        private Long powerAmount;
        private Long finalAmount;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodSummaryDto {
        private Long periodId;
        private Integer periodIndex;
        private String periodName;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer totalActivityCount;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotSummaryDto {
        private Long snapshotId;
        private String mode;
        private Double participationCut;
        private Double powerScoreCut;
        private Long totalDiamonds;
        private Long allocatedDiamonds;
        private Long remainingDiamonds;
        private List<Long> periodIds;
        private List<PeriodSummaryDto> selectedPeriods;
        private LocalDateTime createdAt;
        private String createdByName;
    }
}
