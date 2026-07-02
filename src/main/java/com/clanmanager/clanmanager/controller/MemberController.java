package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.MemberInfoResponseDto;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        Member member = findMember(memberId);
        long myCount = attendanceRepository.countByMemberAndStatus(member, AttendanceStatus.ATTENDED);
        long topCount = attendanceRepository.findAttendanceCountsByMember(PageRequest.of(0, 1))
                .stream().findFirst().orElse(0L);
        double participationRate = topCount == 0 ? 0.0 : ((double) myCount / topCount) * 100.0;

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
        return findMember(memberId);
    }

    @GetMapping("/search")
    public List<Member> searchMembers(@RequestParam String keyword) {
        return memberRepository.findByCharacterNameContaining(keyword);
    }

    @PostMapping
    public Member createMember(
            @RequestParam Long adminMemberId,
            @RequestBody CreateMemberRequest request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜원을 미리 등록할 수 있습니다.");
        }

        String characterName = request.getCharacterName() == null ? "" : request.getCharacterName().trim();
        if (characterName.isBlank()) {
            throw new IllegalArgumentException("캐릭터 이름은 비워둘 수 없습니다.");
        }
        if (memberRepository.existsByCharacterName(characterName)) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        String initialPassword = request.getInitialPassword() == null || request.getInitialPassword().isBlank()
                ? "112200"
                : request.getInitialPassword();

        return memberRepository.save(Member.builder()
                .characterName(characterName)
                .password(initialPassword)
                .combatPower(request.getCombatPower() == null ? 0 : request.getCombatPower())
                .rank(blankToNull(request.getRank()))
                .status(blankToNull(request.getStatus()))
                .role(MemberRole.MEMBER)
                .active(request.getActive() == null ? true : request.getActive())
                .build());
    }

    @PatchMapping("/{memberId}/rank")
    public Map<String, Object> updateRank(@PathVariable Long memberId, @RequestParam String rank) {
        Member member = findMember(memberId);
        member.setRank(rank);
        memberRepository.save(member);
        return Map.of("message", "직급 변경 완료", "memberId", member.getMemberId(), "rank", member.getRank());
    }

    @PatchMapping("/{memberId}/status")
    public Map<String, Object> updateStatus(@PathVariable Long memberId, @RequestParam String status) {
        Member member = findMember(memberId);
        member.setStatus(status);
        memberRepository.save(member);
        return Map.of("message", "상태 변경 완료", "memberId", member.getMemberId(), "status", member.getStatus());
    }

    @PatchMapping("/{memberId}/profile")
    public Member updateProfile(
            @PathVariable Long memberId,
            @RequestParam Long adminMemberId,
            @RequestBody MemberProfileRequest request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜원 정보를 수정할 수 있습니다.");
        }

        Member member = findMember(memberId);
        String characterName = request.getCharacterName() == null ? "" : request.getCharacterName().trim();
        if (characterName.isBlank()) {
            throw new IllegalArgumentException("캐릭터 이름은 비워둘 수 없습니다.");
        }
        if (memberRepository.existsByCharacterNameAndMemberIdNot(characterName, memberId)) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        member.setCharacterName(characterName);
        member.setCombatPower(request.getCombatPower() == null ? 0 : request.getCombatPower());
        member.setRank(blankToNull(request.getRank()));
        member.setStatus(blankToNull(request.getStatus()));
        member.setActive(request.getActive() == null ? true : request.getActive());
        return memberRepository.save(member);
    }

    @PatchMapping("/{memberId}/password")
    public Map<String, Object> changePassword(
            @PathVariable Long memberId,
            @RequestBody PasswordChangeRequest request
    ) {
        Member member = findMember(memberId);
        if (request.getCurrentPassword() == null || !member.getPassword().equals(request.getCurrentPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().length() < 4) {
            throw new IllegalArgumentException("새 비밀번호는 4자리 이상으로 입력해 주세요.");
        }

        member.setPassword(request.getNewPassword());
        memberRepository.save(member);
        return Map.of("message", "비밀번호 변경 완료", "memberId", member.getMemberId());
    }

    @PatchMapping("/{memberId}/role")
    public Map<String, Object> updateRole(
            @PathVariable Long memberId,
            @RequestParam String role,
            @RequestParam Long adminMemberId
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 권한을 변경할 수 있습니다.");
        }

        Member member = findMember(memberId);
        MemberRole nextRole;
        try {
            nextRole = MemberRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("존재하지 않는 권한입니다.");
        }

        if (member.getRole() == MemberRole.ADMIN && nextRole == MemberRole.MEMBER
                && memberRepository.countByRole(MemberRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("마지막 운영자는 일반 클랜원으로 변경할 수 없습니다.");
        }

        if (member.getMemberId().equals(adminMemberId) && nextRole == MemberRole.MEMBER) {
            throw new IllegalArgumentException("본인의 운영자 권한은 직접 해제할 수 없습니다.");
        }

        member.setRole(nextRole);
        memberRepository.save(member);
        return Map.of(
                "message", "권한 변경 완료",
                "memberId", member.getMemberId(),
                "characterName", member.getCharacterName(),
                "role", member.getRole().name()
        );
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Getter
    @Setter
    public static class MemberProfileRequest {
        private String characterName;
        private Integer combatPower;
        private String rank;
        private String status;
        private Boolean active;
    }

    @Getter
    @Setter
    public static class CreateMemberRequest {
        private String characterName;
        private String initialPassword;
        private Integer combatPower;
        private String rank;
        private String status;
        private Boolean active;
    }

    @Getter
    @Setter
    public static class PasswordChangeRequest {
        private String currentPassword;
        private String newPassword;
    }
}
