package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.LoginRequestDto;
import com.clanmanager.clanmanager.dto.RegisterRequestDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.security.PasswordSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REGISTRATION_PENDING_STATUS = "가입승인대기";

    private final MemberRepository memberRepository;

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequestDto request) {
        if (memberRepository.existsByCharacterName(request.getCharacterName())) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        boolean bootstrapAdmin = memberRepository.count() == 0;
        Member savedMember = memberRepository.save(Member.builder()
                .characterName(request.getCharacterName())
                .password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
                .mustChangePassword(true)
                .combatPower(request.getCombatPower())
                .status(bootstrapAdmin ? "활동중" : REGISTRATION_PENDING_STATUS)
                .role(bootstrapAdmin ? MemberRole.ADMIN : MemberRole.MEMBER)
                .active(bootstrapAdmin)
                .build());

        return Map.of(
                "message", bootstrapAdmin ? "관리자 계정 생성 완료" : "회원가입 신청이 접수되었습니다. 운영자 승인 후 로그인할 수 있습니다.",
                "memberId", savedMember.getMemberId(),
                "characterName", savedMember.getCharacterName(),
                "role", savedMember.getRole().name(),
                "mustChangePassword", Boolean.TRUE.equals(savedMember.getMustChangePassword()),
                "approvalPending", !bootstrapAdmin
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequestDto request) {
        Member member = memberRepository.findByCharacterName(request.getCharacterName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 캐릭터입니다."));

        if (!PasswordSupport.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (Boolean.FALSE.equals(member.getActive())) {
            if (REGISTRATION_PENDING_STATUS.equals(member.getStatus())) {
                throw new IllegalArgumentException("회원가입 승인 대기 중입니다. 운영자 승인 후 로그인해 주세요.");
            }
            throw new IllegalArgumentException("비활성화된 계정입니다. 운영진에게 문의해 주세요.");
        }

        boolean usesInitialPassword = PasswordSupport.matches(
                PasswordSupport.DEFAULT_INITIAL_PASSWORD,
                member.getPassword()
        );
        boolean memberChanged = false;
        if (!PasswordSupport.isEncoded(member.getPassword())) {
            member.setPassword(PasswordSupport.encode(request.getPassword()));
            memberChanged = true;
        }
        if (usesInitialPassword && !Boolean.TRUE.equals(member.getMustChangePassword())) {
            member.setMustChangePassword(true);
            memberChanged = true;
        }
        if (memberChanged) {
            memberRepository.save(member);
        }

        return Map.of(
                "message", "로그인 성공",
                "memberId", member.getMemberId(),
                "characterName", member.getCharacterName(),
                "role", member.getRole().name(),
                "mustChangePassword", Boolean.TRUE.equals(member.getMustChangePassword())
        );
    }
}
