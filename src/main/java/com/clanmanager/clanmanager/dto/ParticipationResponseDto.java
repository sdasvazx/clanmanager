package com.clanmanager.clanmanager.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ParticipationResponseDto {

    private LocalDate startDate;
    private LocalDate endDate;
    private Long topAttendanceCount;
    private Integer totalMemberCount;
    private List<ParticipationMemberDto> rows;

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
        private Double participationRate;

        public Long getCount() {
            return attendanceCount;
        }

        public Double getRate() {
            return participationRate;
        }
    }
}
