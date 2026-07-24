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

    private final MemberRepository memberRepository;

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequestDto request) {
        if (memberRepository.existsByCharacterName(request.getCharacterName())) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        Member savedMember = memberRepository.save(Member.builder()
                .characterName(request.getCharacterName())
                .password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
                .mustChangePassword(true)
                .combatPower(request.getCombatPower())
                .role(memberRepository.count() == 0 ? MemberRole.ADMIN : MemberRole.MEMBER)
                .active(true)
                .build());

        return Map.of(
                "message", "회원가입 성공",
                "memberId", savedMember.getMemberId(),
                "characterName", savedMember.getCharacterName(),
                "role", savedMember.getRole().name(),
                "mustChangePassword", Boolean.TRUE.equals(savedMember.getMustChangePassword())
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
