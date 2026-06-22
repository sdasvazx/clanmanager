package com.clanmanager.clanmanager.config;

import com.clanmanager.clanmanager.entity.ActivityCategory;
import com.clanmanager.clanmanager.entity.ActivitySchedule;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.repository.ActivityScheduleRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InitialDataInitializer implements ApplicationRunner {

    private final ActivityTypeRepository activityTypeRepository;
    private final ActivityScheduleRepository activityScheduleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ActivityType boss13 = createType("13\uC2DC \uBCF4\uC2A4", ActivityCategory.BOSS);
        ActivityType boss17 = createType("17\uC2DC \uBCF4\uC2A4", ActivityCategory.BOSS);
        ActivityType boss21 = createType("21\uC2DC \uBCF4\uC2A4", ActivityCategory.BOSS);
        ActivityType enoch = createType("\uC5D0\uB178\uD06C", ActivityCategory.BOSS);
        ActivityType mashmid = createType("\uB9C8\uC288\uBBF8\uB4DC", ActivityCategory.BOSS);
        ActivityType elite = createType("\uC815\uC608\uB358\uC804\uBCF4\uC2A4", ActivityCategory.ELITE_DUNGEON_BOSS);
        createType("\uD074\uB79C\uC784\uBB34", ActivityCategory.CLAN_MISSION);
        createType("\uC218\uD638", ActivityCategory.GUARDIAN);
        createType("\uC7C1\uD0C8\uC804", ActivityCategory.CONQUEST);

        createDailySchedule(boss13, LocalTime.of(13, 0));
        createDailySchedule(boss17, LocalTime.of(17, 0));
        createDailySchedule(boss21, LocalTime.of(21, 0));
        createSchedule(enoch, DayOfWeek.SATURDAY, LocalTime.of(22, 0));
        createSchedule(mashmid, DayOfWeek.SATURDAY, LocalTime.of(22, 0));
        createSchedule(elite, DayOfWeek.WEDNESDAY, LocalTime.of(21, 30));
        createSchedule(elite, DayOfWeek.FRIDAY, LocalTime.of(21, 30));

        deactivateObsoleteTimedTypes();
        deactivateInvalidSchedules();
    }

    private ActivityType createType(String typeName, ActivityCategory category) {
        return activityTypeRepository.findByTypeName(typeName)
                .orElseGet(() -> activityTypeRepository.save(ActivityType.builder()
                        .typeName(typeName).category(category).score(1).active(true).build()));
    }

    private void createDailySchedule(ActivityType type, LocalTime time) {
        for (DayOfWeek day : DayOfWeek.values()) {
            createSchedule(type, day, time);
        }
    }

    private void createSchedule(ActivityType type, DayOfWeek day, LocalTime time) {
        if (!activityScheduleRepository.existsByActivityTypeAndDayOfWeekAndActivityTime(type, day, time)) {
            activityScheduleRepository.save(ActivitySchedule.builder()
                    .activityType(type).dayOfWeek(day).activityTime(time)
                    .recurring(true).active(true).build());
        }
    }

    private void deactivateObsoleteTimedTypes() {
        List<String> obsoleteNames = List.of(
                "\uB9E4\uC77C 13\uC2DC \uBCF4\uC2A4", "\uB9E4\uC77C 17\uC2DC \uBCF4\uC2A4", "\uB9E4\uC77C 21\uC2DC \uBCF4\uC2A4",
                "\uD1A0\uC694\uC77C 22\uC2DC \uC5D0\uB178\uD06C", "\uD1A0\uC694\uC77C 22\uC2DC \uB9C8\uC288\uBBF8\uB4DC",
                "\uC218\uC694\uC77C/\uAE08\uC694\uC77C 21\uC2DC 30\uBD84 \uC815\uC608\uB358\uC804\uBCF4\uC2A4"
        );
        obsoleteNames.stream().map(activityTypeRepository::findByTypeName).flatMap(java.util.Optional::stream)
                .forEach(type -> {
                    type.setActive(false);
                    activityScheduleRepository.findByActivityType(type).forEach(schedule -> schedule.setActive(false));
                });
    }

    private void deactivateInvalidSchedules() {
        activityScheduleRepository.findByDayOfWeekIsNullAndActivityDateIsNull()
                .forEach(schedule -> schedule.setActive(false));
    }
}
