package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BossParticipationResponseDto {

    private Long recordId;
    private LocalDate bossDate;
    private LocalTime cutTime;
    private LocalDateTime submittedAt;
    private String bossName;
    private Integer score;
    private Long activityTypeId;
    private Boolean penaltyApplied;
    private Boolean attendanceApplied;
    private String memo;
    private String createdByName;
    private long totalCount;
    private Map<String, Long> clanCounts;
    private boolean activityTypeMatched;
    private String activityTypeName;
    private String warningMessage;

    public static BossParticipationResponseDto from(
            BossParticipationRecord record,
            long totalCount,
            Map<String, Long> clanCounts
    ) {
        return from(record, totalCount, clanCounts, true, null);
    }

    public static BossParticipationResponseDto from(
            BossParticipationRecord record,
            long totalCount,
            Map<String, Long> clanCounts,
            boolean activityTypeMatched,
            String activityTypeName
    ) {
        return BossParticipationResponseDto.builder()
                .recordId(record.getRecordId())
                .bossDate(record.getBossDate())
                .cutTime(record.getCutTime())
                .submittedAt(record.getSubmittedAt())
                .bossName(record.getBossName())
                .score(record.getScore())
                .activityTypeId(record.getActivityType() == null ? null : record.getActivityType().getActivityTypeId())
                .penaltyApplied(Boolean.TRUE.equals(record.getPenaltyApplied()))
                .attendanceApplied(!Boolean.FALSE.equals(record.getAttendanceApplied()))
                .memo(record.getMemo())
                .createdByName(record.getCreatedBy() == null ? null : record.getCreatedBy().getCharacterName())
                .totalCount(totalCount)
                .clanCounts(clanCounts)
                .activityTypeMatched(activityTypeMatched)
                .activityTypeName(activityTypeName)
                .warningMessage(activityTypeMatched ? null : "보스명이 활동 종류와 매칭되지 않아 참여율에는 반영되지 않았습니다.")
                .build();
    }
}
