package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.MemberInfoResponseDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.MemberSpecHistory;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.repository.MemberSpecHistoryRepository;
import com.clanmanager.clanmanager.security.PasswordSupport;
import com.clanmanager.clanmanager.service.ParticipationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;
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
import java.util.Objects;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private static final String RESERVED_ACTIVITY_MEMBER_NAME = "\uC18C\uC218\uC7C1";

    private final MemberRepository memberRepository;
    private final MemberSpecHistoryRepository memberSpecHistoryRepository;
    private final ParticipationService participationService;

    @GetMapping("/{memberId}/my-info")
    public MemberInfoResponseDto getMyInfo(@PathVariable Long memberId) {
        Member member = findMember(memberId);
        var participation = participationService.getMemberParticipation(memberId, null, null);

        return MemberInfoResponseDto.builder()
                .memberId(member.getMemberId())
                .characterName(member.getCharacterName())
                .combatPower(member.getCombatPower())
                .guildName(member.getGuildName())
                .characterClass(member.getCharacterClass())
                .level(member.getLevel())
                .myAttendanceCount(participation.getAttendanceCount())
                .topAttendanceCount(participation.getTopAttendanceCount())
                .totalActivityCount(participation.getTotalActivityCount())
                .participationRate(participation.getParticipationRate())
                .build();
    }

    @GetMapping
    public List<Member> getAllMembers() {
        return memberRepository.findByActiveTrueOrderByMemberIdAsc();
    }

    @GetMapping("/spec-histories")
    public List<MemberSpecHistoryDto> getSpecHistories() {
        return memberSpecHistoryRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(MemberSpecHistoryDto::from)
                .toList();
    }

    @GetMapping("/{memberId}/spec-histories")
    public List<MemberSpecHistoryDto> getMemberSpecHistories(@PathVariable Long memberId) {
        findMember(memberId);
        return memberSpecHistoryRepository.findTop5ByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(MemberSpecHistoryDto::from)
                .toList();
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
            @Valid @RequestBody CreateMemberRequest request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜원을 미리 등록할 수 있습니다.");
        }

        String characterName = request.getCharacterName() == null ? "" : request.getCharacterName().trim();
        if (characterName.isBlank()) {
            throw new IllegalArgumentException("캐릭터 이름은 비워둘 수 없습니다.");
        }
        validateMemberNameAllowed(characterName);
        Member member = memberRepository.findByCharacterName(characterName).orElse(null);
        if (member != null && Boolean.TRUE.equals(member.getActive())) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        if (member == null) {
            member = Member.builder()
                    .characterName(characterName)
                    .password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
                    .role(MemberRole.MEMBER)
                    .build();
        } else {
            // 삭제는 비활성 처리이므로 같은 닉네임을 다시 등록할 때 기존 계정을 복구한다.
            // 기존 행을 재사용하면 출석/금고 등 연관 기록의 참조도 유지된다.
            member.setPassword(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD));
            member.setRole(MemberRole.MEMBER);
        }

        member.setCombatPower(request.getCombatPower() == null ? 0 : request.getCombatPower());
        member.setGuildName(blankToNull(request.getGuildName()));
        member.setCharacterClass(blankToNull(request.getCharacterClass()));
        member.setLevel(request.getLevel() == null ? 0 : request.getLevel());
        member.setRank(blankToNull(request.getRank()));
        member.setStatus(blankToNull(request.getStatus()));
        member.setActive(request.getActive() == null ? true : request.getActive());
        return memberRepository.save(member);
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
            @Valid @RequestBody MemberProfileRequest request
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

        validateMemberNameAllowed(characterName);

        String previousName = member.getCharacterName();
        Integer previousCombatPower = member.getCombatPower();
        Integer previousLevel = member.getLevel();
        String previousGuildName = member.getGuildName();
        String previousCharacterClass = member.getCharacterClass();
        String previousRank = member.getRank();
        String previousStatus = member.getStatus();

        member.setCharacterName(characterName);
        member.setCombatPower(request.getCombatPower() == null ? 0 : request.getCombatPower());
        member.setGuildName(blankToNull(request.getGuildName()));
        member.setCharacterClass(blankToNull(request.getCharacterClass()));
        member.setLevel(request.getLevel() == null ? 0 : request.getLevel());
        member.setRank(blankToNull(request.getRank()));
        member.setStatus(blankToNull(request.getStatus()));
        member.setActive(request.getActive() == null ? true : request.getActive());
        Member saved = memberRepository.save(member);
        saveSpecHistoryIfChanged(
                saved,
                admin,
                previousName,
                previousCombatPower,
                previousLevel,
                previousGuildName,
                previousCharacterClass,
                previousRank,
                previousStatus
        );
        return saved;
    }

    @PatchMapping("/{memberId}/self-profile")
    public Member updateSelfProfile(
            @PathVariable Long memberId,
            @Valid @RequestBody SelfProfileRequest request
    ) {
        Member member = findMember(memberId);
        String characterName = request.getCharacterName() == null ? "" : request.getCharacterName().trim();
        if (characterName.isBlank()) {
            throw new IllegalArgumentException("캐릭터 이름은 비워둘 수 없습니다.");
        }
        if (memberRepository.existsByCharacterNameAndMemberIdNot(characterName, memberId)) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        validateMemberNameAllowed(characterName);

        String previousName = member.getCharacterName();
        Integer previousCombatPower = member.getCombatPower();
        Integer previousLevel = member.getLevel();
        String previousGuildName = member.getGuildName();
        String previousCharacterClass = member.getCharacterClass();
        String previousRank = member.getRank();
        String previousStatus = member.getStatus();

        member.setCharacterName(characterName);
        member.setGuildName(blankToNull(request.getGuildName()));
        member.setCharacterClass(blankToNull(request.getCharacterClass()));
        Member saved = memberRepository.save(member);
        saveSpecHistoryIfChanged(
                saved,
                saved,
                previousName,
                previousCombatPower,
                previousLevel,
                previousGuildName,
                previousCharacterClass,
                previousRank,
                previousStatus
        );
        return saved;
    }

    @PostMapping("/bulk-import")
    public Map<String, Object> bulkImportMembers(@Valid @RequestBody MemberBulkImportRequest request) {
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
            if (isReservedActivityMemberName(characterName)) {
                continue;
            }

            Member member = memberRepository.findByCharacterName(characterName).orElse(null);
            boolean isNew = member == null;
            if (isNew) {
                member = Member.builder()
                        .characterName(characterName)
                        .password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
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
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        Member member = findMember(memberId);
        if (request.getCurrentPassword() == null || !PasswordSupport.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().length() < 4) {
            throw new IllegalArgumentException("새 비밀번호는 4자리 이상으로 입력해 주세요.");
        }

        member.setPassword(PasswordSupport.encode(request.getNewPassword()));
        memberRepository.save(member);
        return Map.of("message", "비밀번호 변경 완료", "memberId", member.getMemberId());
    }

    @PatchMapping("/{memberId}/password/reset")
    public Map<String, Object> resetPassword(
            @PathVariable Long memberId,
            @RequestParam Long adminMemberId,
            @Valid @RequestBody(required = false) PasswordResetRequest request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 비밀번호를 초기화할 수 있습니다.");
        }

        String nextPassword = request == null ? null : request.getNewPassword();
        if (nextPassword == null || nextPassword.trim().isBlank()) {
            nextPassword = PasswordSupport.DEFAULT_INITIAL_PASSWORD;
        }
        if (nextPassword.trim().length() < 4) {
            throw new IllegalArgumentException("새 비밀번호는 4자리 이상으로 입력해 주세요.");
        }

        Member member = findMember(memberId);
        member.setPassword(PasswordSupport.encode(nextPassword.trim()));
        memberRepository.save(member);

        return Map.of(
                "message", "비밀번호 초기화 완료",
                "memberId", member.getMemberId(),
                "characterName", member.getCharacterName()
        );
    }

    @PatchMapping("/passwords/reset")
    public Map<String, Object> resetAllPasswords(
            @RequestParam Long adminMemberId,
            @Valid @RequestBody(required = false) PasswordResetRequest request
    ) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("Only admins can reset all member passwords.");
        }

        String nextPassword = request == null ? null : request.getNewPassword();
        if (nextPassword == null || nextPassword.trim().isBlank()) {
            nextPassword = PasswordSupport.DEFAULT_INITIAL_PASSWORD;
        }
        if (nextPassword.trim().length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }

        String encodedPassword = PasswordSupport.encode(nextPassword.trim());
        List<Member> members = memberRepository.findAll();
        members.forEach(member -> member.setPassword(encodedPassword));
        memberRepository.saveAll(members);

        return Map.of(
                "message", "All member passwords reset.",
                "resetCount", members.size()
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

    @DeleteMapping("/reset")
    public Map<String, Object> resetMemberRoster(@RequestParam Long adminMemberId) {
        Member admin = findMember(adminMemberId);
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜원 명단을 초기화할 수 있습니다.");
        }

        List<Member> targets = memberRepository.findByActiveTrueOrderByMemberIdAsc()
                .stream()
                .filter(member -> !Objects.equals(member.getMemberId(), adminMemberId))
                .filter(member -> member.getRole() != MemberRole.ADMIN)
                .toList();

        targets.forEach(member -> {
            member.setActive(false);
            member.setStatus("삭제됨");
        });
        memberRepository.saveAll(targets);

        return Map.of(
                "message", "클랜원 명단 초기화 완료",
                "deactivated", targets.size()
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

    private void validateMemberNameAllowed(String characterName) {
        if (isReservedActivityMemberName(characterName)) {
            throw new IllegalArgumentException("\uC18C\uC218\uC7C1\uC740 \uD65C\uB3D9\uBA85\uC774\uB77C \uD074\uB79C\uC6D0\uC73C\uB85C \uB4F1\uB85D\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");
        }
    }

    private boolean isReservedActivityMemberName(String characterName) {
        return RESERVED_ACTIVITY_MEMBER_NAME.equals(characterName);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void saveSpecHistoryIfChanged(
            Member saved,
            Member admin,
            String previousName,
            Integer previousCombatPower,
            Integer previousLevel,
            String previousGuildName,
            String previousCharacterClass,
            String previousRank,
            String previousStatus
    ) {
        boolean changed = !Objects.equals(previousName, saved.getCharacterName())
                || !Objects.equals(previousCombatPower, saved.getCombatPower())
                || !Objects.equals(previousLevel, saved.getLevel())
                || !Objects.equals(previousGuildName, saved.getGuildName())
                || !Objects.equals(previousCharacterClass, saved.getCharacterClass())
                || !Objects.equals(previousRank, saved.getRank())
                || !Objects.equals(previousStatus, saved.getStatus());
        if (!changed) {
            return;
        }

        memberSpecHistoryRepository.save(MemberSpecHistory.builder()
                .memberId(saved.getMemberId())
                .characterName(saved.getCharacterName())
                .previousCombatPower(previousCombatPower)
                .nextCombatPower(saved.getCombatPower())
                .previousLevel(previousLevel)
                .nextLevel(saved.getLevel())
                .previousGuildName(previousGuildName)
                .nextGuildName(saved.getGuildName())
                .previousCharacterClass(previousCharacterClass)
                .nextCharacterClass(saved.getCharacterClass())
                .previousRank(previousRank)
                .nextRank(saved.getRank())
                .previousStatus(previousStatus)
                .nextStatus(saved.getStatus())
                .editedByMemberId(admin.getMemberId())
                .editedByName(admin.getCharacterName())
                .build());
    }

    @Getter
    @Setter
    public static class MemberProfileRequest {
        @NotBlank(message = "캐릭터 이름을 입력해 주세요.")
        @Size(max = 50, message = "캐릭터 이름은 50자 이하여야 합니다.")
        private String characterName;
        private Integer combatPower;
        @Size(max = 30, message = "클랜명은 30자 이하여야 합니다.")
        private String guildName;
        @Size(max = 50, message = "클래스는 50자 이하여야 합니다.")
        private String characterClass;
        private Integer level;
        @Size(max = 30, message = "직급은 30자 이하여야 합니다.")
        private String rank;
        @Size(max = 30, message = "상태는 30자 이하여야 합니다.")
        private String status;
        private Boolean active;
    }

    @Getter
    @Setter
    public static class SelfProfileRequest {
        @NotBlank(message = "캐릭터 이름을 입력해 주세요.")
        @Size(max = 50, message = "캐릭터 이름은 50자 이하여야 합니다.")
        private String characterName;
        @Size(max = 30)
        private String guildName;
        @Size(max = 50)
        private String characterClass;
    }

    @Getter
    @Setter
    public static class CreateMemberRequest {
        @NotBlank(message = "캐릭터 이름을 입력해 주세요.")
        @Size(max = 50, message = "캐릭터 이름은 50자 이하여야 합니다.")
        private String characterName;
        @Size(max = 100, message = "초기 비밀번호는 100자 이하여야 합니다.")
        private String initialPassword;
        private Integer combatPower;
        @Size(max = 30, message = "클랜명은 30자 이하여야 합니다.")
        private String guildName;
        @Size(max = 50, message = "클래스는 50자 이하여야 합니다.")
        private String characterClass;
        private Integer level;
        @Size(max = 30, message = "직급은 30자 이하여야 합니다.")
        private String rank;
        @Size(max = 30, message = "상태는 30자 이하여야 합니다.")
        private String status;
        private Boolean active;
    }

    @Getter
    @Setter
    public static class MemberBulkImportRequest {
        @NotNull(message = "운영자 정보를 확인할 수 없습니다.")
        private Long adminMemberId;
        private List<@Valid BulkMemberRequest> members;
    }

    @Getter
    @Setter
    public static class BulkMemberRequest {
        @Size(max = 50, message = "캐릭터 이름은 50자 이하여야 합니다.")
        private String characterName;
        @Size(max = 100, message = "초기 비밀번호는 100자 이하여야 합니다.")
        private String initialPassword;
        private Integer combatPower;
        @Size(max = 30, message = "클랜명은 30자 이하여야 합니다.")
        private String guildName;
        @Size(max = 50, message = "클래스는 50자 이하여야 합니다.")
        private String characterClass;
        private Integer level;
        @Size(max = 30, message = "직급은 30자 이하여야 합니다.")
        private String rank;
        @Size(max = 30, message = "상태는 30자 이하여야 합니다.")
        private String status;
        private Boolean active;
        private Boolean admin;
    }

    @Getter
    @Setter
    public static class PasswordChangeRequest {
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        private String currentPassword;
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(max = 100, message = "새 비밀번호는 100자 이하여야 합니다.")
        private String newPassword;
    }

    @Getter
    @Setter
    public static class PasswordResetRequest {
        @Size(min = 4, max = 100, message = "새 비밀번호는 4자 이상 100자 이하여야 합니다.")
        private String newPassword;
    }

    public record MemberSpecHistoryDto(
            Long historyId,
            Long memberId,
            String characterName,
            Integer previousCombatPower,
            Integer nextCombatPower,
            Integer previousLevel,
            Integer nextLevel,
            String previousGuildName,
            String nextGuildName,
            String previousCharacterClass,
            String nextCharacterClass,
            String previousRank,
            String nextRank,
            String previousStatus,
            String nextStatus,
            Long editedByMemberId,
            String editedByName,
            java.time.LocalDateTime createdAt
    ) {
        public static MemberSpecHistoryDto from(MemberSpecHistory history) {
            return new MemberSpecHistoryDto(
                    history.getMemberSpecHistoryId(),
                    history.getMemberId(),
                    history.getCharacterName(),
                    history.getPreviousCombatPower(),
                    history.getNextCombatPower(),
                    history.getPreviousLevel(),
                    history.getNextLevel(),
                    history.getPreviousGuildName(),
                    history.getNextGuildName(),
                    history.getPreviousCharacterClass(),
                    history.getNextCharacterClass(),
                    history.getPreviousRank(),
                    history.getNextRank(),
                    history.getPreviousStatus(),
                    history.getNextStatus(),
                    history.getEditedByMemberId(),
                    history.getEditedByName(),
                    history.getCreatedAt()
            );
        }
    }
}
