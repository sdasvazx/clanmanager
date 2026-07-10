package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.ParticipationResponseDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParticipationService {

    private final MemberRepository memberRepository;
    private final ActivityAttendanceRepository attendanceRepository;

    public ParticipationResponseDto getParticipation(LocalDate startDate, LocalDate endDate) {
        var members = memberRepository.findByActiveTrueOrderByMemberIdAsc();
        Map<Long, Long> countsByMemberId = attendanceRepository.findAttendanceCountsByPeriod(startDate, endDate)
                .stream()
                .collect(Collectors.toMap(
                        ActivityAttendanceRepository.MemberAttendanceCountProjection::getMemberId,
                        ActivityAttendanceRepository.MemberAttendanceCountProjection::getAttendanceCount
                ));

        long topAttendanceCount = countsByMemberId.values()
                .stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        var rows = members.stream()
                .map(member -> toMemberDto(member, countsByMemberId.getOrDefault(member.getMemberId(), 0L), topAttendanceCount))
                .sorted(Comparator
                        .comparing(ParticipationResponseDto.ParticipationMemberDto::getAttendanceCount, Comparator.reverseOrder())
                        .thenComparing(dto -> dto.getCombatPower() == null ? 0 : dto.getCombatPower(), Comparator.reverseOrder())
                        .thenComparing(ParticipationResponseDto.ParticipationMemberDto::getCharacterName, Comparator.nullsLast(String::compareTo)))
                .toList();

        return ParticipationResponseDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .topAttendanceCount(topAttendanceCount)
                .totalMemberCount(rows.size())
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

    private ParticipationResponseDto.ParticipationMemberDto toMemberDto(Member member, long attendanceCount, long topAttendanceCount) {
        double participationRate = topAttendanceCount == 0
                ? 0.0
                : Math.round(((double) attendanceCount / topAttendanceCount) * 1000) / 10.0;

        return ParticipationResponseDto.ParticipationMemberDto.builder()
                .memberId(member.getMemberId())
                .characterName(member.getCharacterName())
                .guildName(member.getGuildName())
                .characterClass(member.getCharacterClass())
                .level(member.getLevel())
                .combatPower(member.getCombatPower())
                .attendanceCount(attendanceCount)
                .topAttendanceCount(topAttendanceCount)
                .participationRate(participationRate)
                .build();
    }
}
