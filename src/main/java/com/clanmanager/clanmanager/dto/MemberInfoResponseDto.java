package com.clanmanager.clanmanager.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberInfoResponseDto {

    private Long memberId;
    private String characterName;
    private Integer combatPower;
    private String guildName;
    private String characterClass;
    private Integer level;

    private Long myAttendanceCount;
    private Long topAttendanceCount;

    private Double participationRate;
}
