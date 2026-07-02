package com.clanmanager.clanmanager.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BossParticipationRequestDto {

    private Long createdByMemberId;
    private LocalDate bossDate;
    private LocalTime cutTime;
    private String bossName;
    private Integer score;
    private String memo;
    private List<MemberEntry> members = new ArrayList<>();

    @Getter
    @Setter
    public static class MemberEntry {
        private String characterName;
        private String clanName;
    }
}
