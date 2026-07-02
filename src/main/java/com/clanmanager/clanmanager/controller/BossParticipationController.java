package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.BossParticipationMemberDto;
import com.clanmanager.clanmanager.dto.BossParticipationRequestDto;
import com.clanmanager.clanmanager.dto.BossParticipationResponseDto;
import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.BossParticipationMember;
import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import com.clanmanager.clanmanager.repository.BossParticipationMemberRepository;
import com.clanmanager.clanmanager.repository.BossParticipationRecordRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/boss-participations")
@RequiredArgsConstructor
public class BossParticipationController {

    private final BossParticipationRecordRepository recordRepository;
    private final BossParticipationMemberRepository participationMemberRepository;
    private final MemberRepository memberRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final ActivityAttendanceRepository activityAttendanceRepository;

    @GetMapping
    public List<BossParticipationResponseDto> getRecords() {
        return recordRepository.findAllByOrderByBossDateDescCutTimeDescCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{recordId}/members")
    public List<BossParticipationMemberDto> getRecordMembers(@PathVariable Long recordId) {
        BossParticipationRecord record = findRecord(recordId);
        return participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record)
                .stream()
                .map(BossParticipationMemberDto::from)
                .toList();
    }

    @PostMapping
    @Transactional
    public BossParticipationResponseDto createRecord(@RequestBody BossParticipationRequestDto request) {
        Member admin = findMember(request.getCreatedByMemberId());
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 보스 참여내역을 등록할 수 있습니다.");
        }

        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new IllegalArgumentException("참여 명단을 1명 이상 입력해 주세요.");
        }

        String bossName = clean(request.getBossName());
        if (bossName.isBlank()) {
            throw new IllegalArgumentException("보스명을 입력해 주세요.");
        }

        BossParticipationRecord record = recordRepository.save(BossParticipationRecord.builder()
                .bossDate(request.getBossDate() == null ? LocalDate.now() : request.getBossDate())
                .cutTime(request.getCutTime() == null ? LocalTime.now().withSecond(0).withNano(0) : request.getCutTime())
                .bossName(bossName)
                .score(request.getScore() == null ? 1 : request.getScore())
                .memo(clean(request.getMemo()))
                .createdBy(admin)
                .build());

        ActivityType activityType = resolveActivityType(bossName);

        request.getMembers().stream()
                .map(this::normalizeEntry)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(entry -> {
                    Member matched = memberRepository.findByCharacterName(entry.characterName()).orElse(null);
                    participationMemberRepository.save(BossParticipationMember.builder()
                            .record(record)
                            .member(matched)
                            .characterName(entry.characterName())
                            .clanName(entry.clanName())
                            .matched(matched != null)
                            .build());
                    if (matched != null && matched.getActive() && activityType != null
                            && !activityAttendanceRepository.existsByMemberAndActivityTypeAndAttendanceDate(matched, activityType, record.getBossDate())) {
                        activityAttendanceRepository.save(ActivityAttendance.builder()
                                .member(matched)
                                .activityType(activityType)
                                .attendanceDate(record.getBossDate())
                                .status(AttendanceStatus.ATTENDED)
                                .build());
                    }
                });

        return toResponse(record);
    }

    private BossParticipationResponseDto toResponse(BossParticipationRecord record) {
        List<BossParticipationMember> members = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        Map<String, Long> clanCounts = members.stream()
                .collect(Collectors.groupingBy(
                        BossParticipationMember::getClanName,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return BossParticipationResponseDto.from(record, members.size(), clanCounts);
    }

    private NormalizedEntry normalizeEntry(BossParticipationRequestDto.MemberEntry entry) {
        if (entry == null) {
            return null;
        }
        String characterName = clean(entry.getCharacterName());
        if (characterName.isBlank()) {
            return null;
        }
        String clanName = clean(entry.getClanName());
        return new NormalizedEntry(characterName, clanName.isBlank() ? "미분류" : clanName);
    }

    private BossParticipationRecord findRecord(Long recordId) {
        return recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("보스 참여내역을 찾을 수 없습니다."));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private ActivityType resolveActivityType(String bossName) {
        String name = clean(bossName);
        if (name.contains("13")) {
            return activityTypeRepository.findByTypeName("13시 보스").orElse(null);
        }
        if (name.contains("17")) {
            return activityTypeRepository.findByTypeName("17시 보스").orElse(null);
        }
        if (name.contains("21")) {
            return activityTypeRepository.findByTypeName("21시 보스").orElse(null);
        }
        if (name.contains("에노크")) {
            return activityTypeRepository.findByTypeName("에노크").orElse(null);
        }
        if (name.contains("마슈미드")) {
            return activityTypeRepository.findByTypeName("마슈미드").orElse(null);
        }
        if (name.contains("정예")) {
            return activityTypeRepository.findByTypeName("정예던전보스").orElse(null);
        }
        if (name.contains("클랜임무")) {
            return activityTypeRepository.findByTypeName("클랜임무").orElse(null);
        }
        if (name.contains("수호")) {
            return activityTypeRepository.findByTypeName("수호").orElse(null);
        }
        if (name.contains("쟁탈")) {
            return activityTypeRepository.findByTypeName("쟁탈전").orElse(null);
        }
        return activityTypeRepository.findByTypeName(name).orElse(null);
    }

    private record NormalizedEntry(String characterName, String clanName) {
    }
}
