package com.clanmanager.clanmanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    private Long activityTypeId;
    private LocalDate bossDate;
    private LocalTime cutTime;

    @NotBlank(message = "보스명을 입력해 주세요.")
    @Size(max = 100, message = "보스명은 100자 이하로 입력해 주세요.")
    private String bossName;

    @Min(value = 0, message = "점수는 0 이상으로 입력해 주세요.")
    private Integer score;

    private Boolean penaltyApplied;
    private Boolean attendanceApplied;

    @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
    private String memo;

    private List<@Valid MemberEntry> members = new ArrayList<>();

    @Getter
    @Setter
    public static class MemberEntry {
        @NotBlank(message = "참여 캐릭터 이름을 입력해 주세요.")
        @Size(max = 50, message = "캐릭터 이름은 50자 이하로 입력해 주세요.")
        private String characterName;

        @Size(max = 50, message = "클랜명은 50자 이하로 입력해 주세요.")
        private String clanName;
    }
}
