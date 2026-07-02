package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ActivitySchedule;
import com.clanmanager.clanmanager.entity.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface ActivityScheduleRepository extends JpaRepository<ActivitySchedule, Long> {
    boolean existsByActivityTypeAndDayOfWeekAndActivityTime(ActivityType activityType, DayOfWeek dayOfWeek, LocalTime activityTime);

    List<ActivitySchedule> findByActivityType(ActivityType activityType);

    List<ActivitySchedule> findByDayOfWeekIsNullAndActivityDateIsNull();

    List<ActivitySchedule> findByActiveTrueOrderByDayOfWeekAscActivityTimeAsc();
}
