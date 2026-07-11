package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.ActivitySettingDto;
import com.clanmanager.clanmanager.entity.ActivityCategory;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActivitySettingService {

    private final ActivityTypeRepository activityTypeRepository;
    private final MemberRepository memberRepository;

    public static final List<DefaultActivity> DEFAULT_ACTIVITIES = List.of(
            new DefaultActivity("소수쟁", 1, false, 0),
            new DefaultActivity("13시 (2성)", 1, true, 2),
            new DefaultActivity("17시 (1성)", 1, false, 0),
            new DefaultActivity("21시 (2성)", 2, true, 3),
            new DefaultActivity("결승전", 0, true, 10),
            new DefaultActivity("마슈미드", 1, true, 10),
            new DefaultActivity("에노크", 1, true, 10),
            new DefaultActivity("전초전", 0, true, 10),
            new DefaultActivity("클랜임무", 0, true, 10),
            new DefaultActivity("클랜수호", 0, true, 10)
    );

    @Transactional
    public void ensureDefaultSettings() {
        int order = 1;
        for (DefaultActivity defaultActivity : DEFAULT_ACTIVITIES) {
            ActivityType activityType = findOrCreateDefaultActivity(defaultActivity.name());
            activityType.setTypeName(defaultActivity.name());
            activityType.setCategory(resolveCategory(defaultActivity.name()));
            activityType.setScore(defaultActivity.participationScore());
            activityType.setParticipationScore(defaultActivity.participationScore());
            activityType.setPenaltyEnabled(defaultActivity.penaltyEnabled());
            activityType.setAbsencePenaltyScore(defaultActivity.absencePenaltyScore());
            activityType.setDisplayOrder(order++);
            activityType.setActive(true);
        }

        Set<String> defaultNames = DEFAULT_ACTIVITIES.stream()
                .map(DefaultActivity::name)
                .collect(java.util.stream.Collectors.toSet());
        activityTypeRepository.findAll().forEach(activityType -> {
            if (!defaultNames.contains(activityType.getTypeName())) {
                activityType.setActive(false);
                if (activityType.getDisplayOrder() == null) {
                    activityType.setDisplayOrder(999);
                }
                if (activityType.getParticipationScore() == null) {
                    activityType.setParticipationScore(activityType.getScore() == null ? 1 : activityType.getScore());
                }
                if (activityType.getPenaltyEnabled() == null) {
                    activityType.setPenaltyEnabled(false);
                }
                if (activityType.getAbsencePenaltyScore() == null) {
                    activityType.setAbsencePenaltyScore(0);
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ActivitySettingDto.Response> getSettings() {
        return activityTypeRepository.findByActiveTrueOrderByDisplayOrderAscActivityTypeIdAsc().stream()
                .map(ActivitySettingDto.Response::from)
                .toList();
    }

    @Transactional
    public List<ActivitySettingDto.Response> saveSettings(ActivitySettingDto.SaveRequest request) {
        validateAdmin(request == null ? null : request.getAdminMemberId());
        List<ActivitySettingDto.Row> rows = request == null ? List.of() : request.getActivities();
        List<ActivitySettingDto.Row> activeRows = rows == null ? List.of() : rows.stream()
                .filter(row -> row.getActive() == null || row.getActive())
                .toList();
        if (activeRows.isEmpty()) {
            throw new IllegalArgumentException("저장할 활동 설정이 없습니다.");
        }
        validateRows(activeRows);

        List<Long> savedIds = new ArrayList<>();
        int order = 1;
        for (ActivitySettingDto.Row row : activeRows) {
            ActivityType activityType = row.getActivityTypeId() == null
                    ? new ActivityType()
                    : activityTypeRepository.findById(row.getActivityTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("활동 설정을 찾을 수 없습니다."));
            String activityName = clean(row.getActivityName());
            int participationScore = number(row.getParticipationScore(), 0);
            int absencePenaltyScore = number(row.getAbsencePenaltyScore(), 0);
            activityType.setTypeName(activityName);
            activityType.setCategory(resolveCategory(activityName));
            activityType.setScore(participationScore);
            activityType.setParticipationScore(participationScore);
            activityType.setPenaltyEnabled(Boolean.TRUE.equals(row.getPenaltyEnabled()));
            activityType.setAbsencePenaltyScore(absencePenaltyScore);
            activityType.setDisplayOrder(row.getDisplayOrder() == null ? order : row.getDisplayOrder());
            activityType.setActive(true);
            savedIds.add(activityTypeRepository.save(activityType).getActivityTypeId());
            order++;
        }

        activityTypeRepository.findAll().forEach(activityType -> {
            if (!savedIds.contains(activityType.getActivityTypeId()) && Boolean.TRUE.equals(activityType.getActive())) {
                activityType.setActive(false);
            }
        });

        return getSettings();
    }

    private ActivityType findOrCreateDefaultActivity(String name) {
        return activityTypeRepository.findByTypeName(name)
                .or(() -> findLegacyActivity(name))
                .orElseGet(() -> activityTypeRepository.save(ActivityType.builder()
                        .typeName(name)
                        .category(resolveCategory(name))
                        .score(1)
                        .participationScore(1)
                        .penaltyEnabled(false)
                        .absencePenaltyScore(0)
                        .displayOrder(999)
                        .active(true)
                        .build()));
    }

    private java.util.Optional<ActivityType> findLegacyActivity(String name) {
        return switch (name) {
            case "13시 (2성)" -> activityTypeRepository.findByTypeName("13시 보스")
                    .or(() -> activityTypeRepository.findByTypeName("매일 13시 보스"));
            case "17시 (1성)" -> activityTypeRepository.findByTypeName("17시 보스")
                    .or(() -> activityTypeRepository.findByTypeName("매일 17시 보스"));
            case "21시 (2성)" -> activityTypeRepository.findByTypeName("21시 보스")
                    .or(() -> activityTypeRepository.findByTypeName("매일 21시 보스"));
            case "클랜수호" -> activityTypeRepository.findByTypeName("수호");
            case "전초전" -> activityTypeRepository.findByTypeName("쟁탈전");
            default -> java.util.Optional.empty();
        };
    }

    private void validateAdmin(Long adminMemberId) {
        Member admin = memberRepository.findById(adminMemberId == null ? -1L : adminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("운영자 정보를 찾을 수 없습니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 출석보스 설정을 저장할 수 있습니다.");
        }
    }

    private void validateRows(List<ActivitySettingDto.Row> rows) {
        Set<String> names = new HashSet<>();
        for (ActivitySettingDto.Row row : rows) {
            String name = clean(row.getActivityName());
            if (name.isBlank()) {
                throw new IllegalArgumentException("빈 활동명은 저장할 수 없습니다.");
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (!names.add(key)) {
                throw new IllegalArgumentException("중복 활동명은 저장할 수 없습니다: " + name);
            }
            if (number(row.getParticipationScore(), 0) < 0 || number(row.getAbsencePenaltyScore(), 0) < 0) {
                throw new IllegalArgumentException("점수는 음수로 저장할 수 없습니다.");
            }
        }
    }

    private ActivityCategory resolveCategory(String name) {
        String compact = clean(name).replaceAll("\\s+", "");
        if (compact.contains("클랜임무")) return ActivityCategory.CLAN_MISSION;
        if (compact.contains("수호")) return ActivityCategory.GUARDIAN;
        if (compact.contains("전초") || compact.contains("쟁탈")) return ActivityCategory.CONQUEST;
        return ActivityCategory.BOSS;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int number(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    public record DefaultActivity(String name, int participationScore, boolean penaltyEnabled, int absencePenaltyScore) {
    }
}
