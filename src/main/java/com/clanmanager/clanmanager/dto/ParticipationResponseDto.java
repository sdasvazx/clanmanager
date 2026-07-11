package com.clanmanager.clanmanager.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ParticipationResponseDto {

    private LocalDate startDate;
    private LocalDate endDate;
    private Long topAttendanceCount;
    private Integer totalActivityCount;
    private Integer topFinalScore;
    private Integer totalMemberCount;
    private List<ActivityColumnDto> activityColumns;
    private List<ParticipationMemberDto> rows;

    @Getter
    @Builder
    public static class ActivityColumnDto {
        private Long activityTypeId;
        private String activityName;
        private Integer displayOrder;
        private Integer participationScore;
        private Boolean penaltyEnabled;
        private Integer absencePenaltyScore;
        private Integer totalCount;
    }

    @Getter
    @Builder
    public static class ParticipationMemberDto {
        private Long memberId;
        private String characterName;
        private String guildName;
        private String characterClass;
        private Integer level;
        private Integer combatPower;
        private Long attendanceCount;
        private Long topAttendanceCount;
        private Integer totalActivityCount;
        private Double participationRate;
        private Integer baseParticipationScore;
        private Integer absencePenaltyScore;
        private Integer minorityBonusScore;
        private Integer finalParticipationScore;
        private Double contributionRate;
        private Map<Long, Long> activityCounts;

        public Long getCount() {
            return attendanceCount;
        }

        public Double getRate() {
            return participationRate;
        }
    }
}
