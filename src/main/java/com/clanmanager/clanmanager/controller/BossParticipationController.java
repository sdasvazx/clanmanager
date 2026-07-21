package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.BossParticipationMemberDto;
import com.clanmanager.clanmanager.dto.BossParticipationPageResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/boss-participations")
@RequiredArgsConstructor
public class BossParticipationController {

    private static final int HISTORY_PAGE_SIZE = 50;
    private static final int MAX_HISTORY_PAGES = 10;
    private static final Set<String> RESERVED_PARTICIPANT_NAMES = Set.of("\uC18C\uC218\uC7C1");

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

    @GetMapping("/page")
    public BossParticipationPageResponseDto getRecordPage(@RequestParam(defaultValue = "1") int page) {
        int safePage = normalizeHistoryPage(page);
        Page<BossParticipationRecord> result = recordRepository.findAllByOrderByBossDateDescCutTimeDescCreatedAtDesc(
                PageRequest.of(safePage - 1, HISTORY_PAGE_SIZE)
        );

        return new BossParticipationPageResponseDto(
                result.getContent().stream().map(this::toResponse).toList(),
                safePage,
                HISTORY_PAGE_SIZE,
                Math.min(Math.max(result.getTotalPages(), 1), MAX_HISTORY_PAGES),
                Math.min(result.getTotalElements(), (long) HISTORY_PAGE_SIZE * MAX_HISTORY_PAGES)
        );
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
        Stream<MemberBossParticipationDto> bossParticipationRecords = participationMemberRepository.findByMemberWithRecordOrderByRecent(member)
                .stream()
                .map(MemberBossParticipationDto::from);

        Stream<MemberBossParticipationDto> manualAttendanceRecords = activityAttendanceRepository
                .findManualAttendancesForMember(member)
                .stream()
                .map(MemberBossParticipationDto::fromAttendance);

        return Stream.concat(bossParticipationRecords, manualAttendanceRecords)
                .sorted(Comparator
                        .comparing(MemberBossParticipationDto::bossDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MemberBossParticipationDto::cutTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(80)
                .toList();
    }

    @PostMapping
    @Transactional
    public BossParticipationResponseDto createRecord(@Valid @RequestBody BossParticipationRequestDto request) {
        Member admin = findMember(request.getCreatedByMemberId());
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("\uC6B4\uC601\uC790\uB9CC \uBCF4\uC2A4 \uCC38\uC5EC\uB0B4\uC5ED\uC744 \uB4F1\uB85D\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        }

        String bossName = clean(request.getBossName());
        if (bossName.isBlank()) {
            throw new IllegalArgumentException("\uBCF4\uC2A4\uBA85\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694.");
        }

        boolean attendanceApplied = !Boolean.FALSE.equals(request.getAttendanceApplied());
        if (attendanceApplied && (request.getMembers() == null || request.getMembers().isEmpty())) {
            throw new IllegalArgumentException("\uCC38\uC5EC \uBA85\uB2E8\uC744 1\uBA85 \uC774\uC0C1 \uC785\uB825\uD574 \uC8FC\uC138\uC694.");
        }

        ActivityType activityType = resolveActivityType(request.getActivityTypeId(), bossName);
        BossParticipationRecord record = recordRepository.save(BossParticipationRecord.builder()
                .bossDate(request.getBossDate() == null ? LocalDate.now() : request.getBossDate())
                .cutTime(request.getCutTime() == null ? LocalTime.now().withSecond(0).withNano(0) : request.getCutTime())
                .bossName(bossName)
                .score(request.getScore() == null ? 1 : request.getScore())
                .penaltyApplied(Boolean.TRUE.equals(request.getPenaltyApplied()))
                .attendanceApplied(attendanceApplied)
                .activityType(activityType)
                .memo(clean(request.getMemo()))
                .createdBy(admin)
                .build());

        saveRecordMembers(record, request.getMembers(), activityType);

        return toResponse(record);
    }

    @PutMapping("/{recordId}/members")
    @Transactional
    public List<BossParticipationMemberDto> updateRecordMembers(
            @PathVariable Long recordId,
            @RequestParam Long adminMemberId,
            @RequestBody BossParticipationRequestDto request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("\uC6B4\uC601\uC790\uB9CC \uBCF4\uC2A4 \uCC38\uC5EC\uBA85\uB2E8\uC744 \uC218\uC815\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        }

        BossParticipationRecord record = findRecord(recordId);
        boolean attendanceApplied = request.getAttendanceApplied() == null
                ? !Boolean.FALSE.equals(record.getAttendanceApplied())
                : !Boolean.FALSE.equals(request.getAttendanceApplied());
        if (attendanceApplied && (request.getMembers() == null || request.getMembers().isEmpty())) {
            throw new IllegalArgumentException("\uCC38\uC5EC \uBA85\uB2E8\uC744 1\uBA85 \uC774\uC0C1 \uC785\uB825\uD574 \uC8FC\uC138\uC694.");
        }

        List<BossParticipationMember> previousMembers = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        ActivityType activityType = request.getActivityTypeId() == null
                ? resolveActivityType(record)
                : resolveActivityType(request.getActivityTypeId(), record.getBossName());
        if (request.getPenaltyApplied() != null) {
            record.setPenaltyApplied(request.getPenaltyApplied());
        }
        if (request.getAttendanceApplied() != null) {
            record.setAttendanceApplied(request.getAttendanceApplied());
        }
        record.setActivityType(activityType);
        recordRepository.save(record);
        participationMemberRepository.deleteByRecord(record);
        saveRecordMembers(record, request.getMembers(), activityType);

        List<BossParticipationMember> currentMembers = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        removeAttendanceForDeletedMembers(record, previousMembers, currentMembers);

        return currentMembers
                .stream()
                .map(BossParticipationMemberDto::from)
                .toList();
    }

    @PutMapping("/{recordId}")
    @Transactional
    public BossParticipationResponseDto updateRecord(
            @PathVariable Long recordId,
            @RequestParam Long adminMemberId,
            @RequestBody BossParticipationRequestDto request
    ) {
        Member admin = findMember(adminMemberId);
        requireAdmin(admin, "운영자만 보스 참여내역을 수정할 수 있습니다.");

        BossParticipationRecord record = findRecord(recordId);
        List<BossParticipationMember> previousMembers = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        LocalDate oldBossDate = record.getBossDate();
        ActivityType oldActivityType = resolveActivityType(record);

        String bossName = clean(request.getBossName());
        if (bossName.isBlank()) {
            throw new IllegalArgumentException("보스명을 입력해 주세요.");
        }

        boolean attendanceApplied = request.getAttendanceApplied() == null
                ? !Boolean.FALSE.equals(record.getAttendanceApplied())
                : !Boolean.FALSE.equals(request.getAttendanceApplied());
        List<BossParticipationRequestDto.MemberEntry> effectiveMembers = request.getMembers() == null || request.getMembers().isEmpty()
                ? toMemberEntries(previousMembers)
                : request.getMembers();
        if (attendanceApplied && effectiveMembers.isEmpty()) {
            throw new IllegalArgumentException("참여 명단을 1명 이상 입력해 주세요.");
        }

        ActivityType activityType = resolveActivityType(request.getActivityTypeId(), bossName);
        deleteAttendanceEntries(oldBossDate, oldActivityType, record, previousMembers);

        record.setBossDate(request.getBossDate() == null ? record.getBossDate() : request.getBossDate());
        record.setCutTime(request.getCutTime() == null ? record.getCutTime() : request.getCutTime());
        record.setBossName(bossName);
        record.setScore(request.getScore() == null ? record.getScore() : request.getScore());
        if (request.getPenaltyApplied() != null) {
            record.setPenaltyApplied(request.getPenaltyApplied());
        }
        record.setAttendanceApplied(attendanceApplied);
        record.setActivityType(activityType);
        record.setMemo(clean(request.getMemo()));
        recordRepository.save(record);

        participationMemberRepository.deleteByRecord(record);
        saveRecordMembers(record, effectiveMembers, activityType);

        return toResponse(record);
    }

    @DeleteMapping("/{recordId}")
    @Transactional
    public Map<String, String> deleteRecord(@PathVariable Long recordId, @RequestParam Long adminMemberId) {
        Member admin = findMember(adminMemberId);
        requireAdmin(admin, "운영자만 보스 참여내역을 삭제할 수 있습니다.");

        BossParticipationRecord record = findRecord(recordId);
        List<BossParticipationMember> previousMembers = participationMemberRepository.findByRecordOrderByClanNameAscCharacterNameAsc(record);
        deleteAttendanceEntries(record.getBossDate(), resolveActivityType(record), record, previousMembers);
        participationMemberRepository.deleteByRecord(record);
        recordRepository.delete(record);
        return Map.of("message", "deleted");
    }

    private void saveRecordMembers(BossParticipationRecord record, List<BossParticipationRequestDto.MemberEntry> members, ActivityType activityType) {
        boolean attendanceApplied = !Boolean.FALSE.equals(record.getAttendanceApplied());
        List<BossParticipationRequestDto.MemberEntry> safeMembers = members == null ? List.of() : members;
        safeMembers.stream()
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
                    if (attendanceApplied && matched != null && matched.getActive() && activityType != null) {
                        boolean exists = activityAttendanceRepository
                                .existsByMemberAndActivityTypeAndAttendanceDateAndBossParticipationRecord(
                                        matched,
                                        activityType,
                                        record.getBossDate(),
                                        record
                                );
                        if (!exists) {
                            activityAttendanceRepository.save(ActivityAttendance.builder()
                                    .member(matched)
                                    .activityType(activityType)
                                    .attendanceDate(record.getBossDate())
                                    .status(AttendanceStatus.ATTENDED)
                                    .bossParticipationRecord(record)
                                    .build());
                        }
                    }
                });
    }

    private void requireAdmin(Member member, String message) {
        if (member.getRole() != MemberRole.ADMIN) {
            throw new SecurityException(message);
        }
    }

    private List<BossParticipationRequestDto.MemberEntry> toMemberEntries(List<BossParticipationMember> members) {
        return members.stream()
                .map(member -> {
                    BossParticipationRequestDto.MemberEntry entry = new BossParticipationRequestDto.MemberEntry();
                    entry.setCharacterName(member.getCharacterName());
                    entry.setClanName(member.getClanName());
                    return entry;
                })
                .toList();
    }

    private void deleteAttendanceEntries(
            LocalDate bossDate,
            ActivityType activityType,
            BossParticipationRecord record,
            List<BossParticipationMember> members
    ) {
        if (bossDate == null || activityType == null || record == null || members == null) {
            return;
        }
        members.stream()
                .map(BossParticipationMember::getMember)
                .filter(Objects::nonNull)
                .forEach(previous -> activityAttendanceRepository.deleteByMemberAndActivityTypeAndAttendanceDateAndBossParticipationRecord(
                        previous,
                        activityType,
                        bossDate,
                        record
                ));
    }

    private void removeAttendanceForDeletedMembers(
            BossParticipationRecord record,
            List<BossParticipationMember> previousMembers,
            List<BossParticipationMember> currentMembers
    ) {
        ActivityType activityType = resolveActivityType(record);
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
                .forEach(previous -> activityAttendanceRepository.deleteByMemberAndActivityTypeAndAttendanceDateAndBossParticipationRecord(
                        previous,
                        activityType,
                        record.getBossDate(),
                        record
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
        ActivityType activityType = resolveActivityType(record);
        return BossParticipationResponseDto.from(
                record,
                members.size(),
                clanCounts,
                activityType != null,
                activityType == null ? null : activityType.getTypeName()
        );
    }

    private int normalizeHistoryPage(int page) {
        return Math.max(1, Math.min(page, MAX_HISTORY_PAGES));
    }

    private NormalizedEntry normalizeEntry(BossParticipationRequestDto.MemberEntry entry) {
        if (entry == null) {
            return null;
        }
        String characterName = clean(entry.getCharacterName());
        if (characterName.isBlank()) {
            return null;
        }
        if (RESERVED_PARTICIPANT_NAMES.contains(characterName)) {
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

    private ActivityType resolveActivityType(BossParticipationRecord record) {
        if (record.getActivityType() != null && Boolean.TRUE.equals(record.getActivityType().getActive())) {
            return record.getActivityType();
        }
        return resolveActivityType(record.getBossName());
    }

    private ActivityType resolveActivityType(Long activityTypeId, String bossName) {
        if (activityTypeId != null) {
            return activityTypeRepository.findById(activityTypeId)
                    .filter(activityType -> Boolean.TRUE.equals(activityType.getActive()))
                    .orElseThrow(() -> new IllegalArgumentException("?쒕룞 ?ㅼ젙??李얠쓣 ???놁뒿?덈떎."));
        }
        return resolveActivityType(bossName);
    }

    private Optional<ActivityType> findActivityTypeByKeyword(String bossName) {
        String compact = bossName.replaceAll("\\s+", "").toLowerCase();

        if (compact.contains("13")) {
            return activityTypeRepository.findByTypeName("13\uC2DC (2\uC131)")
                    .or(() -> activityTypeRepository.findByTypeName("13\uC2DC \uBCF4\uC2A4"));
        }
        if (compact.contains("17")) {
            return activityTypeRepository.findByTypeName("17\uC2DC (1\uC131)")
                    .or(() -> activityTypeRepository.findByTypeName("17\uC2DC \uBCF4\uC2A4"));
        }
        if (compact.contains("21")) {
            return activityTypeRepository.findByTypeName("21\uC2DC (2\uC131)")
                    .or(() -> activityTypeRepository.findByTypeName("21\uC2DC \uBCF4\uC2A4"));
        }
        if (compact.contains("\uC18C\uC218\uC7C1")) {
            return activityTypeRepository.findByTypeName("\uC18C\uC218\uC7C1");
        }
        if (compact.contains("\uC815\uC608")) {
            return activityTypeRepository.findByTypeName("\uC815\uC608\uB358\uC804\uBCF4\uC2A4");
        }
        if (compact.contains("\uC5D0\uB178\uD06C")) {
            return activityTypeRepository.findByTypeName("\uC5D0\uB178\uD06C");
        }
        if (compact.contains("\uB9C8\uC288\uBBF8\uB4DC") || compact.contains("\uB9C8\uC288")) {
            return activityTypeRepository.findByTypeName("\uB9C8\uC288\uBBF8\uB4DC");
        }
        if (compact.contains("\uACB0\uC2B9")) {
            return activityTypeRepository.findByTypeName("\uACB0\uC2B9\uC804");
        }
        if (compact.contains("\uC804\uCD08")) {
            return activityTypeRepository.findByTypeName("\uC804\uCD08\uC804");
        }
        if (compact.contains("\uD074\uB79C\uC784\uBB34") || compact.contains("\uC784\uBB34")) {
            return activityTypeRepository.findByTypeName("\uD074\uB79C\uC784\uBB34");
        }
        if (compact.contains("\uC218\uD638")) {
            return activityTypeRepository.findByTypeName("\uD074\uB79C\uC218\uD638")
                    .or(() -> activityTypeRepository.findByTypeName("\uC218\uD638"));
        }
        if (compact.contains("\uC7C1\uD0C8")) {
            return activityTypeRepository.findByTypeName("\uC7C1\uD0C8\uC804");
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

        public static MemberBossParticipationDto fromAttendance(ActivityAttendance attendance) {
            ActivityType activityType = attendance.getActivityType();
            Integer score = activityType != null && activityType.getParticipationScore() != null
                    ? activityType.getParticipationScore()
                    : 1;
            Member member = attendance.getMember();
            return new MemberBossParticipationDto(
                    attendance.getAttendanceId() == null ? null : -attendance.getAttendanceId(),
                    attendance.getAttendanceDate(),
                    null,
                    activityType == null ? "수동 출석" : activityType.getTypeName(),
                    score,
                    member == null ? "" : member.getGuildName(),
                    member == null ? "" : member.getCharacterName()
            );
        }
    }

    private record NormalizedEntry(String characterName, String clanName) {
    }
}
