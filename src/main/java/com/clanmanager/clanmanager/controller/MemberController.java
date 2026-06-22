package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.MemberInfoResponseDto;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;
    private final ActivityAttendanceRepository attendanceRepository;

    @GetMapping("/{memberId}/my-info")
    public MemberInfoResponseDto getMyInfo(@PathVariable Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));

        long myCount = attendanceRepository.countByMemberAndStatus(member, AttendanceStatus.ATTENDED);

        Long topCount = attendanceRepository.findTopAttendanceCount();

        if (topCount == null) {
            topCount = 0L;
        }

        double participationRate = 0.0;

        if (topCount > 0) {
            participationRate = ((double) myCount / topCount) * 100.0;
        }

        return MemberInfoResponseDto.builder()
                .memberId(member.getMemberId())
                .characterName(member.getCharacterName())
                .combatPower(member.getCombatPower())
                .myAttendanceCount(myCount)
                .topAttendanceCount(topCount)
                .participationRate(Math.round(participationRate * 10) / 10.0)
                .build();
    }

    @GetMapping
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    @GetMapping("/{memberId}")
    public Member getMember(@PathVariable Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));
    }

    @GetMapping("/search")
    public List<Member> searchMembers(@RequestParam String keyword) {
        return memberRepository.findByCharacterNameContaining(keyword);
    }

    @PatchMapping("/{memberId}/rank")
    public Map<String, Object> updateRank(
            @PathVariable Long memberId,
            @RequestParam String rank
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));

        member.setRank(rank);
        memberRepository.save(member);

        return Map.of(
                "message", "등급 변경 완료",
                "memberId", member.getMemberId(),
                "rank", member.getRank()
        );
    }

    @PatchMapping("/{memberId}/status")
    public Map<String, Object> updateStatus(
            @PathVariable Long memberId,
            @RequestParam String status
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));

        member.setStatus(status);
        memberRepository.save(member);

        return Map.of(
                "message", "상태 변경 완료",
                "memberId", member.getMemberId(),
                "status", member.getStatus()
        );
    }
}