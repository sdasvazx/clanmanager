package com.clanmanager.clanmanager.config;

import com.clanmanager.clanmanager.entity.ActivityCategory;
import com.clanmanager.clanmanager.entity.ActivitySchedule;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.ClanVault;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.ActivityScheduleRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import com.clanmanager.clanmanager.repository.ClanVaultRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.service.ActivitySettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InitialDataInitializer implements ApplicationRunner {

    private final ActivityScheduleRepository activityScheduleRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final ClanVaultRepository clanVaultRepository;
    private final MemberRepository memberRepository;
    private final ActivitySettingService activitySettingService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        activitySettingService.ensureDefaultSettings();

        ActivityType boss13 = findType("13시 (2성)");
        ActivityType boss17 = findType("17시 (1성)");
        ActivityType boss21 = findType("21시 (2성)");
        ActivityType enoch = findType("에노크");
        ActivityType mashmid = findType("마슈미드");

        createDailySchedule(boss13, LocalTime.of(13, 0));
        createDailySchedule(boss17, LocalTime.of(17, 0));
        createDailySchedule(boss21, LocalTime.of(21, 0));
        createSchedule(enoch, DayOfWeek.SATURDAY, LocalTime.of(22, 0));
        createSchedule(mashmid, DayOfWeek.SATURDAY, LocalTime.of(22, 0));

        deactivateObsoleteTimedTypes();
        deactivateInvalidSchedules();
        normalizeVaultVersionColumns();
        createClanVault();
        promoteFirstMemberToAdminIfNeeded();
    }

    private ActivityType findType(String typeName) {
        return activityTypeRepository.findByTypeName(typeName)
                .orElseThrow(() -> new IllegalStateException("기본 활동을 찾을 수 없습니다: " + typeName));
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

    private void normalizeVaultVersionColumns() {
        jdbcTemplate.update("UPDATE clan_vault SET version = 0 WHERE version IS NULL");
        jdbcTemplate.update("UPDATE vault_transactions SET version = 0 WHERE version IS NULL");
    }

    private void createClanVault() {
        if (!clanVaultRepository.existsById(1L)) {
            clanVaultRepository.save(ClanVault.builder()
                    .vaultId(1L)
                    .balanceDiamonds(0L)
                    .build());
        }
    }

    private void promoteFirstMemberToAdminIfNeeded() {
        if (!memberRepository.existsByRole(MemberRole.ADMIN)) {
            memberRepository.findFirstByActiveTrueOrderByMemberIdAsc().ifPresent(member -> {
                member.setRole(MemberRole.ADMIN);
                memberRepository.save(member);
            });
        }
    }
}
