package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final ActivityAttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final ActivityTypeRepository activityTypeRepository;

    @PostMapping
    public Map<String, Object> recordAttendance(@Valid @RequestBody AttendanceRequest request) {
        requireAdmin(request.getAdminMemberId());

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        ActivityType activityType = activityTypeRepository.findById(request.getActivityTypeId())
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다."));

        LocalDate attendanceDate = request.getAttendanceDate() == null ? LocalDate.now() : request.getAttendanceDate();
        if (attendanceRepository.existsByMemberAndActivityTypeAndAttendanceDate(member, activityType, attendanceDate)) {
            throw new IllegalArgumentException("이미 같은 날짜의 활동 출석 기록이 존재합니다.");
        }

        ActivityAttendance attendance = ActivityAttendance.builder()
                .member(member)
                .activityType(activityType)
                .attendanceDate(attendanceDate)
                .status(request.getStatus() == null ? AttendanceStatus.ATTENDED : request.getStatus())
                .build();

        ActivityAttendance saved = attendanceRepository.save(attendance);

        return Map.of(
                "message", "참석 기록 저장 완료",
                "attendanceId", saved.getAttendanceId()
        );
    }

    @GetMapping
    public List<Map<String, Object>> getAllAttendances(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Integer limit
    ) {
        List<ActivityAttendance> attendances;
        if (startDate != null && endDate != null) {
            attendances = attendanceRepository.findByAttendanceDateBetweenOrderByAttendanceDateDescRecordedAtDesc(startDate, endDate);
        } else {
            attendances = attendanceRepository.findAllByOrderByAttendanceDateDescRecordedAtDesc(PageRequest.of(0, normalizeLimit(limit)));
        }

        return attendances.stream()
                .limit(normalizeLimit(limit))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/member/{memberId}")
    public List<Map<String, Object>> getMemberAttendances(@PathVariable Long memberId) {
        return attendanceRepository.findByMember_MemberId(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/date/{attendanceDate}")
    public List<Map<String, Object>> getAttendancesByDate(@PathVariable LocalDate attendanceDate) {
        return attendanceRepository.findByAttendanceDate(attendanceDate).stream()
                .map(this::toResponse)
                .toList();
    }

    private Map<String, Object> toResponse(ActivityAttendance attendance) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("attendanceId", attendance.getAttendanceId());
        response.put("attendanceDate", attendance.getAttendanceDate());
        response.put("status", attendance.getStatus());
        response.put("recordedAt", attendance.getRecordedAt());

        Member member = attendance.getMember();
        if (member != null) {
            Map<String, Object> memberMap = new LinkedHashMap<>();
            memberMap.put("memberId", member.getMemberId());
            memberMap.put("characterName", member.getCharacterName());
            memberMap.put("guildName", member.getGuildName());
            memberMap.put("characterClass", member.getCharacterClass());
            memberMap.put("level", member.getLevel());
            response.put("member", memberMap);
        }

        ActivityType activityType = attendance.getActivityType();
        if (activityType != null) {
            Map<String, Object> activityMap = new LinkedHashMap<>();
            activityMap.put("activityTypeId", activityType.getActivityTypeId());
            activityMap.put("typeName", activityType.getTypeName());
            response.put("activityType", activityMap);
        }

        return response;
    }

    private void requireAdmin(Long adminMemberId) {
        if (adminMemberId == null) {
            throw new IllegalArgumentException("운영자 확인 정보가 필요합니다.");
        }
        Member admin = memberRepository.findById(adminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("운영자 확인 정보가 필요합니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 출석을 직접 등록할 수 있습니다.");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 500;
        }
        return Math.min(limit, 2000);
    }

    @Getter
    @Setter
    public static class AttendanceRequest {
        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long adminMemberId;

        @NotNull(message = "출석 대상 클랜원을 선택해 주세요.")
        private Long memberId;

        @NotNull(message = "활동을 선택해 주세요.")
        private Long activityTypeId;
        private LocalDate attendanceDate;
        private AttendanceStatus status;
    }
}
