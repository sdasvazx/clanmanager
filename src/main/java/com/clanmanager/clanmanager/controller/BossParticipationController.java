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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    @GetMapping("/member/{memberId}")
    public List<MemberBossParticipationDto> getMemberRecords(@PathVariable Long memberId) {
        Member member = findMember(memberId);
        return participationMemberRepository.findByMemberWithRecordOrderByRecent(member)
                .stream()
                .limit(80)
                .map(MemberBossParticipationDto::from)
                .toList();
    }

    @PostMapping
    @Transactional
    public BossParticipationResponseDto createRecord(@Valid @RequestBody BossParticipationRequestDto request) {
        Member admin = findMember(request.getCreatedByMemberId());
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("\uC6B4\uC601\uC790\uB9CC \uBCF4\uC2A4 \uCC38\uC5EC\uB0B4\uC5ED\uC744 \uB4F1\uB85D\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        }

        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new IllegalArgumentException("\uCC38\uC5EC \uBA85\uB2E8\uC744 1\uBA85 \uC774\uC0C1 \uC785\uB825\uD574 \uC8FC\uC138\uC694.");
        }

        String bossName = clean(request.getBossName());
        if (bossName.isBlank()) {
            throw new IllegalArgumentException("\uBCF4\uC2A4\uBA85\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694.");
        }

        BossParticipationRecord record = recordRepository.save(BossParticipationRecord.builder()
                .bossDate(request.getBossDate() == null ? LocalDate.now() : request.getBossDate())
                .cutTime(request.getCutTime() == null ? LocalTime.now().withSecond(0).withNano(0) : request.getCutTime())
                .bossName(bossName)
                .score(request.getScore() == null ? 1 : request.getScore())
                .memo(clean(request.getMemo()))
                .createdBy(admin)
                .build());

        saveRecordMembers(record, request.getMembers());

        return toResponse(record);
    }

    @PutMapping("/{recordId}/members")
    @Transactional
    public List<BossParticipationMemberDto> updateRecordMembers(
            @PathVariable Long recordId,
            @RequestParam Long adminMemberId,
            @Valid @RequestBody BossParticipationRequestDto request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("\uC6B4\uC601\uC790\uB9CC \uBCF4\uC2A4 \uCC38\uC5EC\uBA85\uB2E8\uC744 \uC218\uC815\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        }

        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new IllegalArgumentException("\uCC38\uC5EC \uBA85\uB2E8\uC744 1\uBA85 \uC774\uC0C1 \uC785\uB825\uD574 \uC8FC\uC138\uC694.");
        }

        BossParticipationRecord record = findRecord(recordId);
        List<BossParticipationMember> previousMembers = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        participationMemberRepository.deleteByRecord(record);
        saveRecordMembers(record, request.getMembers());

        List<BossParticipationMember> currentMembers = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        removeAttendanceForDeletedMembers(record, previousMembers, currentMembers);

        return currentMembers
                .stream()
                .map(BossParticipationMemberDto::from)
                .toList();
    }

    private void saveRecordMembers(BossParticipationRecord record, List<BossParticipationRequestDto.MemberEntry> members) {
        ActivityType activityType = resolveActivityType(record.getBossName());

        members.stream()
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
    }

    private void removeAttendanceForDeletedMembers(
            BossParticipationRecord record,
            List<BossParticipationMember> previousMembers,
            List<BossParticipationMember> currentMembers
    ) {
        ActivityType activityType = resolveActivityType(record.getBossName());
        if (activityType == null) {
            return;
        }

        Set<Long> currentMemberIds = currentMembers.stream()
                .map(BossParticipationMember::getMember)
                .filter(Objects::nonNull)
                .map(Member::getMemberId)
                .collect(Collectors.toSet());

        previousMembers.stream()
                .map(BossParticipationMember::getMember)
                .filter(Objects::nonNull)
                .filter(previous -> !currentMemberIds.contains(previous.getMemberId()))
                .forEach(previous -> activityAttendanceRepository.deleteByMemberAndActivityTypeAndAttendanceDate(
                        previous,
                        activityType,
                        record.getBossDate()
                ));
    }

    private BossParticipationResponseDto toResponse(BossParticipationRecord record) {
        List<BossParticipationMember> members = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        Map<String, Long> clanCounts = members.stream()
                .collect(Collectors.groupingBy(
                        BossParticipationMember::getClanName,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        ActivityType activityType = resolveActivityType(record.getBossName());
        return BossParticipationResponseDto.from(
                record,
                members.size(),
                clanCounts,
                activityType != null,
                activityType == null ? null : activityType.getTypeName()
        );
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
        return new NormalizedEntry(characterName, clanName.isBlank() ? "\uBBF8\uBD84\uB958" : clanName);
    }

    private BossParticipationRecord findRecord(Long recordId) {
        return recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("\uBCF4\uC2A4 \uCC38\uC5EC\uB0B4\uC5ED\uC744 \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("\uD68C\uC6D0 \uC815\uBCF4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private ActivityType resolveActivityType(String bossName) {
        String name = clean(bossName);
        if (name.isBlank()) {
            return null;
        }

        return activityTypeRepository.findByTypeName(name)
                .or(() -> findActivityTypeByKeyword(name))
                .orElse(null);
    }

    private Optional<ActivityType> findActivityTypeByKeyword(String bossName) {
        String compact = bossName.replaceAll("\\s+", "").toLowerCase();

        if (compact.contains("\uC815\uC608")) {
            return activityTypeRepository.findByTypeName("\uC815\uC608\uB358\uC804\uBCF4\uC2A4");
        }
        if (compact.contains("\uC5D0\uB178\uD06C")) {
            return activityTypeRepository.findByTypeName("\uC5D0\uB178\uD06C");
        }
        if (compact.contains("\uB9C8\uC288\uBBF8\uB4DC") || compact.contains("\uB9C8\uC288")) {
            return activityTypeRepository.findByTypeName("\uB9C8\uC288\uBBF8\uB4DC");
        }
        if (compact.contains("\uD074\uB79C\uC784\uBB34") || compact.contains("\uC784\uBB34")) {
            return activityTypeRepository.findByTypeName("\uD074\uB79C\uC784\uBB34");
        }
        if (compact.contains("\uC218\uD638")) {
            return activityTypeRepository.findByTypeName("\uC218\uD638");
        }
        if (compact.contains("\uC7C1\uD0C8")) {
            return activityTypeRepository.findByTypeName("\uC7C1\uD0C8\uC804");
        }
        if (compact.contains("13")) {
            return activityTypeRepository.findByTypeName("13\uC2DC \uBCF4\uC2A4");
        }
        if (compact.contains("17")) {
            return activityTypeRepository.findByTypeName("17\uC2DC \uBCF4\uC2A4");
        }
        if (compact.contains("21")) {
            return activityTypeRepository.findByTypeName("21\uC2DC \uBCF4\uC2A4");
        }
        return Optional.empty();
    }

    public record MemberBossParticipationDto(
            Long recordId,
            LocalDate bossDate,
            LocalTime cutTime,
            String bossName,
            Integer score,
            String clanName,
            String characterName
    ) {
        public static MemberBossParticipationDto from(BossParticipationMember member) {
            BossParticipationRecord record = member.getRecord();
            return new MemberBossParticipationDto(
                    record.getRecordId(),
                    record.getBossDate(),
                    record.getCutTime(),
                    record.getBossName(),
                    record.getScore(),
                    member.getClanName(),
                    member.getCharacterName()
            );
        }
    }

    private record NormalizedEntry(String characterName, String clanName) {
    }
}
