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
    private String memo;
    private String createdByName;
    private long totalCount;
    private Map<String, Long> clanCounts;

    public static BossParticipationResponseDto from(
            BossParticipationRecord record,
            long totalCount,
            Map<String, Long> clanCounts
    ) {
        return BossParticipationResponseDto.builder()
                .recordId(record.getRecordId())
                .bossDate(record.getBossDate())
                .cutTime(record.getCutTime())
                .submittedAt(record.getSubmittedAt())
                .bossName(record.getBossName())
                .score(record.getScore())
                .memo(record.getMemo())
                .createdByName(record.getCreatedBy() == null ? null : record.getCreatedBy().getCharacterName())
                .totalCount(totalCount)
                .clanCounts(clanCounts)
                .build();
    }
}
