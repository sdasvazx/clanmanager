package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.LoginRequestDto;
import com.clanmanager.clanmanager.dto.RegisterRequestDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberRepository memberRepository;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequestDto request) {

        if (memberRepository.existsByCharacterName(request.getCharacterName())) {
            throw new RuntimeException("이미 등록된 캐릭터 이름입니다.");
        }

        Member member = Member.builder()
                .characterName(request.getCharacterName())
                .password(request.getPassword())
                .combatPower(request.getCombatPower())
                .role(MemberRole.MEMBER)
                .active(true)
                .build();

        Member savedMember = memberRepository.save(member);

        return Map.of(
                "message", "회원가입 성공",
                "memberId", savedMember.getMemberId(),
                "characterName", savedMember.getCharacterName()
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequestDto request) {

        Member member = memberRepository.findByCharacterName(request.getCharacterName())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 캐릭터입니다."));

        if (!member.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        return Map.of(
                "message", "로그인 성공",
                "memberId", member.getMemberId(),
                "characterName", member.getCharacterName(),
                "role", member.getRole().name()
        );
    }
}