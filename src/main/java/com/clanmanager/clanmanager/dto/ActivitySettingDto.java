package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.ActivityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class ActivitySettingDto {

    @Getter
    @Setter
    public static class SaveRequest {
        private List<@Valid Row> activities;
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class Row {
        private Long activityTypeId;

        @NotBlank(message = "활동명을 입력해 주세요.")
        @Size(max = 50, message = "활동명은 50자 이하로 입력해 주세요.")
        private String activityName;

        @Min(value = 0, message = "참여점수는 0 이상으로 입력해 주세요.")
        private Integer participationScore;

        private Boolean penaltyEnabled;

        @Min(value = 0, message = "미참여 페널티 점수는 0 이상으로 입력해 주세요.")
        private Integer absencePenaltyScore;

        @Min(value = 1, message = "표시 순서는 1 이상으로 입력해 주세요.")
        private Integer displayOrder;

        private Boolean active;
    }

    @Getter
    @Builder
    public static class Response {
        private Long activityTypeId;
        private String activityName;
        private Integer participationScore;
        private Boolean penaltyEnabled;
        private Integer absencePenaltyScore;
        private Integer displayOrder;
        private Boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(ActivityType activityType) {
            Integer participationScore = activityType.getParticipationScore() == null
                    ? activityType.getScore()
                    : activityType.getParticipationScore();
            return Response.builder()
                    .activityTypeId(activityType.getActivityTypeId())
                    .activityName(activityType.getTypeName())
                    .participationScore(participationScore == null ? 1 : participationScore)
                    .penaltyEnabled(Boolean.TRUE.equals(activityType.getPenaltyEnabled()))
                    .absencePenaltyScore(activityType.getAbsencePenaltyScore() == null ? 0 : activityType.getAbsencePenaltyScore())
                    .displayOrder(activityType.getDisplayOrder() == null ? 999 : activityType.getDisplayOrder())
                    .active(Boolean.TRUE.equals(activityType.getActive()))
                    .createdAt(activityType.getCreatedAt())
                    .updatedAt(activityType.getUpdatedAt())
                    .build();
        }
    }
}
