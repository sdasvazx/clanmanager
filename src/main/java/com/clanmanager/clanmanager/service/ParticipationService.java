package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.ParticipationResponseDto;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParticipationService {

    private final MemberRepository memberRepository;
    private final ActivityAttendanceRepository attendanceRepository;
    private final ActivityTypeRepository activityTypeRepository;

    @Transactional(readOnly = true)
    public ParticipationResponseDto getParticipation(LocalDate startDate, LocalDate endDate) {
        var members = memberRepository.findByActiveTrueOrderByMemberIdAsc();
        var activeActivities = activityTypeRepository.findByActiveTrueOrderByDisplayOrderAscActivityTypeIdAsc();

        Map<Long, Long> totalByActivityId = attendanceRepository.findActivityOccurrenceCountsByPeriod(startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        ActivityAttendanceRepository.ActivityOccurrenceCountProjection::getActivityTypeId,
                        ActivityAttendanceRepository.ActivityOccurrenceCountProjection::getTotalCount
                ));
        int totalActivityCount = activeActivities.stream()
                .mapToInt(activity -> totalByActivityId.getOrDefault(activity.getActivityTypeId(), 0L).intValue())
                .sum();

        Map<Long, Map<Long, Long>> memberActivityCounts = new HashMap<>();
        attendanceRepository.findMemberActivityCountsByPeriod(startDate, endDate).forEach(row ->
                memberActivityCounts
                        .computeIfAbsent(row.getMemberId(), ignored -> new HashMap<>())
                        .put(row.getActivityTypeId(), row.getAttendanceCount())
        );

        List<ParticipationResponseDto.ParticipationMemberDto> rawRows = members.stream()
                .map(member -> toMemberDto(member, activeActivities, totalByActivityId, memberActivityCounts.getOrDefault(member.getMemberId(), Map.of()), totalActivityCount, 0))
                .toList();

        int topFinalScore = rawRows.stream()
                .mapToInt(row -> row.getFinalParticipationScore() == null ? 0 : row.getFinalParticipationScore())
                .max()
                .orElse(0);
        long topAttendanceCount = rawRows.stream()
                .mapToLong(row -> row.getAttendanceCount() == null ? 0 : row.getAttendanceCount())
                .max()
                .orElse(0L);

        var rows = rawRows.stream()
                .map(row -> withContributionRate(row, topFinalScore, topAttendanceCount))
                .sorted(Comparator
                        .comparing(ParticipationResponseDto.ParticipationMemberDto::getFinalParticipationScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ParticipationResponseDto.ParticipationMemberDto::getAttendanceCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(dto -> dto.getCombatPower() == null ? 0 : dto.getCombatPower(), Comparator.reverseOrder())
                        .thenComparing(ParticipationResponseDto.ParticipationMemberDto::getCharacterName, Comparator.nullsLast(String::compareTo)))
                .toList();

        return ParticipationResponseDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .topAttendanceCount(topAttendanceCount)
                .totalActivityCount(totalActivityCount)
                .topFinalScore(topFinalScore)
                .totalMemberCount(rows.size())
                .activityColumns(toActivityColumns(activeActivities, totalByActivityId))
                .rows(rows)
                .build();
    }

    public ParticipationResponseDto.ParticipationMemberDto getMemberParticipation(Long memberId, LocalDate startDate, LocalDate endDate) {
        return getParticipation(startDate, endDate).getRows()
                .stream()
                .filter(row -> row.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }

    private ParticipationResponseDto.ParticipationMemberDto toMemberDto(
            Member member,
            List<ActivityType> activeActivities,
            Map<Long, Long> totalByActivityId,
            Map<Long, Long> counts,
            int totalActivityCount,
            int minorityBonusScore
    ) {
        long attendanceCount = counts.values().stream().mapToLong(Long::longValue).sum();
        int baseScore = 0;
        int penaltyScore = 0;
        Map<Long, Long> orderedCounts = new LinkedHashMap<>();

        for (ActivityType activity : activeActivities) {
            Long activityTypeId = activity.getActivityTypeId();
            long attended = counts.getOrDefault(activityTypeId, 0L);
            long total = totalByActivityId.getOrDefault(activityTypeId, 0L);
            int participationScore = participationScore(activity);
            orderedCounts.put(activityTypeId, attended);
            baseScore += Math.toIntExact(attended * participationScore);
            if (Boolean.TRUE.equals(activity.getPenaltyEnabled())) {
                long missed = Math.max(0L, total - attended);
                penaltyScore += Math.toIntExact(missed * (long) absencePenaltyScore(activity));
            }
        }

        int finalScore = baseScore - penaltyScore + minorityBonusScore;
        double participationRate = totalActivityCount == 0
                ? 0.0
                : Math.round(((double) attendanceCount / totalActivityCount) * 1000) / 10.0;

        return ParticipationResponseDto.ParticipationMemberDto.builder()
                .memberId(member.getMemberId())
                .characterName(member.getCharacterName())
                .guildName(member.getGuildName())
                .characterClass(member.getCharacterClass())
                .level(member.getLevel())
                .combatPower(member.getCombatPower())
                .attendanceCount(attendanceCount)
                .topAttendanceCount(0L)
                .totalActivityCount(totalActivityCount)
                .participationRate(participationRate)
                .baseParticipationScore(baseScore)
                .absencePenaltyScore(penaltyScore)
                .minorityBonusScore(minorityBonusScore)
                .finalParticipationScore(finalScore)
                .contributionRate(0.0)
                .activityCounts(orderedCounts)
                .build();
    }

    private ParticipationResponseDto.ParticipationMemberDto withContributionRate(
            ParticipationResponseDto.ParticipationMemberDto row,
            int topFinalScore,
            long topAttendanceCount
    ) {
        double contributionRate = topFinalScore == 0
                ? 0.0
                : Math.round(((double) Math.max(0, row.getFinalParticipationScore()) / topFinalScore) * 1000) / 10.0;
        return ParticipationResponseDto.ParticipationMemberDto.builder()
                .memberId(row.getMemberId())
                .characterName(row.getCharacterName())
                .guildName(row.getGuildName())
                .characterClass(row.getCharacterClass())
                .level(row.getLevel())
                .combatPower(row.getCombatPower())
                .attendanceCount(row.getAttendanceCount())
                .topAttendanceCount(topAttendanceCount)
                .totalActivityCount(row.getTotalActivityCount())
                .participationRate(row.getParticipationRate())
                .baseParticipationScore(row.getBaseParticipationScore())
                .absencePenaltyScore(row.getAbsencePenaltyScore())
                .minorityBonusScore(row.getMinorityBonusScore())
                .finalParticipationScore(row.getFinalParticipationScore())
                .contributionRate(contributionRate)
                .activityCounts(row.getActivityCounts())
                .build();
    }

    private List<ParticipationResponseDto.ActivityColumnDto> toActivityColumns(List<ActivityType> activities, Map<Long, Long> totals) {
        return activities.stream()
                .map(activity -> ParticipationResponseDto.ActivityColumnDto.builder()
                        .activityTypeId(activity.getActivityTypeId())
                        .activityName(activity.getTypeName())
                        .displayOrder(activity.getDisplayOrder())
                        .participationScore(participationScore(activity))
                        .penaltyEnabled(Boolean.TRUE.equals(activity.getPenaltyEnabled()))
                        .absencePenaltyScore(absencePenaltyScore(activity))
                        .totalCount(totals.getOrDefault(activity.getActivityTypeId(), 0L).intValue())
                        .build())
                .toList();
    }

    private int participationScore(ActivityType activity) {
        if (activity.getParticipationScore() != null) return activity.getParticipationScore();
        return activity.getScore() == null ? 1 : activity.getScore();
    }

    private int absencePenaltyScore(ActivityType activity) {
        return activity.getAbsencePenaltyScore() == null ? 0 : activity.getAbsencePenaltyScore();
    }
}
