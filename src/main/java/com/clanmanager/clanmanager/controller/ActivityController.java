package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.ActivityScheduleResponseDto;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.repository.ActivityScheduleRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityTypeRepository activityTypeRepository;
    private final ActivityScheduleRepository activityScheduleRepository;

    @GetMapping
    public List<ActivityType> getActiveActivityTypes() {
        return activityTypeRepository.findByActiveTrueOrderByActivityTypeIdAsc();
    }

    @GetMapping("/schedules")
    @Transactional(readOnly = true)
    public List<ActivityScheduleResponseDto> getActiveSchedules() {
        return activityScheduleRepository.findByActiveTrueOrderByDayOfWeekAscActivityTimeAsc().stream()
                .map(schedule -> ActivityScheduleResponseDto.builder()
                        .scheduleId(schedule.getScheduleId())
                        .activityTypeId(schedule.getActivityType().getActivityTypeId())
                        .activityName(schedule.getActivityType().getTypeName())
                        .activityDate(schedule.getActivityDate())
                        .dayOfWeek(schedule.getDayOfWeek())
                        .activityTime(schedule.getActivityTime())
                        .recurring(schedule.getRecurring())
                        .build())
                .toList();
    }
}
