package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.ActivityType;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.ActivityTypeRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

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
    public Map<String, Object> recordAttendance(@RequestBody AttendanceRequest request) {

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        ActivityType activityType = activityTypeRepository.findById(request.getActivityTypeId())
                .orElseThrow(() -> new RuntimeException("활동을 찾을 수 없습니다."));

        ActivityAttendance attendance = ActivityAttendance.builder()
                .member(member)
                .activityType(activityType)
                .attendanceDate(request.getAttendanceDate())
                .status(request.getStatus())
                .build();

        ActivityAttendance saved = attendanceRepository.save(attendance);

        return Map.of(
                "message", "참석 기록 저장 완료",
                "attendanceId", saved.getAttendanceId()
        );
    }

    @GetMapping
    public List<Map<String, Object>> getAllAttendances() {
        return attendanceRepository.findAll().stream()
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

    @Getter
    @Setter
    public static class AttendanceRequest {
        private Long memberId;
        private Long activityTypeId;
        private LocalDate attendanceDate;
        private AttendanceStatus status;
    }
}
