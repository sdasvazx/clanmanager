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
import org.springframework.web.bind.annotation.DeleteMapping;
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
                .guildName(member.getGuildName())
                .characterClass(member.getCharacterClass())
                .level(member.getLevel())
                .myAttendanceCount(myCount)
                .topAttendanceCount(topCount)
                .participationRate(Math.round(participationRate * 10) / 10.0)
                .build();
    }

    @GetMapping
    public List<Member> getAllMembers() {
        return memberRepository.findByActiveTrueOrderByMemberIdAsc();
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
                .guildName(blankToNull(request.getGuildName()))
                .characterClass(blankToNull(request.getCharacterClass()))
                .level(request.getLevel() == null ? 0 : request.getLevel())
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
        member.setGuildName(blankToNull(request.getGuildName()));
        member.setCharacterClass(blankToNull(request.getCharacterClass()));
        member.setLevel(request.getLevel() == null ? 0 : request.getLevel());
        member.setRank(blankToNull(request.getRank()));
        member.setStatus(blankToNull(request.getStatus()));
        member.setActive(request.getActive() == null ? true : request.getActive());
        return memberRepository.save(member);
    }

    @PostMapping("/bulk-import")
    public Map<String, Object> bulkImportMembers(@RequestBody MemberBulkImportRequest request) {
        Member admin = findMember(request.getAdminMemberId());
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜원 명단을 일괄 등록할 수 있습니다.");
        }
        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new IllegalArgumentException("등록할 클랜원 명단이 비어 있습니다.");
        }

        int created = 0;
        int updated = 0;
        for (BulkMemberRequest row : request.getMembers()) {
            String characterName = row.getCharacterName() == null ? "" : row.getCharacterName().trim();
            if (characterName.isBlank()) {
                continue;
            }

            Member member = memberRepository.findByCharacterName(characterName).orElse(null);
            boolean isNew = member == null;
            if (isNew) {
                member = Member.builder()
                        .characterName(characterName)
                        .password(row.getInitialPassword() == null || row.getInitialPassword().isBlank() ? "112200" : row.getInitialPassword())
                        .active(true)
                        .build();
            }

            member.setCombatPower(row.getCombatPower() == null ? 0 : row.getCombatPower());
            member.setGuildName(blankToNull(row.getGuildName()));
            member.setCharacterClass(blankToNull(row.getCharacterClass()));
            member.setLevel(row.getLevel() == null ? 0 : row.getLevel());
            member.setRank(blankToNull(row.getRank()));
            member.setStatus(blankToNull(row.getStatus()) == null ? "활동중" : blankToNull(row.getStatus()));
            member.setActive(row.getActive() == null ? true : row.getActive());
            if (row.getAdmin() != null) {
                member.setRole(row.getAdmin() ? MemberRole.ADMIN : MemberRole.MEMBER);
            } else if (member.getRole() == null) {
                member.setRole(MemberRole.MEMBER);
            }

            memberRepository.save(member);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        return Map.of(
                "message", "클랜원 명단 일괄 등록 완료",
                "created", created,
                "updated", updated,
                "total", created + updated
        );
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

    @PatchMapping("/{memberId}/password/reset")
    public Map<String, Object> resetPassword(
            @PathVariable Long memberId,
            @RequestParam Long adminMemberId,
            @RequestBody(required = false) PasswordResetRequest request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 비밀번호를 초기화할 수 있습니다.");
        }

        String nextPassword = request == null ? null : request.getNewPassword();
        if (nextPassword == null || nextPassword.trim().isBlank()) {
            nextPassword = "112200";
        }
        if (nextPassword.trim().length() < 4) {
            throw new IllegalArgumentException("새 비밀번호는 4자리 이상으로 입력해 주세요.");
        }

        Member member = findMember(memberId);
        member.setPassword(nextPassword.trim());
        memberRepository.save(member);

        return Map.of(
                "message", "비밀번호 초기화 완료",
                "memberId", member.getMemberId(),
                "characterName", member.getCharacterName()
        );
    }

    @DeleteMapping("/{memberId}")
    public Map<String, Object> deleteMember(
            @PathVariable Long memberId,
            @RequestParam Long adminMemberId
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜원을 삭제할 수 있습니다.");
        }
        if (memberId.equals(adminMemberId)) {
            throw new IllegalArgumentException("본인 계정은 삭제할 수 없습니다.");
        }

        Member member = findMember(memberId);
        if (member.getRole() == MemberRole.ADMIN && memberRepository.countByRole(MemberRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("마지막 운영자는 삭제할 수 없습니다.");
        }

        member.setActive(false);
        member.setStatus("삭제됨");
        memberRepository.save(member);

        return Map.of(
                "message", "클랜원 삭제 완료",
                "memberId", member.getMemberId(),
                "characterName", member.getCharacterName()
        );
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
        private String guildName;
        private String characterClass;
        private Integer level;
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
        private String guildName;
        private String characterClass;
        private Integer level;
        private String rank;
        private String status;
        private Boolean active;
    }

    @Getter
    @Setter
    public static class MemberBulkImportRequest {
        private Long adminMemberId;
        private List<BulkMemberRequest> members;
    }

    @Getter
    @Setter
    public static class BulkMemberRequest {
        private String characterName;
        private String initialPassword;
        private Integer combatPower;
        private String guildName;
        private String characterClass;
        private Integer level;
        private String rank;
        private String status;
        private Boolean active;
        private Boolean admin;
    }

    @Getter
    @Setter
    public static class PasswordChangeRequest {
        private String currentPassword;
        private String newPassword;
    }

    @Getter
    @Setter
    public static class PasswordResetRequest {
        private String newPassword;
    }
}
