package com.clanmanager.clanmanager.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class ActivityScheduleResponseDto {
    private Long scheduleId;
    private Long activityTypeId;
    private String activityName;
    private LocalDate activityDate;
    private DayOfWeek dayOfWeek;
    private LocalTime activityTime;
    private Boolean recurring;
}
