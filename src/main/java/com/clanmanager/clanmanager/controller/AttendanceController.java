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
    public List<ActivityAttendance> getAllAttendances() {
        return attendanceRepository.findAll();
    }

    @GetMapping("/member/{memberId}")
    public List<ActivityAttendance> getMemberAttendances(@PathVariable Long memberId) {
        return attendanceRepository.findByMember_MemberId(memberId);
    }

    @GetMapping("/date/{attendanceDate}")
    public List<ActivityAttendance> getAttendancesByDate(@PathVariable LocalDate attendanceDate) {
        return attendanceRepository.findByAttendanceDate(attendanceDate);
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